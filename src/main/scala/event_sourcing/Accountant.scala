package event_sourcing

import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import event_sourcing.AccountantHandler.{GetState, InvoiceDetails}

import scala.concurrent.duration._

/**
  * Created by kartik on Feb 26, 2020
  */

//COMMAND
sealed trait InvoiceCommand

final case class RecordInvoice(companyName: String, date: LocalDate, amount: Int, replyTo: ActorRef[InvoiceStatus]) extends InvoiceCommand

final case class GetStatus(replyTo: ActorRef[InvoiceStatus]) extends InvoiceCommand

//EVENT
final case class InvoiceRecorded(id: Int, companyName: String, date: LocalDate, amount: Int)

//STATE
sealed trait AccountantState {
  def latestInvoiceId: AtomicInteger

  def totalAmount: AtomicInteger
}

case object EmptyState extends AccountantState {
  override lazy val latestInvoiceId: AtomicInteger = new AtomicInteger(0)
  override lazy val totalAmount    : AtomicInteger = new AtomicInteger(0)
}

final case class CurrentState(latestInvoiceId: AtomicInteger, totalAmount: AtomicInteger /*, uniqueInvoices: Seq[String]*/) extends AccountantState

sealed trait InvoiceStatus

final case class Accepted(companyName: String, amount: Int, date: LocalDate) extends InvoiceStatus

final case class InvoiceHistory(latestInvoiceId: Int, totalAmount: Int) extends InvoiceStatus

final case class Rejected(reason: String) extends InvoiceStatus

object Accountant {

  val commandHandler: (AccountantState, InvoiceCommand) => Effect[InvoiceRecorded, AccountantState] = { (state, recordInvoice) =>
    recordInvoice match {
      case RecordInvoice(companyName, date, amount, replyTo) => {
        val invoiceRecordedEvent = InvoiceRecorded(state.latestInvoiceId.get(), companyName, date, amount)
        println(s"Invoice Event : '${companyName} -> $amount', state : $state")
        Effect.persist(invoiceRecordedEvent).thenRun{ newState: AccountantState =>
          replyTo ! Accepted(companyName, amount, date)
        }
      }
      case GetStatus(replyTo)                                => {
        println(s"State : $state")
        replyTo ! InvoiceHistory(state.latestInvoiceId.get(), state.totalAmount.get())
        Effect.none
      }
    }
  }

  val eventHandler: (AccountantState, InvoiceRecorded) => AccountantState = {
    (state, event) =>
      val finalState = state match {
        case emptyState@EmptyState                             => event match {
          case InvoiceRecorded(_, _, _, amount) =>
            CurrentState(new AtomicInteger(1), new AtomicInteger(amount))
        }
        case cState@CurrentState(latestInvoiceId, totalAmount) =>
          event match {
            case InvoiceRecorded(_, _, _, amount) =>
              latestInvoiceId.incrementAndGet()
              totalAmount.addAndGet(amount)
              cState
          }
      }
      println(s"State in event handler : $finalState")
      finalState
  }

  def apply(id: String): Behavior[InvoiceCommand] = {
    //    Behaviors.receiveMessage{ context =>
    EventSourcedBehavior[InvoiceCommand, InvoiceRecorded, AccountantState](
      persistenceId = PersistenceId(s"Accountant-$id"),
      emptyState = EmptyState,
      commandHandler = commandHandler,
      eventHandler = eventHandler)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))

    //    }
  }
}

object AccountantHandler {
  def invoiceBehavior: Behavior[InvoiceStatus] = {
    Behaviors.same
  }

  sealed trait InvoiceDetailCommand

  final case class InvoiceDetails(companyName: String, date: LocalDate, amount: Int) extends InvoiceDetailCommand

  final case object GetState extends InvoiceDetailCommand

  def apply(accountantId: String): Behavior[InvoiceDetailCommand] = {
    Behaviors.setup{ (context) =>
      val accountant    = context.spawn(Accountant(accountantId), s"Accountant-$accountantId")
      val invoiceStatus = context.spawn(AccountantHandler.invoiceBehavior, "InvoiceHandler")
      Behaviors.receiveMessage{
        case GetState                                  => {
          accountant ! GetStatus(invoiceStatus.ref)
          Behaviors.same
        }
        case InvoiceDetails(companyName, date, amount) => {
          accountant ! RecordInvoice(companyName, date, amount, invoiceStatus.ref)
          Behaviors.same
        }
      }

    }
  }
}


object AccountantTest extends App {
  val system = ActorSystem(AccountantHandler("Accountant"), "AccountantSystem")

  system ! InvoiceDetails("TATA", LocalDate.now, 2500)
  system ! InvoiceDetails("Reactore", LocalDate.now, 3500)
  system ! InvoiceDetails("JSW", LocalDate.now, 2000)
  system ! GetState
}