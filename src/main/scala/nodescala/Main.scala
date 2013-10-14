package nodescala



import scala.util._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}



object Main {

  def main(args: Array[String]) {
    val subscription = Server(8191, "/test") { req =>
      Iterator.from(1).map(_ + ", ")
    }

    Future.delay(8 second) andThen {
      case _ => subscription.unsubscribe()
    }

    Thread.sleep(10000)
  }

}
