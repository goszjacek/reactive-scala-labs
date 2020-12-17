package EShop.lab5

import EShop.lab5.HelloWorldAkkaHttpServer.Response
import EShop.lab5.ProductCatalog.{GetItems, Item, Items}
import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object HelloWorldAkkaHttpServer {
  case class Query(brand: String, productKeyWords: List[String])
  case class Response(products: List[Item])
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val queryFormat     = jsonFormat2(HelloWorldAkkaHttpServer.Query)

  //custom formatter just for example
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }
  implicit val itemFormat     = jsonFormat5(Item)
  implicit val responseFormat = jsonFormat1(HelloWorldAkkaHttpServer.Response)

}

object HelloWorldAkkaHttpServerApp extends App {
  new HelloWorldAkkaHttpServer().startServer("localhost", 9000)
}

/** Just to demonstrate how one can build akka-http based server with JsonSupport */
class HelloWorldAkkaHttpServer extends HttpApp with JsonSupport {
  val config      = ConfigFactory.load()
  val actorSystem = ActorSystem("ProductCatalog", config.getConfig("productcatalog").withFallback(config))
  val productCatalog = actorSystem.actorOf(
    ProductCatalog.props(new SearchService()),
    "productcatalog"
  )
  override protected def routes: Route = {
    path("search") {
      post {
        entity(as[HelloWorldAkkaHttpServer.Query]) { query =>
          implicit val timeout = Timeout(6 seconds)
          print("Asking for search\n")
          val future = productCatalog ? GetItems(query.brand, query.productKeyWords)
          val result = Await.result(future, timeout.duration).asInstanceOf[Items]
          complete {
            Future.successful(Response(result.items))
          }
        }
      }
    }
  }
  //  example request:
  //    {"brand":"Starbucks", "productKeyWords" : ["Frappuccino", "Coffee" ,"Drink"]}
  //  POST http://localhost:9000/search

}
