package ch.elca.advisory
package plugin.snapshot

import akka.persistence.typed.SnapshotAdapter


trait EventStoreSnapshotGenericAdapter[S, W[_]] extends SnapshotAdapter[S] {

  // Abstract methods or members that use both S (State) and W (Wrapper)
  def adaptSnapshot(state: S): W[S]
  def reverseAdapt(snapshot: W[S]): S


  override def toJournal(state: S): Any = adaptSnapshot(state)

  override def fromJournal(from: Any): S = from match {
    case w: W[S] => reverseAdapt(w)
    case _ => throw new Exception("Cannot read snapshot from Snapshot Store")
  }
}
