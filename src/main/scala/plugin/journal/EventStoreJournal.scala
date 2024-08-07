package ch.elca.advisory
package plugin.journal

import akka.actor.{ActorSystem, Props}
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.persistence.journal.AsyncWriteJournal
import ch.elca.advisory.plugin.Helper.sequence
import ch.elca.advisory.plugin.{EventStorePlugin, EventStoreSerialization}
import com.eventstore.dbclient.{CreateProjectionOptions, EventData, EventStoreDBProjectionManagementClient, ReadStreamOptions, RecordedEvent, StreamMetadata, WriteResult}

import java.io.{ByteArrayOutputStream, ObjectOutputStream}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import java.util.UUID
import scala.reflect.ClassTag



class EventStoreJournal extends AsyncWriteJournal with EventStoreJournalPlugin {

  
  /*
  * 
  * 
  * */

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {

    /*
     * Before trying to persist
     * 1. Check if the payload is correctly wrapped : necessary for eventsByTags
     * 2. Check if the persistenceId is an UUID : necessary for persistenceIds and currentPersistenceIds
     * */
    // Helper function to validate and serialize a single AtomicWrite

    def checkPayload(payload: Any): Boolean = {
      // Implement the logic to check if the payload is correctly wrapped
      // This is a placeholder for the actual check you need
      payload match {
        case _: EventStoreWrapper[Any] => true // Example: replace with actual check for WrappedEvent
        case _ => false
      }
    }

    def isValidUUID(uuid: String): Boolean = {
      Try(UUID.fromString(uuid)).isSuccess
    }

    def serializeAtomicWrite(atomicWrite: AtomicWrite): Try[(String, Seq[EventData])] = {
      for {
        persistenceId <- Try {
          if (isValidUUID(atomicWrite.persistenceId)) atomicWrite.persistenceId
          else {
            context.system.log.error("PersistenceId must be a UUID")
            throw new RuntimeException("PersistenceId must be a UUID")
          }
        }
        serializedBatch <- sequence(atomicWrite.payload.map { persistentRepr =>
          Try(persistentRepr.payload match {
            case event: EventStoreWrapper[_] => serialization.serialize(event, None)
            case _ => {
              context.system.log.error("Event must be wrapped into EventStoreWrapper, must use EventStoreAdapter")
              throw new Exception("Event must be wrapped into EventStoreWrapper")
            }
          })
        })
      } yield (persistenceId, serializedBatch)
    }

    // Serialize and validate all AtomicWrites
    val serializedEventBatches: Seq[Try[(String, Seq[EventData])]] = messages.map(serializeAtomicWrite)

    // Write to EventStore
    val seqOfFutureResult: Seq[Future[Try[Unit]]] = serializedEventBatches.map {
      case Failure(exception) => Future.successful(Failure(exception))
      case Success((persistenceId, data)) =>
        client.appendToStream(persistenceId, data.toList.asJava.iterator())
          .asScala.map( _ => Success(())).recover[Try[Unit]](Failure(_))
    }

    // Returning the Future of Sequence
    Future.sequence(seqOfFutureResult)
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    // Create the stream metadata with the truncate before value
    val streamMetadata: StreamMetadata = StreamMetadata()
    streamMetadata.setTruncateBefore(toSequenceNr-1)

    // Set the stream metadata
    client.setStreamMetadata(persistenceId, streamMetadata).asScala.map(_ => ())
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: PersistentRepr => Unit): Future[Unit] = {

    val options: ReadStreamOptions = ReadStreamOptions.get().forwards().fromRevision(fromSequenceNr-1)
    val futureEventList = client.readStream(persistenceId, options).asScala
      .map(result => result.getEvents.asScala.toSeq.map(resolvedEvent => {
        val recordedEvent: RecordedEvent = resolvedEvent.getEvent

        // Deserialize based on the type of the event
        val eventType = recordedEvent.getEventType
        val runTimeEventClass = Class.forName(eventType)
        val runTimeEventClassTag = ClassTag[AnyRef](runTimeEventClass)
        val payload = serialization.deserialize(recordedEvent)(runTimeEventClassTag)

        // Constructing persistentRepr
        PersistentRepr(
          payload = payload,
          sequenceNr = recordedEvent.getPosition.getCommitUnsigned + 1,
          persistenceId = persistenceId,
          manifest = "",
          deleted = false,
          sender = null,
          writerUuid = ""
        )
      }).foreach(recoveryCallback)) // apply recoveryCallBack
    futureEventList
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {

    val options: ReadStreamOptions = ReadStreamOptions.get().forwards().fromRevision(fromSequenceNr)
    client.readStream(persistenceId, null).asScala
      .map(result => result.getLastStreamPosition + 1)
      .recover {
        case e:Exception => 0L
      }
  }
  
  override def preStart(): Unit = initializeProjections()





}

