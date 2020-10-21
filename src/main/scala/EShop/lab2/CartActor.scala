package EShop.lab2

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._
  var cart = Cart.empty

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable = ???

  def receive: Receive = LoggingReceive{
    case CartActor.AddItem(item) => {
      cart.addItem(item)
      // todo
//      context become nonEmpty(cart, scheduleTimer)
    }
  }

  def empty: Receive = ???

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case CartActor.AddItem(item) => {
      cart.addItem(item)
    }
  }

  def inCheckout(cart: Cart): Receive = ???

}


object CartApp extends App{
  val system = ActorSystem("CartSystem")
  val mainActor = system.actorOf(Props[CartActor])

  mainActor ! CartActor.AddItem(5)



}