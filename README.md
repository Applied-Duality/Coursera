Exercise 3: NodeScala 
=====================

In this exercise you will implement a simple NodeJS-style asynchronous server using Scala `Future`s and the `async`/`await`.


# Part 1: Extending Futures

In the first part of the exercise you will extend the Futures and Promises API with some additional methods.
We will define these methods in the file `package.scala`.


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

We will use the factory methods and combinators defined above later in the exercise.

Hint: use whatever tool you see most appropriate for the job when implementing these
factory methods -- existing future combinators, `for`-comprehensions, `Promise`s or `async`/`await`.
You may use `Await.ready` and `Await.result` only when defining the `delay` factory method
and the `now` method on `Future`s.
All the methods should be non-blocking, while `delay` may asynchronously block its `Future` execution
until the specified time period elapses.

Hint: whenever you have a long-running computation or blocking make sure to run it inside the `blocking` construct.
For example:

    blocking {
      Thread.sleep(1000)
    }

This ensures that the thread pool does not run out of threads and deadlocks the entire application.


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



# Part 2: An Asynchronous NodeJS-style HTTP Server

Finally, you have everything you need to write an asynchronous HTTP Server.
The HTTP server will consist of two components -- the `HttpListener` that translates
incoming requests into `Future`s and the `server` method.


## The HTTP Listener

The `HttpListener` in file `HttpListener.scala` will do the translation from the event-driven `HttpServer` API to a more `Future`-based API.
See [the `HttpServer` docs](http://docs.oracle.com/javase/7/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpServer.html)
for more info on how it works.

The `HttpListener` is created using the companion object factory method like this:

    val listener = HttpListener(8192, "/myTestUrl")

The above statement creates an `HttpListener` that will wait for incoming HTTP requests
on port `8192` of the machine with the relative path "/myTestUrl".
After creating the `HttpListener`, it needs to be started like this:

    val subscription = listener.start()

Starting the `HttpListener` returns a `Subscription` object used to stop the listener later.
To retrieve the next incoming HTTP request, the `HttpListener` exposes the `nextRequest` method:

    def nextRequest(): Future[(Request, Exchange)]

This method will return a `Future` containing a pair of the `Request`, which is an `immutable.Map`
with all the HTTP request headers, and an `Exchange` object which can be used to write
the response back to the HTTP client.

Upon calling the `nextRequest` method, the `HttpListener` creates an empty `Promise` from the result,
installs a HTTP request handler that will complete the promise with the request and then deregister itself,
and returns the `Future` of the result `Promise`.
This pattern in which a callback completes a `Promise` to translate an event into a `Future`
is ubiquitous in reactive programming with `Future`s.

Your task in this part is to implement the `nextRequest` method.

Hint: make sure you understand how the [`createContext` and `removeContext`](http://docs.oracle.com/javase/7/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpServer.html#createContext%28java.lang.String,%20com.sun.net.httpserver.HttpHandler%29)
methods of the `HttpServer` class work.


## The HTTP Server

The HTTP server consists of two main methods `respond` and `server`.
The `respond` method is used to write the `response` back to the client using an `exchange` object.
While doing so, this method must periodically check the `token` to see
if the response should be interrupted early, otherwise our server
might run forever!

    private def respond(exchange: Exchange, token: CancellationToken, response: Response): Unit

The `Response` is a simple type alias:

    type Response = Iterator[String]

We could have simply encoded the response as a `String`, but responses can potentially be huge
and even if a huge `Response` fit in memory, we would not be able to cancel the responses
early if the server were cancelled.

Your first task is to implement the method `respond`.

To start the HTTP server, we declare a single method `server` in file `nodescala.scala`:

    def server(port: Int, relativePath: String)(handler: Request => Response): Subscription

This method starts an asynchronous server on that server HTTP requests on `port` and `relativePath`.
It takes a `handler` argument to generically map requests into responses -- for each `Request`, the server invokes the `handler` to produce an appropriate `Response`.
The method returns a `Subscription` that cancels all asynchronous computations related to this server.

Your task is to implement `server` using `async`/`await` in the following way:

1. create and start an http listener
2. create a cancellation token to run an asynchronous computation (hint: use one of the `Future` companion methods)
3. in this asynchronous computation, while the token is not cancelled, await the next request from the listener and then respond to it asynchronously
4. have the method return a subscription that cancels both the http listener, the server loop
   and any responses that are in progress
   (hint: use one of the `Subscription` companion methods)

## Instantiating the Server

Finally, you can instantiate the server in the file `Main.scala`:

1. Create a server on port `8191` and relative path `/test` with a subscription `myServer`
2. Create a `userInterrupted` future that is completed when the user presses `ENTER`, continued with a message `"You entered... "`
3. Create a `timeOut` future that is completed after 20 seconds, continued with a message `"Server timeout!"`
4. Create a `terminationRequested` future that is completed once any of the two futures above complete
5. Once the `terminationRequested` completes, print its message, unsubscribe from `myServer` and print `"Bye!"`

Hint: where possible, use the previously defined `Future` factory methods and combinators.