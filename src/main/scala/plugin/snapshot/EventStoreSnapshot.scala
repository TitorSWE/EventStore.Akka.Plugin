package ch.elca.advisory
package plugin.snapshot


import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.persistence.snapshot.SnapshotStore
import ch.elca.advisory.plugin.Helper.getClassTag
import ch.elca.advisory.plugin.{EventStorePlugin, EventStoreSerialization}

import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

/*
* In this implementation, snapshots of a certain persistenceId are stored inside its stream metadata
* Custom properties is then used as a dictionary of snapshots
* The key corresponds to the snapshot metadata (sequenceNr and timestamp)
* The value is the snapshot payload
*
* Maybe this implementation will change, snapshots could eventually be stored inside a stream of event
* which will allow the user to choose a different serialization technic than json serialization(Jackson json here)
*
* However this method allow an easy way to get snapshots according to timestamp criteria
*  */



class EventStoreSnapshot extends SnapshotStore with EventStorePlugin {


  /*
  * Snapshots are stored in a dictionary where the key is the snapshot metadata
  * Deserialize the keys of this dictionary, ease the filtering over sequenceNr or timestamp
  * */
  private def getDeserializedSnapshotsMetadata(snapshots: Map[String, AnyRef]): Map[SnapshotMetadata, String] = {
    snapshots.map {case (key, value) =>
      (serialization.deserializeSnapshotMetadata(key), value.toString)
    }
  }

  private def getSerializedSnapshotsMetadata(snapshots: Map[SnapshotMetadata, String]): Map[String, String] = {
    snapshots.map { case (key, value) =>
      (serialization.serializeSnapshotMetadata(key), value)
    }
  }

  /*
  * Returns the snapshots filtered according to the most restrictive criteria between sequenceNr and timestamp
  * Meaning snapshots are filtered according to sequenceNr criteria and timestamp criteria separately
  * Then computes the intersection of both filtered snapshots
  */
  private def filterSnapshots(snapshots: Map[SnapshotMetadata, String], criteria: SnapshotSelectionCriteria): Map[SnapshotMetadata, String] = {

    val snapshotsFilteredBySeq = snapshots.filter { case (metadata, snapshot) =>
      (criteria.minSequenceNr <= metadata.sequenceNr && metadata.sequenceNr <= criteria.maxSequenceNr) }

    val snapshotsFilteredByTime = snapshots.filter { case (metadata, snapshot) =>
      (criteria.minTimestamp <= metadata.timestamp && metadata.timestamp <= criteria.maxTimestamp)}

    val intersection = snapshotsFilteredBySeq.filter { case (k, v) => snapshotsFilteredByTime.get(k).contains(v) }

    intersection
  }


  /*
  * Implementation of the SnapshotStore API
  * Since snapshots are not stored in event stream, the idea is always the same for Write or Update
  * 1. Retry the snapshots from the customProperties of the metadata stream
  * 2. Modify these custom properties (add or delete a snapshot)
  * 3. Update the stream metadata containing new properties (snapshots) in EventStore
  */


  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {

    /*
    * Select most recent snapshot json
    * Then deserializes it
    * */
    def selectMostRecentSnapshot(selectedSnapshots: Map[SnapshotMetadata, AnyRef]): Option[SelectedSnapshot] = {
      // Find the most recent snapshot metadata
      val mostRecentMetadata: Option[SnapshotMetadata] = selectedSnapshots.keys.maxByOption(_.timestamp)

      mostRecentMetadata.flatMap { metadata =>
        // Retrieve the snapshot data
        val snapshotData = selectedSnapshots(metadata)

        // Get the runtime class tag
        val runtimeClass = Class.forName(metadata.metadata.get.toString)
        val classTag = ClassTag[AnyRef](runtimeClass)
        // Wrap the deserialization in Try
        val deserializedSnapshotTry: Try[AnyRef] = Try (serialization.deserializeSnapshot(snapshotData.toString)(classTag))

        // Convert Try to Option based on success or failure
        deserializedSnapshotTry.toOption.map(deserializedSnapshot => SelectedSnapshot(metadata, deserializedSnapshot))
      }
    }

    client.getStreamMetadata(persistenceId).asScala.map { streamMetadata =>
      val snapshots = streamMetadata.getCustomProperties.asScala.toMap
      val selectedSnapshots = filterSnapshots(getDeserializedSnapshotsMetadata(snapshots), criteria)
      val mostRecent = selectMostRecentSnapshot(selectedSnapshots)
      mostRecent
    }.recover { case _ => None }

  }


  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {

    // Manifest is in metadata of SnapshotMetadata, needed for deserializing
    val serializedSnapshotMetadata = serialization.serializeSnapshotMetadata(metadata.withMetadata(snapshot.getClass.getName))
    val serializedSnapshot = snapshot match {
      case snap: AnyRef => serialization.serializeSnapshot(snap)
      case snap: AnyVal => snap.toString
    }
    client.getStreamMetadata(metadata.persistenceId).asScala.flatMap { streamMetadata =>
      val snapshots = Option(streamMetadata.getCustomProperties.asScala).getOrElse(Map.empty[String, AnyRef])
      val updatedSnapshots = snapshots + (serializedSnapshotMetadata -> serializedSnapshot)
      streamMetadata.setCustomProperties(updatedSnapshots.asJava)
      client.setStreamMetadata(metadata.persistenceId, streamMetadata).asScala.map( _ => ())}

  }

  // TO DO :
  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {

    // Define a function to compare SnapshotMetadata objects for "almost equality"
    def isAlmostEqual(metadata1: SnapshotMetadata, metadata2: SnapshotMetadata): Boolean = {
      metadata1.persistenceId == metadata2.persistenceId &&
        metadata1.sequenceNr == metadata2.sequenceNr &&
        metadata1.timestamp == metadata2.timestamp
    }

    val persistenceId = metadata.persistenceId
    client.getStreamMetadata(metadata.persistenceId).asScala.flatMap { streamMetadata =>
      val snapshots = Option(streamMetadata.getCustomProperties.asScala).getOrElse(Map.empty[String, AnyRef])
      val deserializeSnapshots: Map[SnapshotMetadata, String] = getDeserializedSnapshotsMetadata(snapshots.toMap)
      // Deleting the snapshots given a certain metadata
      val filteredSnapshots: Map[SnapshotMetadata, String] = deserializeSnapshots.filterNot {
        case (key, value) => isAlmostEqual(key, metadata)
      }
      val updatedSnapshots = getSerializedSnapshotsMetadata(filteredSnapshots)
      streamMetadata.setCustomProperties(updatedSnapshots.asJava)
      client.setStreamMetadata(metadata.persistenceId, streamMetadata).asScala.map( _ => ())}
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    client.getStreamMetadata(persistenceId).asScala.flatMap { streamMetadata =>
      val snapshots = Option(streamMetadata.getCustomProperties.asScala).getOrElse(Map.empty[String, AnyRef]).toMap
      val deserializedSnapshots = getDeserializedSnapshotsMetadata(snapshots)
      val snapshotsToDelete = filterSnapshots(deserializedSnapshots, criteria)
      val updatedSnapshots = (deserializedSnapshots -- snapshotsToDelete.keys).map { case (key, value) => (serialization.serializeSnapshotMetadata(key), value)} // Deleting snapshot
      streamMetadata.setCustomProperties(updatedSnapshots.asJava)
      client.setStreamMetadata(persistenceId, streamMetadata).asScala.map( _ => ())
    }
  }

  override implicit val ec: ExecutionContext = context.dispatcher
  override val serialization: EventStoreSerialization = EventStoreSerialization(context.system)
}
