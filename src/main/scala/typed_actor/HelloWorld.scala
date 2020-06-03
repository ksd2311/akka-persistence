package typed_actor

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import typed_actor.HelloWorldMain.SayHello

/**
  * Created by kartik on Feb 26, 2020
  */

object HelloWorld {

  final case class Greet(whom: String, replyTo: ActorRef[Greeted])

  final case class Greeted(whom: String, from: ActorRef[Greet])

  def apply(): Behavior[Greet] = {
    Behaviors.receive{ (context, message) =>
     println(s"Hello ${message.whom}!")
      message.replyTo ! Greeted(message.whom, context.self)
      Behaviors.same
    }
  }

}

object HelloWorldBot {

  def bot(greetingCounter: Int, max: Int): Behavior[HelloWorld.Greeted] = {
    Behaviors.receive{ (context, message) =>
      val n = greetingCounter + 1
      println(s"Greeting $n for ${message.whom}")
      if (n == max) {
        Behaviors.stopped
      } else {
        message.from ! HelloWorld.Greet(message.whom, context.self)
        bot(n, max)
      }
    }
  }

  def apply(max: Int): Behavior[HelloWorld.Greeted] = {
    bot(0, max)
  }
}

object HelloWorldMain {

  final case class SayHello(name: String)

  def apply(): Behavior[SayHello] = {
    Behaviors.setup{ context =>
      val greeter = context.spawn(HelloWorld(), "greeter")
      Behaviors.receiveMessage{ message =>
        val replyTo = context.spawn(HelloWorldBot(3), message.name)
        greeter ! HelloWorld.Greet(message.name, replyTo)
        Behaviors.same
      }
    }
  }
}

object TypedActorApp extends App {
  val system: ActorSystem[HelloWorldMain.SayHello] = ActorSystem(HelloWorldMain(), "Hello")

  system ! SayHello("World")
//  system ! SayHello("Akka")
}