package ch.elca.advisory
package plugin.snapshot

import akka.serialization.jackson.JsonSerializable

abstract class EventStoreSnapshotWrapper[S] extends JsonSerializable{

  val payload: S
  val version: Int
  val className: String

}
