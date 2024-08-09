package ch.elca.advisory
package plugin.query.scaladsl

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.query.{EventEnvelope, NoOffset, Offset, Sequence}
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source
import ch.elca.advisory.plugin.Helper.uuidPattern
import ch.elca.advisory.plugin.{EventStorePlugin, EventStoreSerialization}
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.Config
import com.eventstore.dbclient.{ReadStreamOptions, ResolvedEvent}
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

class EventStoreReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal
  with akka.persistence.query.scaladsl.EventsByTagQuery
  with akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
  with akka.persistence.query.scaladsl.PersistenceIdsQuery
  with akka.persistence.query.scaladsl.CurrentPersistenceIdsQuery
  with EventStorePlugin
{

  private def getEventStoreConnectionString: String = {
    config.getString("eventstore.connection-string")
  }
  val serialization: EventStoreSerialization = EventStoreSerialization(system = system)



  val mapper: ObjectMapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)


  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] = {

    // The offset is defined as a Sequence or NoOffset
    val offsetValue: Long = offset match {
      case Sequence(value) => value
      case NoOffset        => 0L
      case _               => throw new Exception("The offset should be either Sequence(value) or NoOffset")
    }

    // Setting options
    val fromPositionValue = offsetValue - 1
    val options: ReadStreamOptions = ReadStreamOptions.get().forwards().fromRevision(fromPositionValue)
    val reactiveStream = client.readStreamReactive(s"tag-$tag", options)

    // Returning the source
    val source = Source.fromPublisher(reactiveStream)
    source.map { readMessage =>
      val resolvedEvent = readMessage.getEvent
      getEventEnvelope(resolvedEvent)
    }
  }

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] = {

    // Setting reading options
    val fromPositionValue = fromSequenceNr - 1
    val maxCountValue = toSequenceNr - fromSequenceNr + 1
    val options: ReadStreamOptions = ReadStreamOptions.get().forwards().fromRevision(fromPositionValue).maxCount(maxCountValue)

    // Returning the source
    val reactiveStream = client.readStreamReactive(persistenceId, options)
    val source = Source.fromPublisher(reactiveStream)
    source.map {readMessage =>
      val resolvedEvent = readMessage.getEvent
      getEventEnvelope(resolvedEvent)
    }
  }



  override def persistenceIds(): Source[String, NotUsed] = {

    // Options for reading the $streams system stream
    val options = ReadStreamOptions.get().forwards().fromStart()

    val reactiveStream = client.readStreamReactive("$streams", options)
    Source.fromPublisher(reactiveStream)
      .map(readMessage => new String(readMessage.getEvent.getOriginalEvent.getEventData))
      .filter(streamId => uuidPattern.matcher(streamId).matches())
  }

  override def currentPersistenceIds(): Source[String, NotUsed] = {

    // Options for reading the $streams system stream
    val options = ReadStreamOptions.get().forwards().fromStart()

    // Read the $streams system stream
    val readResult = client.readStream("$streams", options).get()

    // Extract and filter stream IDs that match the UUID pattern
    val uuidStreams = readResult.getEvents.asScala
      .map(event => new String(event.getOriginalEvent.getEventData))
      .filter(streamId => uuidPattern.matcher(streamId).matches())
      .toList

    // Returning the stream
    Source(uuidStreams)
  }

  /*
  * Use jackson for deserializing the projected event into an EventEnvelope
  * */
  private def getEventEnvelope(resolvedEvent: ResolvedEvent): EventEnvelope = {
    val event = resolvedEvent.getEvent.getEventData
    val jsonEvent = mapper.readTree(event)

    // TO DO : Throw an Exception when cannot get these values
    // Extract the fields from the JSON
    val eventNode = jsonEvent.get("payload")
    val sequenceNr = jsonEvent.get("sequenceNr").asLong()
    val persistenceId = jsonEvent.get("persistenceId").asText()
    val eventType = jsonEvent.get("eventClassName").asText()

    // Deserialize the payload to the runtime class
    val runTimeEventClass = Class.forName(eventType)
    val payload = mapper.treeToValue(eventNode, runTimeEventClass)

    // Create an EventEnvelope
    val offset = Offset.sequence(resolvedEvent.getEvent.getRevision)

    // TO DO : Find a way to put timestamp in metadata
    EventEnvelope(offset, persistenceId, sequenceNr, payload, 0L)
  }

  override implicit val ec: ExecutionContext = system.getDispatcher
}


