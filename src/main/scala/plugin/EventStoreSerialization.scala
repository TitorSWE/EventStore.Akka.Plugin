package ch.elca.advisory
package plugin

import akka.actor.ActorSystem
import akka.persistence.SnapshotMetadata
import akka.persistence.query.EventEnvelope
import akka.serialization.jackson.JsonSerializable
import akka.serialization.{Serialization, SerializationExtension, Serializer}
import ch.elca.advisory.plugin.Helper.getClassTag
import ch.elca.advisory.plugin.journal.EventStoreJournalSerializer
import ch.elca.advisory.plugin.snapshot.{EventStoreSnapshotSerializer, EventStoreSnapshotWrapper}
import ch.qos.logback.core.spi.ConfigurationEvent.EventType
import com.eventstore.dbclient.{EventData, RecordedEvent, ResolvedEvent}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.annotation.tailrec
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

case class EventStoreSerialization(serialization: Serialization) {


  /*
  * Serialization for journal
  * */

  def deserialize[T](event: RecordedEvent)(implicit tag: ClassTag[T]): T = {
    val ser: Serializer = serialization.serializerFor(tag.runtimeClass)
    val res = ser match {
      case ser: EventStoreJournalSerializer => ser.fromEvent(event, tag.runtimeClass)
      case _ => ser.fromBinary(event.getEventData, tag.runtimeClass)
    }
    res.asInstanceOf[T]
  }

  def serialize(data: AnyRef, eventType: => Option[Any] = None): EventData = {
    val ser = serialization.findSerializerFor(data)
    ser match {
      case ser: EventStoreJournalSerializer => ser.toEvent(data)
      case _ => EventData.builderAsBinary((eventType getOrElse data).getClass.getName, ser.toBinary(data)).build()
    }
  }


 /*
 * Serialization for snapshots
 * The serialization is made with Jackson Json to satisfy the API of customProperties in eventstore stream metadata
 * TO DO : generalize the json serialization in order to use other json serializer
 * */

  /*
  * Small Wrapper in order to keep the runtime class of the snapshot inside the json for deserializing the snapshot payload
  * */
  private class snapWrapper(val payload: AnyRef, val className: String) extends JsonSerializable

  private object snapWrapper {
    def apply(payload:AnyRef): snapWrapper = new snapWrapper(payload, payload.getClass.getName)
  }
  
  
  /* Transform the snapshot json string into a json object which can be manipulate
  * Retry the runtime class 
  * Return the deserialized snapshot payload
  * */
  def deserializeSnapshot(snapshot: String): AnyRef = {
    val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val snapshotJson = mapper.readTree(snapshot)
    val payload = snapshotJson.get("payload")
    val className = snapshotJson.get("className").asText()
    val runTimeClass = Class.forName(className)
    mapper.treeToValue(payload, runTimeClass)
  }

  /*
  * Wrap the snapshot payload, then serializing into a json string
  * */
  def serializeSnapshot(data: AnyRef, eventType: => Option[Any] = None): String = {
    val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
    mapper.writeValueAsString(snapWrapper(data))
  }
  
  def deserializeSnapshotMetadata(metadata: String): SnapshotMetadata = {
    val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
    mapper.readValue(metadata, classOf[SnapshotMetadata])
  }

  def serializeSnapshotMetadata(metaData: SnapshotMetadata): String = {
    val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
    mapper.writeValueAsString(metaData)
  }
}

object EventStoreSerialization {
  def apply(system: ActorSystem): EventStoreSerialization = {
    EventStoreSerialization(SerializationExtension(system))
  }
}
