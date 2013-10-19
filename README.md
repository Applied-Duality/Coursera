Exercise 3: NodeScala 
=====================

In this exercise you will implement a simple NodeJS-style asynchronous server using Scala `Future`s and the `async`/`await`.


# Part 1: Extending Futures

In the first part of the exercise you will extend the Futures and Promises API with some additional methods.


## Extension Methods on `Future`s

In Scala you can add missing methods to existing classes and singleton objects.
Lets say you want to have a new `Future` factory method `userInput` in the `Future` companion object that expects user input and completes the future with the user input once the `ENTER` key was pressed.
The `Future` companion object is already baked into the standard library, so you cannot add method there directly.
Here is an example how you can add `userInput` using extension methods:

    implicit class FutureCompanionOps(f: Future.type) extends AnyVal {
      def userInput(message: String): Future[String] = Future {
        readLine(message)
      }
    }

The `implicit` modifier on the `class` declaration above means that the compiler will generate
an implicit conversion from the `Future` companion object to the `FutureCompanionOps` object.
The declaration above is desugared into:

    class FutureCompanionOps(f: Future.type) extends AnyVal {
      def userInput(message: String): Future[String] = Future {
        readLine(message)
      }
    }
    implicit def f2ops(f: Future.type) = new FutureCompanionOps(f)

This implicit conversion will be called every time you call a non-existing method on the `Future`
companion object -- `Future.userInput` thus automatically becomes `f2ops(Future).userInput`.
The `extends AnyVal` part is just an optimization telling the compiler to avoid instantiating the
`FutureCompanionOps` object where possible and call its methods directly.

The bottomline is that whenever you want to add missing methods to an already existing class implementation,
you should use these pattern.

Now you can add the following methods to the `Future` companion object:

    /** Returns a future that is always completed with `value`.
     */
    def always[T](value: T): Future[T]

    /** Returns a future that is never completed.
     */
    def never[T]: Future[T]

    /** Given a list of futures `fs`, returns the future holding the value of the future from `fs` that completed first.
     *  If the first completing future in `fs` fails, then the result is failed as well.
     *
     *  E.g.:
     *
     *      Future.any(List(Future { 1 }, Future { 2 }, Future { throw new Exception }))
     *
     *  may return a `Future` succeeded with `1`, `2` or failed with an `Exception`.
     */
    def any[T](fs: List[Future[T]]): Future[T]

    /** Given a list of futures `fs`, returns the future holding the list of values of all the futures from `fs`.
     *  The values in the list are in the same order as corresponding futures `fs`.
     *  If any of the futures `fs` fails, the resulting future also fails.
     */
    def all[T](fs: List[Future[T]]): Future[List[T]]

    /** Returns a future with a unit value that is completed after time `t`.
     */
    def delay(t: Duration): Future[Unit]

Hint: use whatever tool you see most appropriate for the job when implementing these
factory methods -- existing future combinators, `for`-comprehensions, `Promise`s or `async`/`await`.
You may only use `Await.ready` and `Await.result` when defining the `delay` factory method.

In the same way, add the following methods to `Future` objects:

    /** Returns the result of the future `f` if it is completed now.
     *  Otherwise, throws a `NoSuchElementException`.
     *  
     *  Note: This method does not wait for the result.
     *  It is thus non-blocking.
     *  However, it is also non-deterministic -- it may throw or return a value
     *  depending on the state of the `Future`.
     */
    def now: T

    /** Continues the computation of this future by taking the current future
     *  and mapping it into another future.
     * 
     *  The function `f` is called only after the current future completes.
     *  The resulting future contains a value returned by `f`.
     */
    def continueWith[S](cont: Future[T] => S): Future[S]

    /** Continues the computation of this future by taking the result
     *  of the current future and mapping it into another future.
     *  
     *  The function `f` is called only after the current future completes.
     *  The resulting future contains a value returned by `f`.
     */
    def continue[S](cont: Try[T] => S): Future[S]



## Adding Cancellation


TODO

    /** Creates a cancellable context for an execution and runs it.
     */
    def run()(f: CancellationToken => Future[Unit]): Subscription = {
      val cts = CancellationTokenSource()
      f(cts.cancellationToken)
      cts
    }


# Part 2: A Dataflow Stream




# Part 3: An Asynchronous NodeJS-style HTTP Server


## The HTTP Listener


## The HTTP Server


## Instantiating the Server


