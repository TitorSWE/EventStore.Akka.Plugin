package ch.elca.advisory
package plugin.journal

import akka.persistence.typed.EventAdapter

abstract class EventStoreAdapter[E] extends EventAdapter[E,EventStoreWrapper[E]] {

}
