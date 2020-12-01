package EShop.lab2

import EShop.lab3.OrderManager.ConfirmCheckoutStarted
import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContextExecutor
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
  case object GetItems                 extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props(): Props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._
  val cart: Cart = Cart.empty

  private val log       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 100 seconds

  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher
  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = LoggingReceive{
    case CartActor.AddItem(item) =>
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case GetItems => sender() ! cart
  }

  def empty: Receive = {
    case CartActor.AddItem(item) =>
      context become nonEmpty(cart.addItem(item),scheduleTimer)
    case _ =>
    case GetItems => sender() ! cart
  }

  override def unhandled(message: Any): Unit = print(s"<<< UNHANDLED $message >>> ")

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case CartActor.AddItem(item) =>
      cart.addItem(item)
    case CartActor.RemoveItem(item) =>
      val newCart: Cart = cart.removeItem(item)
      if (newCart.size == 0){
        context become empty
      }
    case CartActor.StartCheckout =>
      val checkout = context.actorOf(Checkout.props(self), "checkout")
      sender() ! ConfirmCheckoutStarted(checkout)
      context become inCheckout(cart)
    case CartActor.ExpireCart =>
      context become empty
    case GetItems => sender() ! cart
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CartActor.ConfirmCheckoutCancelled =>
      context become nonEmpty(cart, scheduleTimer)
    case CartActor.ConfirmCheckoutClosed =>
      context become empty
    case CartActor.AddItem =>
    case GetItems => sender() ! cart
  }


}


object CartApp extends App{
  val system = ActorSystem("CartSystem")
  val mainActor = system.actorOf(Props[CartActor])

  mainActor ! CartActor.AddItem(5)
  mainActor ! CartActor.RemoveItem(5)

  import scala.concurrent.Await



  Await.result(system.whenTerminated, Duration.Inf)
}