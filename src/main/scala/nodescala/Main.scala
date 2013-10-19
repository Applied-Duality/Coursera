package nodescala

import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}

object Main {

  def main(args: Array[String]) {
    // TO IMPLEMENT
    // 1. instantiate the server at 8191, relative path "/test",
    //    and have the response return comma separated numbers from 1 to 1000
    val cancel = NodeScala.server(8191, "/test") { request =>
      for (i <- (1 to 1000).iterator) yield (i + ", ").toString
    }

    // TO IMPLEMENT
    // 2. create a future that expects some user input `x`
    //    and continues with a `"You entered... " + x` message
    val userInterrupted = Future.userInput("hit ENTER to cancel... ") continueWith {
      f => "You entered... " + f.now
    }

    // TO IMPLEMENT
    // 3. create a future that completes after 10 seconds
    //    and continues with a `"Server timeout!"` message
    val timeElapsed = Future.delay(10 seconds) continue {
      _ => "Server timeout!"
    }

    // TO IMPLEMENT
    // 4. create a future that completes when either 10 seconds elapse
    //    or the user presses ENTER
    val serverTerminated = Future.any(List(
      userInterrupted,
      timeElapsed
    ))

    // TO IMPLEMENT
    // 5. unsubscribe from the server
    serverTerminated onSuccess {
      case msg =>
        println(msg)
        cancel.unsubscribe()
        println("bye")
    }
  }

}
