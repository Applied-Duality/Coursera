package nodescala

import NodeScala._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}

object Main {

  def mainOne(args: Array[String]) {
    val subscription = Server(8191, "/test") { req =>
      Iterator.from(1).map(_ + ", ")
    }

    readLine("hit any key to cancel")
    subscription.unsubscribe()
    println("bye")
  }

  def main(args: Array[String]) {
    val listener = HttpListener(8191)
    val s = listener.start()
    val cancel = CancellationTokenSource()
    val token = cancel.cancellationToken

    async {
      while (!token.isCancelled) {
        val s = await { listener.getContext("/test") }
        async {
          val request = s.request
          val response = for (i <- (1 to 1000).iterator) yield (i + ", ").toString
          s.response_=(200, response)
        }
      }

      ()
    }

    readLine("hit any key to cancel")
    cancel.unsubscribe()
    s.unsubscribe()

    println("bye")
  }

}
