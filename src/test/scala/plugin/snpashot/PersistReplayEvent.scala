package ch.elca.advisory
package plugin.snpashot

import plugin.BasePersistenceSpec

import akka.persistence.typed.PersistenceId
import ch.elca.advisory.plugin.journal.SnapshotTestPersistentActor
import scala.concurrent.duration.*

import java.util.UUID

class PersistReplayEvent extends BasePersistenceSpec{

  "A Persistent Actor with Snapshot" should {

    "persist snapshots and replay events from the snapshot" in {
      val correctUUID = UUID.randomUUID().toString
      val persistenceId = PersistenceId.ofUniqueId(correctUUID)
      val probe = testKit.createTestProbe[SnapshotTestPersistentActor.State]()

      // Create and interact with the actor
      val actor = testKit.spawn(SnapshotTestPersistentActor(persistenceId))

      // Send commands to the actor to persist events
      val events = (1 to 20).toList.map(n => s"event${n.toString}")
      events.foreach(event => actor ! SnapshotTestPersistentActor.AddData(event))

      // Verify the state after commands
      actor ! SnapshotTestPersistentActor.GetData(probe.ref)
      probe.expectMessage(100000.seconds,SnapshotTestPersistentActor.State(events))

      // Stop and restart the actor to simulate recovery
      testKit.stop(actor)
      val restartedActor = testKit.spawn(SnapshotTestPersistentActor(persistenceId))

      // Verify the state after recovery
      restartedActor ! SnapshotTestPersistentActor.GetData(probe.ref)
      probe.expectMessage(100000.seconds, SnapshotTestPersistentActor.State(events))
    }
  }

}
