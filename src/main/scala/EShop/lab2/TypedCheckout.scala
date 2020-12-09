package EShop.lab2

import EShop.lab2
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.event.Logging

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.TypedOrderManager

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

import EShop.lab3.{TypedOrderManager, TypedPayment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cats.implicits.catsSyntaxOptionId

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCheckout {

  sealed trait Command
  case object StartCheckout                                                                       extends Command
  case class SelectDeliveryMethod(method: String)                                                 extends Command
  case object CancelCheckout                                                                      extends Command
  case object ExpireCheckout                                                                      extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command
  case object ExpirePayment                                                                       extends Command
  case object ConfirmPaymentReceived                                                              extends Command

  sealed trait Event
  case object CheckOutClosed                                         extends Event
  case class PaymentStarted(payment: ActorRef[TypedPayment.Command]) extends Event
  case object CheckoutStarted                                        extends Event
  case object CheckoutCancelled                                      extends Event
  case class DeliveryMethodSelected(method: String)                  extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(timer.some)
  case class SelectingPaymentMethod(timer: Cancellable) extends State(timer.some)
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(timer.some)
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
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
      case SelectPayment(payment , orderManagerRef ) => {
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
