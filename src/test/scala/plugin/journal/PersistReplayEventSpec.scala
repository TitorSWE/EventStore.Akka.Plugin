package ch.elca.advisory
package plugin.journal

import plugin.BasePersistenceSpec

import akka.persistence.typed.PersistenceId

import java.util.UUID

class PersistReplayEventSpec extends BasePersistenceSpec{
  "A Persistent Actor" should {
    "persist and replay events correctly" in {
      val correctUUID = UUID.randomUUID().toString
      val persistenceId = PersistenceId.ofUniqueId(correctUUID)
      val probe = createTestProbe[TestPersistentActor.State]()

      // Create and interact with the actor
      val actor = spawn(TestPersistentActor(persistenceId))

      // Send commands to the actor
      actor ! TestPersistentActor.AddData("event1")
      actor ! TestPersistentActor.AddData("event2")

      actor ! TestPersistentActor.AddMultipleData(List("event3", "event4"))

      // Verify the state after commands
      actor ! TestPersistentActor.GetData(probe.ref)
      probe.expectMessage(TestPersistentActor.State(List("event1", "event2", "event3", "event4")))

      // Stop and restart the actor to simulate a failure and recovery scenario
      testKit.stop(actor)
      val restartedActor = spawn(TestPersistentActor(persistenceId))

      // Verify the state after recovery
      restartedActor ! TestPersistentActor.GetData(probe.ref)
      probe.expectMessage(TestPersistentActor.State(List("event1", "event2", "event3", "event4")))
    }
  }
}
