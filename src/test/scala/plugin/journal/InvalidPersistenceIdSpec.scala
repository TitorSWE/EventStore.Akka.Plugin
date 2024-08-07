package ch.elca.advisory
package plugin.journal

import plugin.BasePersistenceSpec

import akka.actor.testkit.typed.Effect
import akka.actor.testkit.typed.javadsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestProbe}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.{StatusReply, ask}
import akka.persistence.{AtomicWrite, Persistence, PersistentRepr}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.testkit.PersistenceTestKitDurableStateStorePlugin.config
import akka.persistence.typed.PersistenceId
import akka.serialization.SerializationExtension
import ch.elca.advisory.plugin.journal.TestPersistentActor.State

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}



class InvalidPersistenceIdSpec extends BasePersistenceSpec {

  "A Persistent Actor with a non UUID persistenceId" should {
    "stop when trying to persist an event " in {
      val invalidPersistenceId = PersistenceId.ofUniqueId("invalid-id")
      val persistent = testKit.spawn(TestPersistentActor(invalidPersistenceId))
      val probe = testKit.createTestProbe()
      persistent ! TestPersistentActor.AddData("Some event")
      probe.expectTerminated(persistent, 10.seconds)
    }
  }
  
  "The Supervisor A Persistent Actor with a non UUID persistenceId " should {
    "receive a signal when the child wants to persist an event" in {
      val probe = testKit.createTestProbe()
    }
  }
  
  // The state should not be updated
  // When restarting, the state should not be updated
}

