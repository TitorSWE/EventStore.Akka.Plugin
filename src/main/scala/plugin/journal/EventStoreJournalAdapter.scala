package ch.elca.advisory
package plugin.journal

import akka.persistence.typed.EventAdapter

abstract class EventStoreJournalAdapter[E] extends EventAdapter[E,EventStoreJournalWrapper[E]] {

}
