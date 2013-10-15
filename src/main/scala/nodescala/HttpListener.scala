package nodescala

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.InetSocketAddress
import scala.concurrent._

class HttpListener(private val server: HttpServer) {

  def getContext(url: String): Future[HttpExchange] = {
    val p = Promise[HttpExchange]
    server.createContext(url, new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
         server.removeContext(url)
         p.success(exchange)
      }
    })
    p.future
  }

  def start(): Subscription = {
    server.setExecutor(null)
    server.start()
    new Subscription {
      def unsubscribe(): Unit = {
        server.stop(0)
      }
    }
  }

}

object HttpListener {
  def apply(port: Int): HttpListener = {
    val server = HttpServer.create(new InetSocketAddress(port), 0)
    new HttpListener(server)
  }
}
