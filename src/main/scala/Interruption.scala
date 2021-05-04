
import java.time.LocalDateTime

import cats.effect._
import cats.effect.concurrent.Deferred
import fs2.Stream
import fs2.concurrent.SignallingRef

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

object Interruption {
  def interruption[F[_] : Timer : Concurrent : Sync]: Stream[F, Any] = {
    Stream
      .emit[F, String]("ping")
      .repeat
      .metered(1.second)
      .evalTap(x => Sync[F].delay(println(s"${LocalDateTime.now()}: $x")))
      .interruptAfter(3.seconds)
      .onComplete(Stream.eval(Sync[F].delay(println("pong"))))
  }

  def interruption2[F[_] : Timer : Concurrent : Sync]: Stream[F, Any] = {
    Stream
      .eval(Deferred[F, Either[Throwable, Unit]])
      .flatMap { promise =>
        Stream
          .eval(Sync[F].delay(Random.nextInt(5)))
          .repeat
          .metered(1.second)
          .evalTap(x => Sync[F].delay(println(s"${LocalDateTime.now()}: $x")))
          .evalTap {
            case 0 => promise.complete(Right(()))
            case _ => Sync[F].delay(())
          }.
          interruptWhen(promise)
          .onComplete(Stream.eval(Sync[F].delay(println("interrupted!"))))
      }
  }
  def interruption3[F[_] : Timer : Concurrent : Sync]: Stream[F, Any] = {
    Stream
      .eval(SignallingRef[F, Boolean](false))
      .flatMap { signal =>
        val src =
          Stream
            .emit[F, String]("ping")
            .repeat
            .metered(1.second)
            .evalTap(x => Sync[F].delay(println(s"${LocalDateTime.now()}: $x")))
            .pauseWhen(signal)
        val pause =
          Stream
            .sleep[F](3.seconds)
            .evalTap(_ => Sync[F].delay(println("» Pausing stream «")))
            .evalTap(_ => signal.set(true))
        val resume =
          Stream
            .sleep[F](7.seconds)
            .evalTap(_ => Sync[F].delay(println(("» Resuming stream «"))))
            .evalTap(_ => signal.set(false))
        Stream(src, pause, resume).parJoinUnbounded
      } .
      interruptAfter(10.seconds)
      .onComplete(Stream.eval(Sync[F].delay(("pong"))))
  }
}

object Main{
  def main(args: Array[String]): Unit = {
    import Interruption._
    val executionContext = ExecutionContext.global

    implicit val timer: Timer[IO] = IO.timer(executionContext)
    implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

    interruption[IO].compile.toVector.unsafeRunSync()
    interruption2[IO].compile.toVector.unsafeRunSync()
    interruption3[IO].compile.toVector.unsafeRunSync()
  }
}
