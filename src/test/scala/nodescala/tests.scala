package nodescala


import scala.concurrent._
import scala.concurrent.duration._
import org.scalatest._



class ExampleSpec extends FlatSpec {

  "A Future" should "always be created" in {
    val always = Future.always(517)

    assert(Await.result(always, 0 nanos) == 517)
  }

  it should "never be created" in {
    val never = Future.never[Int]

    try {
      Await.result(never, 1 second)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

}