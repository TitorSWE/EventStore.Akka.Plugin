package ch.elca.advisory
package plugin.journal

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.persistence.typed.{EventSeq, PersistenceId, SnapshotSelectionCriteria}
import akka.serialization.jackson.JsonSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

object TestPersistentActor {
  
  // Commands
  sealed trait Command extends JsonSerializable
  case class AddData(data: String) extends Command
  case class AddMultipleData(data: List[String]) extends Command
  case class GetData(replyTo: akka.actor.typed.ActorRef[State]) extends Command

  // Events
  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
  )
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DataAdded], name = "dataAdded")
  ))
  sealed trait Event extends JsonSerializable
  case class DataAdded(data: String) extends Event

  // State
  case class State(data: List[String] = List.empty) extends JsonSerializable
 
  // Adapter
  case class MyWrapper(override val payload: Event, override val tags: Set[String], override val eventClassName: String) extends EventStoreWrapper[Event] with JsonSerializable

  class MyAdapter extends EventStoreAdapter[Event]:
    override def toJournal(e: Event): EventStoreWrapper[Event] = e match {
      case DataAdded(data) => MyWrapper(e, Set("newTag"), e.getClass.getName)
    }

    override def manifest(event: Event): String = "my-adapter"

    override def fromJournal(p: EventStoreWrapper[Event], manifest: String): EventSeq[Event] = EventSeq.single(p.payload)


  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      State(),
      (state, command) => command match {
        case AddData(data) =>
          context.log.info(s"Processing AddData: $data")
          Effect.persist(DataAdded(data))
        case AddMultipleData(data) =>
          Effect.persist(data.map(DataAdded))
        case GetData(replyTo) =>
          context.log.info(s"Processing GetData")
          replyTo ! state
          Effect.none
      },
      (state, event) => event match {
        case DataAdded(data) => state.copy(data = state.data :+ data)
      }
    ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
  }
}
