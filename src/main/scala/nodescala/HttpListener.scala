package nodescala

import java.util.concurrent.{Executor, ThreadPoolExecutor, TimeUnit, LinkedBlockingQueue}
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
class HttpListener private (val port: Int, val relativePath: String) {

  private val server = HttpServer.create(new InetSocketAddress(port), 0)
  private val executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue)
  server.setExecutor(executor)
    
  // TO IMPLEMENT
  /** Given a relative path:
   *  1) constructs an uncompleted promise
   *  2) installs an asynchronous `HttpHandler` to the `server`
   *     that completes the promise with a request when `handle` method is invoked,
   *     and then deregisters itself
   *  3) returns the future with the request
   *
   *  Note: the `handle` method of the `HttpHandler` is never called concurrently,
   *        and is always called from the same thread.
   *
   *  @param relativePath    the relative path on which we want to listen to requests
   *  @return                the promise holding the pair of a request and an exchange object
   */
  def nextRequest(): Future[(Request, Exchange)] = {
    val p = Promise[(Request, Exchange)]()

    server.createContext(relativePath, new HttpHandler {
      def handle(httpxchg: HttpExchange): Unit = {
        val req = httpxchg.request
        val xchg = Exchange(httpxchg)
        server.removeContext(relativePath)
        p.success((req, xchg))
      }
    })

    p.future
  }

  // GIVEN TO STUDENTS AS IS
  /** Starts this http listener, and returns the `Subscription` object.
   *
   *  @return         a subscription that should be used to turn off the listener
   */
  def start(): Subscription = {
    server.start()
    new Subscription {
      def unsubscribe(): Unit = {
        server.stop(0)
        executor.shutdown()
      }
    }
  }

}

object HttpListener {

  // GIVEN TO STUDENTS AS IS
  /** Creates a new HTTP listener on the given port.
   */
  def apply(port: Int, relativePath: String): HttpListener = {
    new HttpListener(port, relativePath)
  }

}
