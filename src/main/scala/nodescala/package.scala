
import scala.util._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}

package object nodescala {

  /** Adds extensions methods to the `Future` companion object.
   */
  implicit class FutureCompanionOps[T](val f: Future.type) extends AnyVal {

    /** Returns a future that is always completed with `value`.
     */
    def apply[T](value: T) = {
      val p = Promise[T]()
      p.success(value)
      p.future
    }

    /** Returns a future that is never completed.
     */
    def never[T]: Future[T] = Promise[T]().future

    /** Given a list of futures `fs`, returns the future holding the list of values of all the futures from `fs`, in that order.
     */
    def all[T](fs: List[Future[T]]): Future[List[T]] = ???

    /** Given a list of futures `fs`, returns the future holding the value of the future from `fs` that completed first.
     */
    def any[T](fs: List[Future[T]]): Future[T] = ???

    /** Returns a future with a unit value that is completed after time `t`.
     */
    def delay(t: Duration): Future[Unit] = Future {
      try {
        Await.result(Future.never, t)
      } catch {
        case _: Exception => Future { () }
      }
    }

    /** Creates a cancellable context for an execution and runs it.
     * 
     *  Runs a `postAction` after cancellation.
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

    /** Returns the result of the future `f` if it is completed.
     *  Otherwise, throws a `NoSuchElementException`.
     *  
     *  Note: This method does not wait for the result.
     *  It is non-blocking and non-deterministic.
     */
    def result: T = {
      try {
        Await.result(f, 0 nanos)
      } catch {
        case t: TimeoutException => throw new NoSuchElementException
      }
    }

    /** Continues the computation of this future by taking the current future
     *  and mapping it into another future.
     * 
     *  The function `f` is called only after the current future completes.
     *  The resulting future contains a value returned by `f`.
     */
    def continueWith[S](f: Future[T] => S): Future[S] = ???

    /** Continues the computation of this future by taking the result
     *  of the current future and mapping it into another future.
     *  
     *  The function `f` is called only after the current future completes.
     *  The resulting future contains a value returned by `f`.
     */
    def continue[S](f: Try[T] => S): Future[S] = ???

  }

  trait Subscription {
    def unsubscribe(): Unit
  }

  object Subscription {
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

  /** A subscription providing cancellation tokens.
   */
  trait CancellationTokenSource extends Subscription {
    def cancellationToken: CancellationToken
  }

  /** Creates cancellation token sources.
   */
  object CancellationTokenSource {
    // this one executes work after cancelling
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








