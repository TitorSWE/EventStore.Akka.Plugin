package ch.elca.advisory
package plugin.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import akka.persistence.query.javadsl.ReadJournal
import akka.persistence.query.scaladsl.ReadJournal
import ch.elca.advisory.plugin.query.scaladsl.EventStoreReadJournal
import com.typesafe.config.Config

class EventStoreReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  override def scaladslReadJournal(): scaladsl.EventStoreReadJournal  = {
    new scaladsl.EventStoreReadJournal(system, config)
  }

  override def javadslReadJournal(): javadsl.EventStoreReadJournal = {
    javadsl.EventStoreReadJournal(scaladslReadJournal())
  }
}
