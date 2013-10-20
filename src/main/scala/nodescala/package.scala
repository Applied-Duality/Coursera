
import scala.util._
import scala.util.control.NonFatal
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}

/** Contains basic data types, data structures and `Future` extensions.
 */
package object nodescala {

  /** Adds extensions methods to the `Future` companion object.
   */
  implicit class FutureCompanionOps[T](val f: Future.type) extends AnyVal {

    // TO IMPLEMENT
    /** Returns a future that is always completed with `value`.
     */
    def always[T](value: T) = {
      val p = Promise[T]()
      p.success(value)
      p.future
    }

    // TO IMPLEMENT
    /** Returns a future that is never completed.
     */
    def never[T]: Future[T] = Promise[T]().future

    // TO IMPLEMENT
    /** Given a list of futures `fs`, returns the future holding the list of values of all the futures from `fs`.
     *  The values in the list are in the same order as corresponding futures `fs`.
     *  If any of the futures `fs` fails, the resulting future also fails.
     */
    def all[T](fs: List[Future[T]]): Future[List[T]] = {
      fs.foldRight(Future.always(List[T]())) { (f, acc) =>
        for (tail <- acc; head <- f) yield head :: tail
      }
    }

    // TO IMPLEMENT
    /** Given a list of futures `fs`, returns the future holding the value of the future from `fs` that completed first.
     *  If the first completing future in `fs` fails, then the result is failed as well.
     *
     *  E.g.:
     *
     *      Future.any(List(Future { 1 }, Future { 2 }, Future { throw new Exception }))
     *
     *  may return a `Future` succeeded with `1`, `2` or failed with an `Exception`.
     */
    def any[T](fs: List[Future[T]]): Future[T] = {
      val p = Promise[T]()
      for (f <- fs) f onComplete {
        case t => p.tryComplete(t)
      }
      p.future
    }

    // TO IMPLEMENT
    /** Returns a future with a unit value that is completed after time `t`.
     */
    def delay(t: Duration): Future[Unit] = Future {
      try {
        blocking {
          Await.result(Future.never, t)
        }
      } catch {
        case _: Exception =>
      }
    }

    // GIVEN TO STUDENTS AS IS
    /** Completes this future with user input.
     */
    def userInput(message: String): Future[String] = Future {
      readLine(message)
    }

    // TO IMPLEMENT
    /** Creates a cancellable context for an execution and runs it.
     */
    def run()(f: CancellationToken => Future[Unit]): Subscription = {
      val cts = CancellationTokenSource()
      f(cts.cancellationToken)
      cts
    }

  }

  /** Adds extension methods to future objects.
   */
  implicit class FutureOps[T](val f: Future[T]) extends AnyVal {

    // TO IMPLEMENT
    /** Returns the result of the future `f` if it is completed now.
     *  Otherwise, throws a `NoSuchElementException`.
     *  
     *  Note: This method does not wait for the result.
     *  It is thus non-blocking.
     *  However, it is also non-deterministic -- it may throw or return a value
     *  depending on the current state of the `Future`.
     */
    def now: T = {
      try {
        Await.result(f, 0 nanos)
      } catch {
        case t: TimeoutException => throw new NoSuchElementException
      }
    }

    // TO IMPLEMENT
    /** Continues the computation of this future by taking the current future
     *  and mapping it into another future.
     * 
     *  The function `f` is called only after the current future completes.
     *  The resulting future contains a value returned by `f`.
     */
    def continueWith[S](cont: Future[T] => S): Future[S] = {
      val p = Promise[S]()

      f onComplete {
        case _ =>
          try {
            p.success(cont(f))
          } catch {
            case NonFatal(t) => p.failure(t)
          }
      }

      p.future
    }

    // TO IMPLEMENT
    /** Continues the computation of this future by taking the result
     *  of the current future and mapping it into another future.
     *  
     *  The function `f` is called only after the current future completes.
     *  The resulting future contains a value returned by `f`.
     */
    def continue[S](cont: Try[T] => S): Future[S] = {
      val p = Promise[S]()

      f onComplete {
        case t =>
          try {
            p.success(cont(t))
          } catch {
            case NonFatal(t) => p.failure(t)
          }
      }

      p.future
    }

  }

  /** Subscription objects are used to be able to unsubscribe
   *  from some event source.
   */
  trait Subscription {
    def unsubscribe(): Unit
  }

  object Subscription {
    // GIVEN TO STUDENTS AS IS
    /** Given two subscriptions `s1` and `s2` returns a new composite subscription
     *  such that when the new composite subscription cancels both `s1` and `s2`
     *  when `unsubscribe` is called.
     */
    def apply(s1: Subscription, s2: Subscription) = new Subscription {
      def unsubscribe() {
        s1.unsubscribe()
        s2.unsubscribe()
      }
    }
  }

  /** Used to check if cancellation was requested.
   */
  trait CancellationToken {
    def isCancelled: Boolean
    def nonCancelled = !isCancelled
  }

  /** The `CancellationTokenSource` is a special kind of `Subscription` that
   *  returns a `cancellationToken` which is cancelled by calling `unsubscribe`.
   *  
   *  After calling `unsubscribe` once, the associated `cancellationToken` will
   *  forever remain cancelled -- its `isCancelled` will return `false.
   */
  trait CancellationTokenSource extends Subscription {
    def cancellationToken: CancellationToken
  }

  /** Creates cancellation token sources.
   */
  object CancellationTokenSource {
    // TO IMPLEMENT
    /** Creates a new `CancellationTokenSource`.
     */
    def apply() = new CancellationTokenSource {
      val p = Promise[Unit]()
      val cancellationToken = new CancellationToken {
        def isCancelled = p.future.value != None
      }
      def unsubscribe() {
        p.trySuccess(())
      }
    }
  }

}








