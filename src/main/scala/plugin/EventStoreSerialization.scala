package ch.elca.advisory
package plugin

import akka.actor.ActorSystem
import akka.persistence.SnapshotMetadata
import akka.persistence.query.EventEnvelope
import akka.serialization.{Serialization, SerializationExtension, Serializer}
import ch.elca.advisory.plugin.journal.EventStoreJournalSerializer
import ch.elca.advisory.plugin.snapshot.EventStoreSnapshotSerializer
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
  * Serialization for snapshot
  * */
  case class WrappedSnapshot(typeName: String, payload: Any)

  private object WrappedSnapshot {
    def apply(payload: Any): WrappedSnapshot = {
      val clazz = payload.getClass.getName
      WrappedSnapshot(clazz, payload)
    }
  }

  val mapper: ObjectMapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  def snapshotstoMap(snapshots: AnyRef): Map[String, String] = {
    mapper.convertValue(snapshots, new TypeReference[Map[String, String]]() {})
  }


  def deserializeSnapshot[T](snap: String)(implicit tag: ClassTag[T]): T = {

    val ser = serialization.findSerializerFor(tag.runtimeClass)
    val res = ser match {
      case ser: EventStoreSnapshotSerializer => ser.fromSnapshot(snap, tag.runtimeClass)
      case _ => mapper.readValue(snap, tag.runtimeClass)
    }
    res.asInstanceOf[T]
  }

  def serializeSnapshot(data: AnyRef, eventType: => Option[Any] = None): String = {
    // convert snapshot into JSON-like string
    mapper.writeValueAsString(data)
  }

  def deserializeSnapshotMetadata(metadata: String): SnapshotMetadata = {
    mapper.readValue(metadata, classOf[SnapshotMetadata])
  }

  def serializeSnapshotMetadata(metaData: SnapshotMetadata): String = {
    mapper.writeValueAsString(metaData)
  }
}

object EventStoreSerialization {
  def apply(system: ActorSystem): EventStoreSerialization = {
    EventStoreSerialization(SerializationExtension(system))
  }
}
