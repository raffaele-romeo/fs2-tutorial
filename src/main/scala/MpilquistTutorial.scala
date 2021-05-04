import scala.concurrent.duration._
import fs2.{Pipe, Pull, Stream}
import cats.effect.{ContextShift, IO, Timer}
import cats.effect._
import cats.implicits._
import fs2.concurrent._

object MpilquistTutorial extends App {

  val executionContext              = scala.concurrent.ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val timer: Timer[IO] = IO.timer(executionContext)


  val ex1 = Stream(1,2,3).map(_ + 1)
  val ex2 = Stream(1,2,3).flatMap(n => Stream.emits(List.fill(n)(n)) )
  val ex3 = ex1 interleave ex2
  val ex4 = ex1 zip ex2
  val ex5 = ex1 zip ex2.repeat
  val ex6 = IO.delay{println("compute value"); System.currentTimeMillis()}
  val ex7: Stream[IO, Long] = Stream.eval(ex6)
  val ex8 = Stream.repeatEval(ex6)

  def myLines[F[_]]: Pipe[F, String, String] = in => in.flatMap( s => Stream.emits(s.lines.toList))
  val ex9 = Stream("hello\nworld").through(myLines)

  val ex10 = Stream(1,2,3,4).repeat.take(10).chunks.toList

  def myTake[F[_], O](n: Int): Pipe[F, O, O] = {
    def go(s: Stream[F,O], n: Long): Pull[F,O,Unit] = {
      s.pull.uncons.flatMap {
        case Some((hd,tl)) =>
          hd.size match {
            case m if m <= n => Pull.output(hd) >> go(tl, n - m)
            case m => Pull.output(hd.take(n.toInt)) >> Pull.done
          }
        case None => Pull.done
      }
    }
    in => go(in,n).stream
  }
  val ex11 = Stream(1,2,3,4).through(myTake(2)).toList
  val ex12 = Stream(1,2,3,4).repeat.covary[IO]
  val ex13 = Stream.awakeEvery[IO](1.seconds).evalMap{i => IO.delay((println("Time: " + i)))}

  def log[A](prefix: String): Pipe[IO, A, A] = _.evalMap {a => IO.delay {println(s"$prefix> $a"); a}}

  def randomDelays[A](max: FiniteDuration): Pipe[IO, A, A] = _.evalMap{ a =>
    val delay = IO.delay(scala.util.Random.nextInt(max.toMillis.toInt))
    delay.flatMap {d => IO.sleep(d.millis) *> IO.pure(a)}
  }

  val ex14 = Stream.range(1, 10).through(randomDelays(1.seconds)).through(log("A"))
  val ex15 = Stream.range(1, 10).through(randomDelays(1.seconds)).through(log("B"))
  val ex16 = (ex14 interleave ex15).through(log("interleaved"))
  val ex17 = (ex14 merge ex15).through(log("merge"))
  val ex18 = (ex14 either ex15).through(log("either"))
  val ex19 = Stream.range(1, 10).through(randomDelays(1.seconds)).through(log("C"))
  val ex20 = Stream(ex14, ex15, ex19)
  val ex21 = ex20.parJoin(2)

  class Buffering[F[_]](q1: Queue[F, Int], q2: Queue[F, Int])(implicit F: Concurrent[F]) {
    def start: Stream[F, Unit] =
      Stream(
        Stream.range(0, 1000).covary[F].through(q1.enqueue),
        q1.dequeue.through(q2.enqueue),
        //.map won't work here as you're trying to map a pure value with a side effect. Use `evalMap` instead.
        q2.dequeue.evalMap(n => F.delay(println(s"Pulling out $n from Queue #2")))
      ).parJoin(3)
  }

  val ex22 = for {
    q1 <- Stream.eval(Queue.bounded[IO, Int](1))
    q2 <- Stream.eval(Queue.bounded[IO, Int](100))
    bp = new Buffering[IO](q1, q2)
    _  <- Stream.sleep_[IO](5.seconds) concurrently bp.start.drain
  } yield ()


  println(ex9.toList)
  println(ex6.unsafeRunSync())
  println(ex7.compile.toVector.unsafeRunSync)
  println(ex21.compile.drain.unsafeRunSync())

  println(ex22.compile.drain.unsafeRunSync())
}
