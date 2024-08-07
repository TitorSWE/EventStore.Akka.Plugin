package ch.elca.advisory
package plugin.query.javadsl


import akka.persistence.query.javadsl.*
import plugin.query.scaladsl

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.stream.javadsl.Source

class EventStoreReadJournal(readJournal: scaladsl.EventStoreReadJournal)
  extends ReadJournal
    with PersistenceIdsQuery
    with CurrentPersistenceIdsQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByPersistenceIdQuery {

  override def persistenceIds(): Source[String, NotUsed] = ???

  override def currentPersistenceIds(): Source[String, NotUsed] = ???

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] = ???

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] = ???
}
