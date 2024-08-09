package ch.elca.advisory
package plugin.snapshot

import akka.serialization.Serializer

/*
* TO DO : will be used when generalizing the json serialization
* */
trait EventStoreSnapshotSerializer extends Serializer {

  def toSnapshot(o: AnyRef) : String
  def fromSnapshot(snapshot: String, manifest: Class[_]): AnyRef
}
