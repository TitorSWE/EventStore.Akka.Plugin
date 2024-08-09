package ch.elca.advisory
package plugin.journal

import akka.actor.{Actor, ActorSystem}
import ch.elca.advisory.plugin.{EventStorePlugin, EventStoreSerialization}
import com.eventstore.dbclient.{CreateProjectionOptions, EventStoreDBProjectionManagementClient}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait EventStoreJournalPlugin extends EventStorePlugin {self:Actor => 
  
  override implicit val ec: ExecutionContext = context.dispatcher
  override val serialization: EventStoreSerialization = EventStoreSerialization(context.system)

  protected def initializeProjections(): Unit = {

    
    val projectionClient: EventStoreDBProjectionManagementClient = EventStoreDBProjectionManagementClient.create(settings)
    val projectionOptions: CreateProjectionOptions = CreateProjectionOptions.get().emitEnabled(true).trackEmittedStreams(true)

    // Define your projection query and name
    
    /*
    * TO DO : Change the projection -> only include the payload, (maybe the flag) and set the event type
    * */
    val tagProjectionName: String = "tag-projection"
    val tagProjectionQuery: String =
      """
        |fromAll()
        |  .when({
        |    $init: function () { return { count: 0, lastProcessedPosition: 0 }; },
        |    $any: function (state, event) {
        |      if (typeof event.bodyRaw === 'string') {
        |        try {
        |          var parsedData = JSON.parse(event.bodyRaw);
        |          if (parsedData._emitted === true) {
        |            log('Skipping emitted event at position: ' + event.position);
        |            return;
        |          }
        |          if (Array.isArray(parsedData.tags)) {
        |            parsedData._emitted = true;
        |            parsedData.persistenceId = event.streamId;
        |            parsedData.sequenceNr = event.sequenceNumber + 1;
        |            if (event.metadata && typeof event.metadata === 'object' && event.metadata.timestamp) {
        |              parsedData._originalTimestamp = event.metadata.timestamp;
        |            }
        |            parsedData.tags.forEach(function(tag) {
        |              if (typeof tag === 'string') {
        |                var taggedStream = 'tag-' + tag;
        |                emit(taggedStream, event.eventType, parsedData);
        |                log('Emitted event to stream: ' + taggedStream);
        |              }
        |            });
        |          }
        |        } catch (e) {
        |          log('Error parsing JSON: ' + e.message);
        |        }
        |      } else {
        |        log('Event bodyRaw is not a string: ' + JSON.stringify(event));
        |      }
        |      state.count++;
        |      state.lastProcessedPosition = event.position;
        |      log('Current state count: ' + state.count);
        |    }
        |  });
        |""".stripMargin

    val resProj: Future[Unit] = for {
      _ <- projectionClient.create(tagProjectionName, tagProjectionQuery, projectionOptions).asScala
      _ <- projectionClient.enable(tagProjectionName).asScala
      _ <- projectionClient.enable("$streams").asScala
    } yield ()

    resProj.onComplete {
      case Success(_) => context.system.log.info("Projections created and enabled")
      case Failure(ex) => context.system.log.error(ex, "Failed to create or enable projections")
    }
  }
}

