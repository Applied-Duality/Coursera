package nodescala

import com.sun.net.httpserver._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}
import scala.collection._
import scala.collection.JavaConversions._

/** Contains utilities common to the NodeScala© framework.
 */
object NodeScala {

  /** A request is a multimap of headers, where each header is a key-value pair of strings.
   */
  type Request = Map[String, List[String]]

  /** A response consists of a potentially long string (e.g. a data file).
   *  To be able to process this string in parts, the response is encoded
   *  as an iterator over a subsequences of the response string.
   */
  type Response = Iterator[String]

  /** Used to write the response to the request.
   */
  trait Exchange {
    /** Writes to the output stream of the exchange.
     */
    def write(s: String): Unit

    /** Communicates that the response has ended and that there
     *  will be no further writes.
     */
    def close(): Unit

    /** Uses the response object to respond to the write the response back.
     *  The response should be written back in parts, and the method should
     *  occasionally check that server was not stopped, otherwise a very long
     *  response may take very long to finish.
     *
     *  @param token        the cancellation token for
     *  @param body         the response to write back
     */
    def respond(token: CancellationToken, body: Response): Unit

  }

  object Exchange {
    def apply(exchange: HttpExchange) = new Exchange {
      val os = exchange.getResponseBody()

      def write(s: String) = os.write(s.getBytes)

      def close() = os.close()

      // TO IMPLEMENT
      def respond(token: CancellationToken, body: Response): Unit = {
        // If the response length parameter is zero,
        // then chunked transfer encoding is used and an arbitrary amount of data may be sent.
        exchange.sendResponseHeaders(200, 0L)
        while (body.hasNext && !token.isCancelled) {
          val dataChunk = body.next()
          os.write(dataChunk.getBytes())
        }
        os.close()
      }
    }
  }

  /** Adds additional functionality to `HttpExchange` (see JavaDoc).
   */
  implicit class HttpExchangeExtensions(val exchange: HttpExchange) extends AnyVal {

    // TO IMPLEMENT
    /** Constructs a `Request` object from this `HttpExchange`.
     *  See JavaDoc for what `HttpExchange` contains.
     */
    def request: Request = {
      val headers = for ((k, vs) <- exchange.getRequestHeaders) yield (k, vs.toList)
      immutable.Map() ++ headers
    }
  }

  // TO IMPLEMENT
  /** A server first creates and starts an http listener.
   *  It then creates a cancellation token and as long as the token is not cancelled
   *  and there is a request from the http listener, asynchronously process that request.
   *
   *  @return          a subscription that can stop the server and all its asynchronous operations *entirely*.
   */
  def server(port: Int, relativePath: String)(handler: Request => Response): Subscription = {
    val listener = HttpListener(port, relativePath)
    val cancelListener = listener.start()
    val cancelServer = Future.run() { token =>
      async {
        var stream = listener.requestStream()
        while (!token.isCancelled) {
          val Stream(head, tail) = await { stream }
          val (request, xchg) = head
          stream = tail
          async {
            xchg.respond(token, handler(request))
          }
        }
  
        cancelListener.unsubscribe()
      }
    }

    Subscription(cancelListener, cancelServer)
  }

}


