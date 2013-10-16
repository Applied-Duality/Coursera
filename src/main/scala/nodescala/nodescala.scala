package nodescala

import com.sun.net.httpserver._
import scala.collection._
import scala.collection.JavaConversions._

object NodeScala {

  object Request {
    def apply(exchange: HttpExchange): Request = {
      val headers = for ((k, vs) <- exchange.getRequestHeaders) yield (k, vs.toList)
      immutable.Map() ++ headers
    }
  }

  implicit class HttpExchangeExtensions(val exchange: HttpExchange) extends AnyVal {

    def request: Request = Request(exchange)

    def response_= (statusCode: Int, body: Response) = {
      val os = exchange.getResponseBody()
      // If the response length parameter is zero,
      // then chunked transfer encoding is used and an arbitrary amount of data may be sent.
      exchange.sendResponseHeaders(statusCode, 0L)
      while (body.hasNext) {
        val dataChunk = body.next()
        os.write(dataChunk.getBytes())
      }
      os.close()
    }
  }

  type Request = Map[String, List[String]]

  type Response = Iterator[String]

}


