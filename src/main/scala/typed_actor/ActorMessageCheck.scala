package typed_actor

import akka.actor.{Actor, ActorSystem, Props}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by kartik on Apr 28, 2020
  */

case object TestCmd

object ActorMessageCheck extends App {
  val system = ActorSystem("Test")
  val ref    = system.actorOf(Props(classOf[TestActor]))
  system.scheduler.schedule(500.millis, 300.millis, ref, TestCmd)
}

class TestActor extends Actor {
  var counter = 0

  override def receive: Receive = {
    case TestCmd => {
      println("receoved")
      for {
        _ <- Future.successful(true)
        _ = println("received >> " + counter)
        _ = Thread.sleep(3000)
        _ = counter += 1
        _ = println("After thread.sleep >> " + counter)
      } yield ()
    }
  }
}
