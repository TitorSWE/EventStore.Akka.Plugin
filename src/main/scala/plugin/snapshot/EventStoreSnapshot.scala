package ch.elca.advisory
package plugin.snapshot

import akka.actor.ActorSystem
import akka.actor.Status.Success
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.persistence.snapshot.SnapshotStore
import ch.elca.advisory.plugin.Helper.getClassTag
import ch.elca.advisory.plugin.{EventStorePlugin, EventStoreSerialization}
import com.eventstore.dbclient.{EventData, StreamMetadata}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

/*
* The snapshots will be stored in the metadata of the stream
* */

class EventStoreSnapshot extends SnapshotStore with EventStorePlugin{


  private def getSnapshots(customProperties: Map[String, AnyRef]): Map[SnapshotMetadata, String] = {
    val snapshots: Map[String, String] = customProperties.get("snapshots") match {
      case Some(value) => serialization.snapshotstoMap(value)
      case None => Map.empty[String, String]
    }
    snapshots.map {case (key, value) =>
      (serialization.deserializeSnapshotMetadata(key), value)
    }
  }


  private def filterSnapshots(properties: Map[SnapshotMetadata, String], criteria: SnapshotSelectionCriteria): Map[SnapshotMetadata, String] = {
    properties.filter { case (metadata, snapshot) =>
      (criteria.minSequenceNr <= metadata.sequenceNr && metadata.sequenceNr <= criteria.maxSequenceNr) ||
        (criteria.minTimestamp <= metadata.timestamp && metadata.timestamp <= criteria.maxTimestamp)
    }
  }


  // criteria according timestamp is not implemented for the moment because snapshot are in metadata
  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {

    def selectMostRecentSnapshot(selectedSnapshots: Map[SnapshotMetadata, String])(implicit tag: ClassTag[_]): Option[SelectedSnapshot] = {
      selectedSnapshots.keys.maxByOption(_.sequenceNr).map { key =>
        SelectedSnapshot(key, serialization.deserializeSnapshot(selectedSnapshots(key)))
      }
    }

    client.getStreamMetadata(persistenceId).asScala.map { streamMetadata =>
      val properties = streamMetadata.getCustomProperties.asScala.toMap
      val snapshots = getSnapshots(properties)
      val tag = getClassTag(properties("type").toString).get
      val selectedSnapshots = filterSnapshots(snapshots, criteria)
      selectMostRecentSnapshot(selectedSnapshots)(tag)
    }.recover { case _ => None }

  }


  /*
  *
  * TO DO : Custom Properties is json containing type and snapshots properties which is a map[String, String]
  * */

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {

    val serializedSnapshotMetadata = serialization.serializeSnapshotMetadata(metadata)
    val serializedSnapshot = snapshot match {
      case snap: AnyRef => serialization.serializeSnapshot(snap)
      case snap: AnyVal => snap.toString
    }
    client.getStreamMetadata(metadata.persistenceId).asScala.flatMap { streamMetadata =>
      val customProperties = Option(streamMetadata.getCustomProperties.asScala).getOrElse(Map.empty[String, AnyRef])
      val snapshots: Map[String, String] = customProperties.get("snapshots") match {
        case Some(value) => serialization.snapshotstoMap(value)
        case None => Map.empty[String, String]
      }
      val updatedSnapshots = snapshots + (serializedSnapshotMetadata -> serializedSnapshot)
      val classStateName = customProperties.getOrElse("type", snapshot.getClass.getName)
      val updatedProperties = updatedSnapshots + ("type" -> classStateName)
      streamMetadata.setCustomProperties(updatedProperties.asJava)
      client.setStreamMetadata(metadata.persistenceId, streamMetadata).asScala.map( _ => ())}

  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    val persistenceId = metadata.persistenceId
    client.getStreamMetadata(metadata.persistenceId).asScala.flatMap { streamMetadata =>
      val customProperties = Option(streamMetadata.getCustomProperties.asScala).getOrElse(Map.empty[String, AnyRef])
      val snapshots: Map[String, String] = customProperties.get("snapshots") match {
        case Some(value) => serialization.snapshotstoMap(value)
        case None => Map.empty[String, String]
      }
      val updatedSnapshots = snapshots - serialization.serializeSnapshotMetadata(metadata) // Deleting snapshot
      val updatedProperties = updatedSnapshots + ("type" -> customProperties("type"))
      streamMetadata.setCustomProperties(updatedProperties.asJava)
      client.setStreamMetadata(metadata.persistenceId, streamMetadata).asScala.map( _ => ())}
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    client.getStreamMetadata(persistenceId).asScala.flatMap { streamMetadata =>
      val customProperties = streamMetadata.getCustomProperties.asScala.toMap
      val snapshots = getSnapshots(customProperties)
      val snapshotsToDelete = filterSnapshots(snapshots, criteria)
      val updatedSnapshots = (snapshots -- snapshotsToDelete.keys).map { case (key, value) => (serialization.serializeSnapshotMetadata(key), value)} // Deleting snapshot
      val updatedProperties = updatedSnapshots + ("type" -> customProperties("type"))
      streamMetadata.setCustomProperties(updatedProperties.asJava)
      client.setStreamMetadata(persistenceId, streamMetadata).asScala.map( _ => ())
    }
  }

  override implicit val ec: ExecutionContext = context.dispatcher
  override val serialization: EventStoreSerialization = EventStoreSerialization(context.system)
}
