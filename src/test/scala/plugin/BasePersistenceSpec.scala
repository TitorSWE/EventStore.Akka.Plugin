package ch.elca.advisory
package plugin

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

abstract class BasePersistenceSpec extends ScalaTestWithActorTestKit(ConfigFactory.load("test-application.conf")) with AnyWordSpecLike
