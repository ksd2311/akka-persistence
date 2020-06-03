package event_sourcing

import java.time.LocalDate

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike
//import akka.actor.typed.eventstream.EventStream

/**
  * Created by kartik on Feb 27, 2020
  */

class AccountantTestSpec extends ScalaTestWithActorTestKit(
  s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot"
    """) with WordSpecLike {

  "Accountant Test" should {

    "record an invoice and get current status" in {
      //      val eventProbe = createTestProbe[InmemJournal.Operation]()
      //      system.eventStream ! EventStream.Subscribe(eventProbe.ref)

      val accountant  = spawn(Accountant("Accountant-1"))
      val statusProbe = createTestProbe[InvoiceStatus]

      val reactoreInvoice = RecordInvoice("Reactore", LocalDate.now, 2500, statusProbe.ref)
      accountant ! reactoreInvoice
      statusProbe.expectMessage[Accepted](Accepted(reactoreInvoice.companyName, reactoreInvoice.amount, reactoreInvoice.date))
      //      eventProbe.expectMessageType[InmemJournal.Write].event.asInstanceOf[InvoiceRecorded].companyName shouldBe reactoreInvoice.companyName

      val jswInvoice = RecordInvoice("JSW", LocalDate.now.plusDays(5), 5000, statusProbe.ref)
      accountant ! jswInvoice
      statusProbe.expectMessage[Accepted](Accepted(jswInvoice.companyName, jswInvoice.amount, jswInvoice.date))

      accountant ! GetStatus(statusProbe.ref)
      statusProbe.expectMessage[InvoiceHistory](InvoiceHistory(2, reactoreInvoice.amount + jswInvoice.amount))
    }

    "restore the previous state" in {
      val id         = "Accountant-2"
      val accountant = spawn(Accountant(id))
      val probe      = createTestProbe[InvoiceStatus]

      val tataInvoice = RecordInvoice("TATA", LocalDate.now, 3000, probe.ref)
      accountant ! tataInvoice
      probe.expectMessage[Accepted](Accepted(tataInvoice.companyName, tataInvoice.amount, tataInvoice.date))

      val hondaInvoice = RecordInvoice("Honda", LocalDate.now.plusDays(5), 4000, probe.ref)
      accountant ! hondaInvoice
      probe.expectMessage[Accepted](Accepted(hondaInvoice.companyName, hondaInvoice.amount, hondaInvoice.date))

      accountant ! GetStatus(probe.ref)
      probe.expectMessage[InvoiceHistory](InvoiceHistory(2, tataInvoice.amount + hondaInvoice.amount))

      testKit.stop(accountant)

      println("Stopping And Restarting")
      val restartedAccountant = spawn(Accountant(id))

      restartedAccountant ! GetStatus(probe.ref)
      probe.expectMessage[InvoiceHistory](InvoiceHistory(2, tataInvoice.amount + hondaInvoice.amount))
    }
  }

}
