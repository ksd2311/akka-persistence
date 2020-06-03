package event_sourcing

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}

import scala.concurrent.duration._

/**
  * Created by kartik on Feb 25, 2020
  */

object PersistenceBehavior {

  //COMMAND
  sealed trait Command

  final case class AddData(data: String, replyTo: ActorRef[DataStatus]) extends Command

  final case class ClearData(replyTo: ActorRef[DataStatus]) extends Command

  final case class GetState(replyTo: ActorRef[DataStatus]) extends Command

  //EVENT
  sealed trait Event

  final case class DataAdded(data: String) extends Event

  case object DataCleared extends Event

  //STATE
  final case class State(history: Seq[String] = Nil)

  sealed trait DataStatus

  final case class DataAccepted(data: String) extends DataStatus

  final case class DataHistory(history: Seq[String]) extends DataStatus

  final case object DataReset extends DataStatus

  val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    command match {
      case AddData(data, replyTo) => {
        println(s"Added data:$data")
        Effect.persist(DataAdded(data)).thenRun(state => replyTo ! DataAccepted(data))
      }
      case ClearData(replyTo)     => {
        println("Received Clear")
        Effect.persist(DataCleared).thenRun(state => replyTo ! DataReset)
      }
      case GetState(replyTo)      => replyTo ! DataHistory(state.history)
        Effect.none
    }
  }

  val eventHandler: (State, Event) => State = {
    (state, event) =>
      event match {
        case DataAdded(data) => {
          val currentState = state.copy((data +: state.history).take(5))
          println(s"Current State : $currentState")
          currentState
        }
        case DataCleared     => {
          println("cleared state")
          state.copy(Nil) // State(Nil)
        }
      }
  }

  def apply(id: String): Behavior[Command] = {
    EventSourcedBehavior[Command, Event, State](persistenceId = PersistenceId(id),
      emptyState = State(),
      commandHandler = commandHandler,
      eventHandler = eventHandler)
      /*.snapshotWhen{
        case (state, event: DataAdded, sequenceNumber) => true
        case (state, DataCleared, sequenceNumber)      => false
      }*/
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))

  }

}

//
//object PersistenceTestApp extends App {
//  val system1: ActorSystem[PersistenceBehavior.Command] = ActorSystem(PersistenceBehavior("BehaviorTest"), "Data-persistence")
//  system1 ! AddData("common")
//  Thread.sleep(500)
//
//  system1 ! ClearData
//  Thread.sleep(500)
//
//  system1 ! AddData("op")
//  Thread.sleep(500)
//
//  system1 ! AddData("mineone")
//  Thread.sleep(500)
//
//  system1.terminate()
////
////  val system2: ActorSystem[PersistenceBehavior.Command] = ActorSystem(PersistenceBehavior("BehaviorTest"), "Data-persistence")
////  system2 ! AddData("common")
////  Thread.sleep(500)
//
//}

