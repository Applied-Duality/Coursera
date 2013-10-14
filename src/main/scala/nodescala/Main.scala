package nodescala



import scala.util._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}



object Main {

  def main(args: Array[String]) {
    val subscription = Server(8193, "/test") { req =>
      Iterator.from(1).map(_.toString)
    }

    Future.delay(10 second) andThen {
      case _ => subscription.unsubscribe()
    }
  }

}
