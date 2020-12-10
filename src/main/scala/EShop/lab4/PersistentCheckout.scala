package EShop.lab4

import EShop.lab2.CartActor
import EShop.lab2.CartActor.ConfirmCheckoutClosed
import EShop.lab3.{OrderManager, Payment}
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._
  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration     = 1.seconds
  private def scheduleCheckoutTimer: Cancellable = scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)
  private def schedulePaymentTimer: Cancellable = scheduler.scheduleOnce(timerDuration, self, ExpirePayment)

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    ???
    event match {
      case CheckoutStarted                => selectingDelivery(scheduleCheckoutTimer)
      case DeliveryMethodSelected(method) => selectingPaymentMethod(schedulePaymentTimer)
      case CheckOutClosed                 => closed
      case CheckoutCancelled => cancelled
      case PaymentStarted(payment)        => processingPayment(maybeTimer.getOrElse(schedulePaymentTimer))

    }
  }

  def receiveCommand: Receive = LoggingReceive{
    case StartCheckout =>
      persist(CheckoutStarted){ e => updateState(e) }
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpireCheckout =>
      persist(CheckoutCancelled){ e => {
        timer.cancel()
        updateState(e)
      }}
    case SelectDeliveryMethod(m) =>
      persist(DeliveryMethodSelected(m)){ e => updateState(e)}
  }


  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive{
    case ExpirePayment =>
      persist(CheckoutCancelled){ e => {
        updateState(e)
      }}
    case ExpireCheckout | CancelCheckout =>
      persist(CheckoutCancelled){ e => {
        timer.cancel()
        updateState(e)
      }}
    case SelectPayment(method) =>
      val orderManger = sender()
      val paymentRef = context.actorOf(Payment.props(method = method, orderManager = orderManger, checkout = self ), "PaymentActor")
      persist(PaymentStarted(paymentRef)){ e =>
        sender() ! OrderManager.ConfirmPaymentStarted(paymentRef)
        updateState(e, Some(timer))
      }
  }


  def processingPayment(timer: Cancellable): Receive = LoggingReceive{
    case ExpirePayment =>
      persist(CheckoutCancelled){ e => {
        updateState(e)
      }}
    case CancelCheckout =>
      persist(CheckoutCancelled){ e => {
        timer.cancel()
        updateState(e)
      }}
    case ConfirmPaymentReceived =>
      persist(CheckOutClosed){ e =>
        timer.cancel()
        cartActor ! ConfirmCheckoutClosed
        updateState(e)
      }
  }


  def cancelled: Receive = LoggingReceive{ case _ =>}

  def closed: Receive = LoggingReceive{ case _ =>}

  override def receiveRecover: Receive = LoggingReceive{case e: Event => updateState(e)}
}
