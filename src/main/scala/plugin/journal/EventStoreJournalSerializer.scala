package ch.elca.advisory
package plugin.journal

import akka.serialization.Serializer
import com.eventstore.dbclient.{EventData, RecordedEvent, ResolvedEvent}

trait EventStoreJournalSerializer extends Serializer{
  def toEvent(o: AnyRef): EventData
  def fromEvent(event: RecordedEvent, manifest: Class[_]): AnyRef
}
