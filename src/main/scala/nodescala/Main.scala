package nodescala

import nodescala.NodeScala._

object Main {

  def main(args: Array[String]) {
    // TO IMPLEMENT
    // 1. instantiate the server at 8191, relative path "/test"
    val cancel = server(8191, "/test") { request =>
      for (i <- (1 to 1000).iterator) yield (i + ", ").toString
    }

    // 2. wait until user cancels the server with the "hit ENTER to cancel" message
    readLine("hit ENTER to cancel")

    // TO IMPLEMENT
    // 3. unsubscribe from the server
    cancel.unsubscribe()

    // 4. print a "bye" message
    println("bye")
  }

}
