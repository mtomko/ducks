package io.github.mtomko.ducks

import java.nio.file.Path

import cats.effect.{Blocker, Concurrent, ContextShift, Sync}
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.{compress, io, text, Pipe, Stream}
import fs2.concurrent.Queue

package object stream {

  def lines[F[_]: Sync: Concurrent: ContextShift](p: Path)(implicit blocker: Blocker): Stream[F, String] = {
    val byteStream: Stream[F, Byte] =
      if (isGzFile(p)) io.file.readAll[F](p, blocker, 65536).through(compress.gunzip(65536))
      else io.file.readAll[F](p, blocker, 65536)
    byteStream
      .through(text.utf8Decode)
      .through(text.lines)
  }

  def fastq[F[_]]: Pipe[F, String, Fastq] =
    _.chunkN(4, allowFewer = false).map(seg => Fastq(seg(0), seg(1), seg(2), seg(3)))

  // from https://gist.github.com/kiambogo/8247a7bbf79f00414d1489b7e6fc90d0 and
  // https://gist.github.com/SystemFw/168ff694eecf45a8d0b93ce7ef060cfd
  def groupBy[F[_], A, K](selector: A => F[K])(implicit F: Concurrent[F]): Pipe[F, A, (K, Stream[F, A])] = { in =>
    Stream.eval(Ref.of[F, Map[K, Queue[F, Option[A]]]](Map.empty)).flatMap { st =>
      val cleanup = {
        st.get.flatMap(_.toList.traverse_(_._2.enqueue1(None)))
      }

      (in ++ Stream.eval_(cleanup)).evalMap { el =>
        (selector(el), st.get).mapN { (key, queues) =>
          queues
            .get(key)
            .fold {
              for {
                newQ <- Queue.unbounded[F, Option[A]] // Create a new queue
                _ <- st.modify(x => (x + (key -> newQ), x)) // Update the ref of queues
                _ <- newQ.enqueue1(el.some)
              } yield (key -> newQ.dequeue.unNoneTerminate).some
            }(_.enqueue1(el.some).as(None))
        }.flatten
      }.unNone.onFinalize(cleanup)
    }
  }

}
