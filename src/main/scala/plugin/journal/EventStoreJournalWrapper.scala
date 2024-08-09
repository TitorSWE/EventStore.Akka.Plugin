package ch.elca.advisory
package plugin.journal


/*
* E is generic type for Events
* */


abstract class EventStoreJournalWrapper[E] {

  val payload: E
  val tags: Set[String]
  val eventClassName: String
}
