package EShop.lab2

import EShop.lab2.CartActor.ConfirmCheckoutClosed
import EShop.lab2.Checkout._
import EShop.lab3.{OrderManager, Payment}
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                        extends Event
  case class PaymentStarted(payment: ActorRef)      extends Event
  case object CheckoutStarted                       extends Event
  case object CheckoutCancelled                     extends Event
  case class DeliveryMethodSelected(method: String) extends Event

  def props(cart: ActorRef) = Props(new Checkout(cart))
}

class Checkout(
  cartActor: ActorRef
) extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher
  private def scheduleCheckoutTimer: Cancellable = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def schedulePaymentTimer: Cancellable = scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  def receive: Receive = LoggingReceive{
    case StartCheckout =>
      context become selectingDelivery(scheduleCheckoutTimer)
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
    case ExpireCheckout =>
      context become cancelled
    case SelectDeliveryMethod(_) =>
      context become selectingPaymentMethod(schedulePaymentTimer)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive{
    case ExpirePayment =>
      context become cancelled
    case ExpireCheckout =>
      timer.cancel()
      context become cancelled
    case SelectPayment(method) =>
      val orderManger = sender()
      val paymentRef = context.actorOf(Payment.props(method = method, orderManager = orderManger, checkout = self ), "PaymentActor")
      sender() ! OrderManager.ConfirmPaymentStarted(paymentRef)
      context become processingPayment(timer)
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive{
    case ExpirePayment =>
      context become cancelled
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
    case ConfirmPaymentReceived =>
      timer.cancel()
      cartActor ! ConfirmCheckoutClosed
      context become closed
  }

  def cancelled: Receive = LoggingReceive {
    case _ =>
  }

  def closed: Receive = LoggingReceive{
    case _ =>
  }

}
