package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager._
import EShop.lab3.Payment.DoPayment
import akka.actor.{Actor, ActorRef}
import akka.event.{Logging, LoggingReceive}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object OrderManager {

  sealed trait Command

  case class AddItem(id: String) extends Command

  case class RemoveItem(id: String) extends Command

  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command

  case object Buy extends Command

  case object Pay extends Command

  case class ConfirmCheckoutStarted(checkoutRef: ActorRef) extends Command

  case class ConfirmPaymentStarted(paymentRef: ActorRef) extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Ack

  case object Done extends Ack //trivial ACK
}

class OrderManager extends Actor {
  implicit val timeout: Timeout = Timeout(5 seconds)
  val log = Logging(context.system, this)
  override def receive = uninitialized

  def uninitialized: Receive = LoggingReceive {
    case AddItem(item) =>
      val cartActor = context.actorOf(CartActor.props(), "CartActor")
      cartActor ! CartActor.AddItem(item)
      context become open(cartActor)
      sender() ! Done
  }

  def open(cartActor: ActorRef): Receive = LoggingReceive {
    case AddItem(item) => cartActor ! CartActor.AddItem(item)
      sender() ! Done
    case RemoveItem(item) => cartActor ! CartActor.RemoveItem(item)
    case Buy =>
      cartActor ! CartActor.StartCheckout
      context become inCheckout(cartActor, sender())
  }

  def inCheckout(cartActorRef: ActorRef, senderRef: ActorRef): Receive = {
    case ConfirmCheckoutStarted(checkoutRef) =>
      checkoutRef ! Checkout.StartCheckout
      context become inCheckout(checkoutRef)
      senderRef ! Done
  }

  def inCheckout(checkoutActorRef: ActorRef): Receive = {
    case SelectDeliveryAndPaymentMethod(delivery, payment) =>
      checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! Checkout.SelectPayment(payment)
      context become inPayment(sender())

  }

  def inPayment(senderRef: ActorRef): Receive = {
    case ConfirmPaymentStarted(paymentRef) =>
      context become inPayment(paymentRef, senderRef)
      senderRef ! Done
      log.debug("ConfirmPaymentStarted")
  }

  def inPayment(paymentActorRef: ActorRef, senderRef: ActorRef): Receive = {
    case Pay =>
      paymentActorRef ! DoPayment
      context become inPayment(paymentActorRef, sender())
    case ConfirmPaymentReceived =>
      context become finished
      senderRef ! Done
  }

  def finished: Receive = {
    case _ =>
      sender ! "order manager finished job"
  }
}
