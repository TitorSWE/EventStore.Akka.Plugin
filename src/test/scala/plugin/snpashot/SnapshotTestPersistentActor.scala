package ch.elca.advisory
package plugin.journal

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.persistence.typed.{EventSeq, PersistenceId, SnapshotSelectionCriteria}
import akka.serialization.jackson.JsonSerializable
import ch.elca.advisory.plugin.snapshot.{EventStoreSnapshotAdapter, EventStoreSnapshotWrapper}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

object SnapshotTestPersistentActor {

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


  case class State(data: List[String] = List.empty) extends JsonSerializable

  // Adapter
  case class MyJournalWrapper(override val payload: Event, override val tags: Set[String], override val eventClassName: String) extends EventStoreJournalWrapper[Event] with JsonSerializable

  class MyAdapter extends EventStoreJournalAdapter[Event] {
    override def toJournal(e: Event): EventStoreJournalWrapper[Event] = e match {
      case DataAdded(data) => MyJournalWrapper(e, Set("newTag"), e.getClass.getName)
    }

    override def manifest(event: Event): String = "my-adapter"

    override def fromJournal(p: EventStoreJournalWrapper[Event], manifest: String): EventSeq[Event] = EventSeq.single(p.payload)

  }


  case class MySnapWrapper(override val payload: State, override val version: Int, override val className: String) extends EventStoreSnapshotWrapper[State] with JsonSerializable

  class MySnapAdapter extends EventStoreSnapshotAdapter[State] {
    override def adaptSnapshot(state: State): EventStoreSnapshotWrapper[State] = MySnapWrapper(state, 1, state.getClass.getName)

    override def reverseAdapt(snapshot: EventStoreSnapshotWrapper[State]): State = snapshot.payload
  }




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
    ).eventAdapter(MyAdapter()).withRetention(RetentionCriteria.snapshotEvery(8,3))
  }
}
