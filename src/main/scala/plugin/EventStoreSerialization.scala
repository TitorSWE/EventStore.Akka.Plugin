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
 * The serialization is a json based, by default using jackson
 * If the user wants custom json serialization, must implement EventStoreSnapshotSerializer
 * */

  val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  def deserializeSnapshot[T](snapshot: String)(implicit tag: ClassTag[T]): T = {
    val ser: Serializer = serialization.serializerFor(tag.runtimeClass)
    val res = ser match {
      case ser: EventStoreSnapshotSerializer => ser.fromSnapshot(snapshot, tag.runtimeClass)
      case _ => mapper.readValue(snapshot, tag.runtimeClass)
    }
    res.asInstanceOf[T]

  }

  def serializeSnapshot(data: AnyRef, eventType: => Option[Any] = None): String = {
    val ser = serialization.findSerializerFor(data)
    ser match {
      case ser: EventStoreSnapshotSerializer => ser.toSnapshot(data)
      case _ => mapper.writeValueAsString(data)
    }
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
