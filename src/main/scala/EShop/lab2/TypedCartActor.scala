package EShop.lab2

import akka.actor.Cancellable
import akka.actor.TypedActor.{context, self}
import EShop.lab3.TypedOrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.TypedOrderManager

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                                  extends Command
  case class RemoveItem(item: Any)                                               extends Command
  case object ExpireCart                                                         extends Command
  case class StartCheckout(orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                           extends Command
  case object ConfirmCheckoutClosed                                              extends Command
  case class GetItems(sender: ActorRef[Cart])                                    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case class ItemAdded(item: Any)                                          extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable]) {
    def cart: Cart
  }
  case object Empty extends State(None) {
    def cart: Cart = Cart.empty
  }
  case class NonEmpty(cart: Cart, timer: Cancellable) extends State(Some(timer))
  case class InCheckout(cart: Cart)                   extends State(None)
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable = context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty


  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive[TypedCartActor.Command] (
    (ctx, cmd) =>
      cmd match {
        case AddItem(item) => nonEmpty(new Cart(Seq(item)), scheduleTimer(ctx))
      }

    )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receiveMessage {
    case AddItem(item) => nonEmpty(cart.addItem(item), timer)
    case RemoveItem(item) => {
      val newCart: Cart = cart.removeItem(item)
      if (newCart.size == 0) empty
      Behaviors.same
    }
    case StartCheckout(_) => inCheckout(cart)
    case ExpireCart => empty
  }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive[Command](
    (ctx, msg) =>
      msg match {
        case ConfirmCheckoutCancelled => nonEmpty(cart, scheduleTimer(ctx))
        case ConfirmCheckoutClosed => empty
        case _ => Behaviors.same
      }


  )

}
