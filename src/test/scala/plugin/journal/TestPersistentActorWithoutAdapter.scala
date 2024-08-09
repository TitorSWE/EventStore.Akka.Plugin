package ch.elca.advisory
package plugin.journal



import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.persistence.typed.{EventSeq, PersistenceId, SnapshotSelectionCriteria}
import akka.serialization.jackson.JsonSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

object TestPersistentActorWithoutAdapter {

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
    )
  }
}

