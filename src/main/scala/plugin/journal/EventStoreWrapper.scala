package ch.elca.advisory
package plugin.journal


/*
* E is generic type for Events
* */


abstract class EventStoreWrapper[E] {

  val payload: E
  val tags: Set[String]
  val eventClassName: String
}
