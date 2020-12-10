package EShop.lab4

import EShop.lab2.{Cart, CartActor, Checkout}
import EShop.lab3.OrderManager.ConfirmCheckoutStarted
import akka.actor.TypedActor.dispatcher
import akka.actor.{Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.duration._

object PersistentCartActor {

  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5.seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {
    lazy val targetTimer = timer.getOrElse(scheduleTimer)
    context become (
      event match {
        case CartExpired | CheckoutClosed                                     => empty
        case CheckoutCancelled(cart)                                          => nonEmpty(cart, scheduleTimer)
        case ItemAdded(item, cart)                                            => nonEmpty(cart.addItem(item), scheduleTimer)
        case CartEmptied                                                      => ???
        case ItemRemoved(item, cart) if cart.contains(item) && cart.size == 1 => empty
        case ItemRemoved(item, cart) if cart.size > 1                         => nonEmpty(cart.removeItem(item), targetTimer)
        case CheckoutStarted(checkoutRef, cart)                               => inCheckout(cart)
      }
    )
  }

  def empty: Receive = {
    case CartActor.AddItem(item) =>
      persist(ItemAdded(item, Cart.empty)) { event =>
        updateState(event)
      }
    case GetItems => sender ! Cart.empty
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive =
    LoggingReceive {
      case CartActor.AddItem(item) =>
        persist(ItemAdded(item, cart)) { event =>
          updateState(event, Some(timer))
        }
      case CartActor.RemoveItem(item) =>
        persist(ItemRemoved(item, cart)) { event =>
          updateState(event, Some(timer))
        }
      case CartActor.StartCheckout =>
        val checkout = context.actorOf(Checkout.props(self), "checkout")
        persist(CheckoutStarted(checkoutRef = checkout, cart = cart)) { event =>
          sender() ! ConfirmCheckoutStarted(checkout)
          updateState(event)
        }

      case CartActor.ExpireCart =>
        persist(CartExpired) { event =>
          updateState(event)
        }
      case GetItems => sender() ! cart.items
    }

  def inCheckout(cart: Cart): Receive =
    LoggingReceive {
      case CartActor.ConfirmCheckoutCancelled =>
        persist(CheckoutCancelled(cart)) { e =>
          this.updateState(e)
        }
        context become nonEmpty(cart, scheduleTimer)
      case CartActor.ConfirmCheckoutClosed =>
        persist(CheckoutClosed)(e => this.updateState(e))
      case CartActor.AddItem =>
      case GetItems          => sender() ! cart
    }

  override def receiveRecover: Receive =
    LoggingReceive {
      case e: Event => updateState(e)
    }
}
