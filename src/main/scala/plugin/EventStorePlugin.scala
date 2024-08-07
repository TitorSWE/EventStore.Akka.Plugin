package ch.elca.advisory
package plugin

import akka.actor.{Actor, ActorLogging, ActorSystem}
import com.eventstore.dbclient.{CreateProjectionOptions, EventStoreDBClient, EventStoreDBClientSettings, EventStoreDBConnectionString, EventStoreDBProjectionManagementClient}
import com.typesafe.config.{Config, ConfigFactory}

import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}



trait EventStorePlugin { 

  // Use the Akka system's dispatcher as the execution context
  // abstract
  implicit val ec: ExecutionContext /*= context.system.dispatchers.lookup("eventstore.journal.plugin-dispatcher")*/
  val serialization: EventStoreSerialization
  
  
  private val config: Config = ConfigFactory.load()
  private def getEventStoreConnectionString: String = {
    config.getString("eventstore.connection-string")
  }
  val settings: EventStoreDBClientSettings = EventStoreDBConnectionString.parseOrThrow(getEventStoreConnectionString)
  val client: EventStoreDBClient = EventStoreDBClient.create(settings)
  

  
  
}

