package nodescala


import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.async.Async.{async, await}
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

  it should "be completed with all the results" in {
    val results = List(1, 2, 3, 4, 5)
    val futures = results map { r => Future { r } }

    val all = Future.all(futures)

    assert(Await.result(all, 1 second) == results)
  }

  it should "be completed with any of the results" in {
    val results = List(1, 2, 3, 4, 5)
    val futures = results map { r => Future { r } }

    val any = Future.any(futures)

    assert(results.toSet contains Await.result(any, 1 second))
  }

  it should "fail with one of the exceptions in" in {
    val futures = (0 until 10).toList map { r => Future { throw new IllegalStateException } }

    val any = Future.any(futures)

    try {
      Await.result(any, 1 second)
    } catch {
      case e: IllegalStateException => // ok
    }
  }

  it should "complete after 3s" in {
    val delayed = Future.delay(1 seconds) map { _ => "done!"}

    assert(Await.result(delayed, 3 seconds) == "done!")
  }


  it should "not complete after 1s" in {
    val delayed = Future.delay(3 seconds)

    try {
      Await.result(delayed, 1 second)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  it should "run until cancelled" in {
    val done = Promise[Boolean]()

    val subscription = Future.run() { token =>
      async {
        while (token.nonCancelled) {
          // do some work
        }

        done.success(true)
      }
    }

    subscription.unsubscribe()

    assert(Await.result(done.future, 1 second) == true)
  }

}