package faultTolerance

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import faultTolerance.Protocol.{Fail, Hello}
import org.scalatest.WordSpecLike

/**
  * Created by kartik on Apr 20, 2020
  */

class BossTestSpec extends ScalaTestWithActorTestKit with WordSpecLike {

  "BossTestSpec" should {
    "send message to child" in {
      val bossActor   = spawn(Boss(), "boss1")
      val stringProbe = createTestProbe[String]("StringProbe")
      val hello       = Hello("Hello World", stringProbe.ref)
      bossActor ! hello
      stringProbe.expectMessage("Hello World")

      bossActor ! Fail("Fail here and see")
    }
  }

}
