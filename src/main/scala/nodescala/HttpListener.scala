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
 */
trait HttpListener {
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
  def nextRequest(): Future[(Request, Exchange)]

  /** Starts this http listener, and returns the `Subscription` object.
   *
   *  @return         a subscription that should be used to turn off the listener
   */
  def start(): Subscription

}

object HttpListener {

  /** Abstracts over different HTTP server implementations.
   */
  trait ServerImplementation {
    def start(): Unit
    def stop(): Unit
    def createContext(relativePath: String, handler: Exchange => Unit): Unit
    def removeContext(relativePath: String): Unit
  }

  object ServerImplementation {
    /** The default server implementation based on `com.sun.net.httpserver` package.
     */
    def apply(port: Int) = new ServerImplementation {
      private val s = HttpServer.create(new InetSocketAddress(port), 0)
      private val executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue)
      s.setExecutor(executor)

      def createContext(relativePath: String, handler: Exchange => Unit) = s.createContext(relativePath, new HttpHandler {
        def handle(httpxchg: HttpExchange) = handler(Exchange(httpxchg))
      })
      def removeContext(relativePath: String) = s.removeContext(relativePath)
      def start() = s.start()
      def stop() = {
        s.stop(0)
        executor.shutdown()
      }
    }
  }

  // GIVEN TO STUDENTS AS IS
  /** Creates a new HTTP listener on the given port and relative path.
   *
   *  @param port                the port the listener should listen on
   *  @param relativePath        the relative path for the requests
   *  @param serverFactory       the factory method that creates a `ServerImplentation`
   *  @return                    `HttpListener` for the given port and path
   */
  def apply(port: Int, relativePath: String, serverFactory: Int => ServerImplementation = ServerImplementation.apply) = new HttpListener {
    private val serverImpl = serverFactory(port)
      
    // TO IMPLEMENT
    def nextRequest(): Future[(Request, Exchange)] = {
      val p = Promise[(Request, Exchange)]()
  
      serverImpl.createContext(relativePath, xchg => {
        val req = xchg.request
        serverImpl.removeContext(relativePath)
        p.success((req, xchg))
      })
  
      p.future
    }
  
    // GIVEN TO STUDENTS AS IS
    def start(): Subscription = {
      serverImpl.start()
      new Subscription {
        def unsubscribe(): Unit = {
          serverImpl.stop()
        }
      }
    }
  
  }

}
