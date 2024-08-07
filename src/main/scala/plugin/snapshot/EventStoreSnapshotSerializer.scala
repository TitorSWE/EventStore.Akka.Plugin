package ch.elca.advisory
package plugin.snapshot

import akka.serialization.Serializer

trait EventStoreSnapshotSerializer extends Serializer {

  def toSnapshot(o: AnyRef) : String
  def fromSnapshot(snapshot: String, manifest: Class[_]): AnyRef
}
