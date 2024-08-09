package ch.elca.advisory
package plugin.journal

import plugin.BasePersistenceSpec

import akka.persistence.typed.PersistenceId

import java.util.UUID
import scala.concurrent.duration.*

class InvalidEventAdapterSpec extends BasePersistenceSpec{

  "A Persistent Actor without event adapter " should {
    "fail to persist event" in {
      val correctUUID = UUID.randomUUID().toString
      val invalidPersistenceId = PersistenceId.ofUniqueId(correctUUID)
      val persistent = testKit.spawn(TestPersistentActorWithoutAdapter(invalidPersistenceId))
      val probe = testKit.createTestProbe()
      persistent ! TestPersistentActorWithoutAdapter.AddData("Some event")
      probe.expectTerminated(persistent, 10.seconds)
    }
  }
}
