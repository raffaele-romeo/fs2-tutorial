import cats.effect.{Concurrent, Resource, Timer}
import fs2.concurrent.Queue
import cats.{Eval => _, _}
import cats.implicits._
import scala.concurrent.duration.FiniteDuration

trait Background[F[_]] {
  def schedule[A](
      fa: F[A],
      duration: FiniteDuration
  ): F[Unit]
}

object ResourceSaftey {
  def resource[F[_]: Concurrent: Timer]: Resource[F, Background[F]] =
    fs2.Stream
      .eval(Queue.unbounded[F, (FiniteDuration, F[Any])])
      .flatMap { q =>
        val bg = new Background[F] {
          def schedule[A](
              fa: F[A],
              duration: FiniteDuration
          ): F[Unit] =
            q.enqueue1(duration -> fa.widen)
        }
        val process = q.dequeue.map {
          case (duration, fa) =>
            fs2.Stream.eval_(fa.attempt).delayBy(duration)
        }.parJoinUnbounded
        fs2.Stream.emit(bg).concurrently(process)
      }
      .compile
      .resource
      .lastOrError
}
