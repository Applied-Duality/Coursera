package nodescala


import scala.util.{Try, Success, Failure}
import scala.collection._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.async.Async.{async, await}
import org.scalatest._
import NodeScala._



class ExampleSpec extends FlatSpec {

  "A Future" should "always be created" in {
    val always = Future.always(517)

    assert(Await.result(always, 0 nanos) == 517)
  }

  it should "never be created" in {
    val never = Future.never[Int]

    try {
      Await.result(never, 1 second)
      assert(false)
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
      assert(false)
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
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  it should "run until cancelled" in {
    val done = Promise[Boolean]()

    val subscription = Future.run() { token =>
      async {
        var i = 0
        while (token.nonCancelled) {
          // do some work
          i += 1
        }

        done.success(true)
      }
    }

    subscription.unsubscribe()

    assert(Await.result(done.future, 1 second) == true)
  }

  it should "return the result when completed" in {
    val completed = Future.always(11)

    assert(completed.now == 11)
  }

  it should "throw a NoSuchElementException when not completed" in {
    val notcompleted = Future.never[Int]

    try {
      notcompleted.now
      assert(false)
    } catch {
      case e: NoSuchElementException => // ok
    }
  }

  it should "be continued" in {
    val p = Promise[Int]()

    val continued = p.future continueWith {
      f => f.now * 2
    }

    p.success(11)
    assert(Await.result(continued, 1 second) == 22)
  }

  it should "be continued using its value" in {
    val p = Promise[Int]()

    val continued = p.future continue {
      t => t.get * 2
    }

    p.success(101)
    assert(Await.result(continued, 1 second) == 202)
  }

  "CancellationTokenSource" should "allow stopping the computation" in {
    val cts = CancellationTokenSource()
    val ct = cts.cancellationToken
    val p = Promise[String]()

    async {
      while (ct.nonCancelled) {
        // do work
      }

      p.success("done")
    }

    cts.unsubscribe()
    assert(Await.result(p.future, 1 second) == "done")
  }

  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()
    def write(s: String) {
      response += s
    }
    def close() {
      loaded.success(response)
    }
  }

  class DummyServerImplementation extends HttpListener.ServerImplementation {
    private var started = false
    private val handlers = mutable.Map[String, Exchange => Unit]()

    def start() = this.synchronized {
      started = true
    }

    def stop() = this.synchronized {
      started = false
    }

    def createContext(relativePath: String, handler: Exchange => Unit) = this.synchronized {
      assert(started)

      handlers(relativePath) = handler
    }

    def removeContext(relativePath: String) = this.synchronized {
      assert(started)

      handlers.remove(relativePath)
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val exchange = new DummyExchange(req)
      val h = handlers(relativePath)
      h(exchange)
      exchange
    }
  }

  "HttpListener" should "serve the next request as a future" in {
    val dummyImpl = new DummyServerImplementation
    val listener = HttpListener(8191, "/test", p => dummyImpl)
    val subscription = listener.start()

    def test(req: Request) {
      val f = listener.nextRequest()
      dummyImpl.emit("/test", req)
      val (reqReturned, xchg) = Await.result(f, 1 second)

      assert(reqReturned == req)
    }

    test(immutable.Map("StrangeHeader" -> List("StrangeValue1")))
    test(immutable.Map("StrangeHeader" -> List("StrangeValue2")))

    subscription.unsubscribe()
  }

  it should "not work if not previously started" in {
    val listener = HttpListener(8191, "/test", p => new DummyServerImplementation)

    try {
      listener.nextRequest()
      assert(false)
    } catch {
      case e: Exception => // ok
    }
  }

  "Server" should "serve requests" in {
    val dummyImpl = new DummyServerImplementation
    val myServer = server(8191, "/testDir", (port, relPath) => HttpListener(port, relPath, p => dummyImpl)) {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummyImpl.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    myServer.unsubscribe()
  }

  it should "cancel a long-running response" in {
    val dummyImpl = new DummyServerImplementation
    val myServer = server(8191, "/testDir", (port, relPath) => HttpListener(port, relPath, p => dummyImpl)) {
      request => Iterator.from(1).map(_.toString)
    }

    // wait until the server is installed
    Thread.sleep(500)

    val webpage = dummyImpl.emit("/testDir", immutable.Map())

    // let some work be done
    Thread.sleep(200)

    myServer.unsubscribe()

    // give him some time to unsubscribe
    Thread.sleep(500)

    webpage.loaded.future.now
  }

}




