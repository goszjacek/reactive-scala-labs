package EShop.lab2

import EShop.lab2
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.event.Logging

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCheckout {

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
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                        extends Event
  case class PaymentStarted(payment: ActorRef[Any]) extends Event
}

class TypedCheckout {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private def scheduleCheckoutTimer(context: ActorContext[TypedCheckout.Command]): Cancellable = context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)
  private def schedulePaymentTimer(context: ActorContext[TypedCheckout.Command]): Cancellable = context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)



  def start: Behavior[TypedCheckout.Command] = Behaviors.receive[TypedCheckout.Command](
    (ctx, cmd) => cmd match {
      case StartCheckout => {
        selectingDelivery(scheduleCheckoutTimer(ctx))
      }
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive[Command](
    (ctx, cmd) => cmd match {
      case CancelCheckout => {
        timer.cancel()
        cancelled
      }
      case ExpireCheckout => {
        ctx.log.info("Checkout expired")
        cancelled
      }
      case SelectDeliveryMethod(_) => {
        selectingPaymentMethod(schedulePaymentTimer(ctx))
      }
    }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive[Command](
    (ctx, cmd) => cmd match {
      case ExpirePayment => {
        ctx.log.info("payment expired")
        cancelled
      }
      case ExpireCheckout => {
        timer.cancel()
       cancelled
      }
      case SelectPayment(_) => {
        processingPayment(timer)
      }
      case CancelCheckout => {
        timer.cancel()
        cancelled
      }
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive[Command](
    (ctx, cmd) => cmd match {
      case ExpirePayment => {
        ctx.log.info("Payment expired")
        cancelled
      }
      case CancelCheckout => {
        timer.cancel()
      cancelled
      }
      case ConfirmPaymentReceived => {
        timer.cancel()
       closed
      }
      case ExpireCheckout =>
        cancelled

    }
  )


  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case any => cancelled
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped

}
