package nodescala

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.InetSocketAddress
import scala.concurrent._
import ExecutionContext.Implicits.global
import nodescala.NodeScala._

/** Used to obtain http requests on a certain port and a certain relative path.
 *
 *  Use case:
 *
 *      val listener = HttpListener(8080, "/test")
 *      val futureRequest = listener.nextRequest()
 *
 */
class HttpListener private (private val server: HttpServer, val relativePath: String) {

  // TO IMPLEMENT
  /** Given a relative path, this method returns a future containing
   *  the next request to this http listener for that path.
   * 
   *  It does so by installing an `HttpHandler` object on the given
   *  relative path, that completes the returned future.
   *
   *  @param relativePath    the relative path on which we want to listen to requests
   *  @return                the future holding the pair of a request and a `HttpExchange` used to respond
   */
  def nextRequest(): Future[(Request, HttpExchange)] = {
    val p = Promise[HttpExchange]
    server.createContext(relativePath, new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        server.removeContext(relativePath)
        p.success(exchange)
      }
    })

    for (xchg <- p.future) yield (xchg.request, xchg)
  }

  // GIVEN TO STUDENTS AS IS
  /** Starts this http listener, and returns the `Subscription` object.
   *
   *  @return         a subscription that should be used to turn off the listener
   */
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

  // GIVEN TO STUDENTS AS IS
  /** Creates a new HTTP listener on the given port.
   */
  def apply(port: Int, relativePath: String): HttpListener = {
    val server = HttpServer.create(new InetSocketAddress(port), 0)
    new HttpListener(server, relativePath)
  }

}
