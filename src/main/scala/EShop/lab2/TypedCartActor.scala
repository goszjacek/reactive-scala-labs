package EShop.lab2

import akka.actor.Cancellable
import akka.actor.TypedActor.{context, self}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable = context.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty


  def empty: Behavior[TypedCartActor.Command] = Behaviors.receiveMessage{
    case AddItem(item) => nonEmpty(Cart(Seq(item)), null)
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receiveMessage {
    case AddItem(item) => nonEmpty(cart.addItem(item), null)
    case RemoveItem(item) => {
      val newCart: Cart = cart.removeItem(item)
      if (newCart.size == 0) empty
      Behaviors.same
    }
    case StartCheckout => inCheckout(cart)
  }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receiveMessage{
    case ConfirmCheckoutCancelled => nonEmpty(cart, null)
    case ConfirmCheckoutClosed => empty
    case _ => Behaviors.same
  }

}
