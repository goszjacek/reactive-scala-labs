package EShop.lab3

import EShop.lab2.CartActor.ConfirmCheckoutClosed
import EShop.lab2.Checkout
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
    with AnyFlatSpecLike
    with ImplicitSender
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  import Checkout._

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {
    val orderManagerAsParent = TestProbe()
    val cartActorAsReceiver = TestProbe()
    val checkout = system.actorOf(Checkout.props(cartActorAsReceiver.ref))
    orderManagerAsParent.send(checkout, StartCheckout)
    orderManagerAsParent.send(checkout, SelectDeliveryMethod("InPost"))
    orderManagerAsParent.send(checkout, SelectPayment("Transfer"))
    orderManagerAsParent.send(checkout, ConfirmPaymentReceived)
    cartActorAsReceiver.expectMsg(ConfirmCheckoutClosed)


  }

}
