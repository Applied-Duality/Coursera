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
     *
     *  E.g.:
     *
     *      Future.all(List(Future { 1 }, Future { 2 }, Future { 3 }))
     *
     *  returns a single `Future` containing the `List(1, 2, 3)`.
     */
    def all[T](fs: List[Future[T]]): Future[List[T]]

    /** Returns a future with a unit value that is completed after time `t`.
     */
    def delay(t: Duration): Future[Unit]

In the same way, add the following methods to `Future` objects:

    /** Returns the result of the future `f` if it is completed now.
     *  Otherwise, throws a `NoSuchElementException`.
     *  
     *  Note: This method does not wait for the result.
     *  It is thus non-blocking.
     *  However, it is also non-deterministic -- it may throw or return a value
     *  depending on the current state of the `Future`.
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

Hint: use whatever tool you see most appropriate for the job when implementing these
factory methods -- existing future combinators, `for`-comprehensions, `Promise`s or `async`/`await`.
You may use `Await.ready` and `Await.result` only when defining the `delay` factory method
and the `now` method on `Future`s.

We will use the factory methods and combinators defined above later in the exercise.


## Adding Cancellation

Standard Scala `Future`s cannot be cancelled.
Instead, cancelling an asynchronous computation requires a collaborative effort,
in which the computation that is supposed to be cancelled periodically checks a condition
for cancellation.

In this part of the exercise we will develop support for easier cancellation.
We introduce the following traits:

    trait CancellationToken {
      def isCancelled: Boolean
    }

The `CancellationToken` is an object used by long running asynchronous computation to
periodically check if the should cancel what they are doing.
If `isCancelled` returns `true`, then an asynchronous computation should stop.

    trait Subscription {
      def unsubscribe(): Unit
    }

`Subscription`s are used to unsubscribe from an event.
Calling `unsubscribe` means that the `Subscription` owner is no longer
interested in the asynchronous computation, and that it can stop.

    trait CancellationTokenSource extends Subscription {
      def cancellationToken: CancellationToken
    }

The `CancellationTokenSource` is a special kind of `Subscription` that
returns a `cancellationToken` which is cancelled by calling `unsubscribe`.
After calling `unsubscribe` once, the associated `cancellationToken` will
forever remain cancelled.

Your first task is to implement the default `CancellationTokenSource`:

    object CancellationTokenSource {
      def apply(): CancellationTokenSource = ???
    }

Hint: use a `Promise` in the above implementation.

We use the above-defined method to implement a method `run` on the `Future` companion object
that starts an asynchronous computation `f` taking a `CancellationToken` and returns a
subscription that cancels that `CancellationToken`:

    def run()(f: CancellationToken => Future[Unit]): Subscription = {
      val cts = CancellationTokenSource()
      f(cts.cancellationToken)
      cts
    }

Clients can use `Future.run` as follows:

    val working = Future.run() { ct =>
      async {
        while (ct.nonCancelled) {
          println("working")
        }
        println("done")
      }
    }
    Future.delay(5 seconds)
    working.unsubscribe()


# Part 2: A Dataflow Stream

A dataflow stream is a `Future`-based data-structure used
as a building block for producer-consumer patterns.
It consists of a single recursive data type called `Stream`:

    case class Stream[T](head: T, tail: Future[Stream[T]])

On the producer side a stream always has the type `Promise[Stream[T]]`,
so that the producer can add values into it.
The consumer always sees the stream as a `Future[Stream[T]]`,
and can only read values from it.
A new dataflow stream is normally created by the producer with the `Stream.sink` method.
This method merely creates an empty promise of type `Promise[Stream[T]]`.

Here is an example of a producer-consumer pattern using dataflow streams,
in which the producer produces a stream of natural numbers:

    val stream = Stream.sink[Int]

    // producer
    async {
      var producerTail: Promise[Stream[Int]] = stream
      var i = 0
      while (true) {
        val head = await { async { Thread.sleep(1000); i += 1; i } }
        val tail = Stream.sink[Int]
        producerTail.success(Stream(head, tail.future))
        producerTail = tail
      }
    }

    // consumer
    async {
      var consumerTail: Future[Stream[Int]] = stream.future
      while (true) {
        val Stream(head, tail) = await { consumerTail }
        println(head)
        consumerTail = tail
      }
    }

This is, however, boilerplatey -- we want to write the same example like this:

    // producer
    async {
      var producerTail: Promise[Stream[Int]] = stream
      var i = 0
      while (true) {
        val head = await { async { Thread.sleep(1000); i += 1; i } }
        producerTail = producerTail << head
      }
    }

    // consumer
    async {
      var consumerTail: Future[Stream[Int]] = stream.future
      while (true) {
        val Stream(head, tail) = await { consumerTail }
        println(head)
        consumerTail = tail
      }
    }

Implement the extension method `<<` on type `Promise[Stream[T]]` that adds
the element to the uncompleted stream, and returns the uncompleted tail of the
newly completed stream:

    /** Given an uncompleted stream:
     *  1) constructs the tail of the current stream, which is another uncompleted stream
     *  2) writes an element `elem` to the head of this stream
     *  3) returns the uncompleted tail of this stream
     *  
     *  A stream:
     *
     *      -> ?
     *
     *  thus becomes:
     *
     *      -> elem -> ?
     *
     *  where `?` denotes an uncompleted stream.
     *
     *  @param elem       an element to write to the uncompleted stream
     *  @return           the tail of this stream after this stream has been completed
     */
    def <<(elem: T): Promise[Stream[T]]


# Part 3: An Asynchronous NodeJS-style HTTP Server


## The HTTP Listener


## The HTTP Server


## Instantiating the Server


