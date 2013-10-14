package nodescala



import java.net.InetSocketAddress
import com.sun.net.httpserver._
import scala.collection._
import scala.collection.JavaConversions._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}



object Server {

  type Req = Map[String, List[String]]

  type Work = (Req, HttpExchange)

  type Resp = Iterator[String]

  case class Stream[T](head: T, tail: Future[Stream[T]])

  def log(msg: String) = println(msg)

  def apply(port: Int, url: String)(handler: Req => Resp): Subscription = Future.run { ct =>
    var stream = Promise[Stream[Work]]()

    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.setExecutor(null)

    // accepter:
    // create a guy that will prepare a unit of work every time a request comes in
    // put all units of work in a dataflow stream
    server.createContext(url, new HttpHandler {
      def handle(x: HttpExchange) = if (ct.nonCancelled) this.synchronized {
        log("request received")

        val headers = for ((k, vs) <- x.getRequestHeaders) yield (k, vs.toList)
        val req = immutable.Map() ++ headers
        val work = (req, x)
        val tail = Promise[Stream[Work]]()
        stream.success(Stream[Work](work, tail.future))
        stream = tail
      }
    })

    // dispatcher: traverse the stream and for each work unit start an async response
    def traverseRequestStream(reqStreamInitial: Future[Stream[Work]]) {
      var tail = reqStreamInitial
      async {
        while (ct.nonCancelled) {
          val stream = await { tail }

          log("scheduling response")

          val work = stream.head
          async { respond(work) }
          tail = stream.tail
        }

        log("stopping server")
        server.stop(0)
      }
    }

    // responder: give a response back, but check if the server was cancelled periodically
    def respond(work: Work) {
      log("answering request")

      val (req, x) = work
      val resp = handler(req)
      val os = x.getResponseBody()
      x.sendResponseHeaders(200, 0L)
      while (ct.nonCancelled && resp.hasNext) {
        val dataChunk = resp.next()
        os.write(dataChunk.getBytes())
      }
      os.close()
    }

    async { traverseRequestStream(stream.future) }
    async { server.start() }
  }

}

