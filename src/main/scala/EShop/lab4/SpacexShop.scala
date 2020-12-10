package EShop.lab4

import EShop.lab2.CartActor.GetItems
import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager
import EShop.lab4.PersistentCartActor.itsCheckout
import akka.actor.{Actor, ActorSystem, Props}
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DemoActor(system: ActorSystem) extends Actor {

  val cartId: String = "spacex"

  override def receive: Receive =
    LoggingReceive {
      case "init" =>
        val cartActor = system.actorOf(Props(new PersistentCartActor(cartId)))
        cartActor ! CartActor.AddItem("starlink")
        cartActor ! CartActor.AddItem("starter-kit")
        cartActor ! GetItems
        cartActor ! CartActor.RemoveItem("starlink")
        cartActor ! CartActor.RemoveItem("starship")
        cartActor ! CartActor.StartCheckout

      case OrderManager.ConfirmCheckoutStarted(_) =>
        context.system.terminate()

      case "resume" =>
        val cartActor     = system.actorOf(Props(new PersistentCartActor(cartId)))
        val checkoutActor = system.actorOf(Props(new PersistentCheckout(cartActor, itsCheckout(cartId))))

        checkoutActor ! Checkout.SelectDeliveryMethod("falcon 9")
        Thread.sleep(1400)
        checkoutActor ! Checkout.SelectPayment("transfer")
        checkoutActor ! Checkout.ConfirmPaymentReceived
        checkoutActor ! ""

        Thread.sleep(1400)
        cartActor ! GetItems
        context.system.terminate()
    }

}

object Demo extends App {
  val system = ActorSystem("System")
  var main   = system.actorOf(Props(new DemoActor(system)))
  main ! "init"

  Await.result(system.whenTerminated, Duration.Inf)

  val newSystem = ActorSystem("System")
  main = newSystem.actorOf(Props(new DemoActor(newSystem)))
  main ! "resume"
}

