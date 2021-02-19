package io.github.mtomko.ducks

import java.nio.file.Path
import cats.effect.{Blocker, Concurrent, ContextShift, Sync}
import cats.effect.concurrent.Ref
import cats.syntax.all._
import fs2.{compression, io, text, Chunk, Pipe, Stream}
import fs2.concurrent.Queue

import scala.collection.mutable

package object stream {
  private[this] val BufferSize = 8192

  def lines[F[_]: Sync: Concurrent: ContextShift](p: Path)(implicit blocker: Blocker): Stream[F, String] = {
    val byteStream: Stream[F, Byte] =
      if (isGzFile(p))
        io.file
          .readAll[F](p, blocker, BufferSize)
          .through(compression.gunzip[F](BufferSize / 2))
          .flatMap(g => g.content)
      else io.file.readAll[F](p, blocker, BufferSize)
    byteStream
      .mapChunks(c => Chunk(new String(c.toBytes.toArray, "ASCII")))
      .through(text.lines)
  }

  def writeFile[F[_]: Sync: Concurrent: ContextShift](p: Path, zip: Boolean)(implicit
      blocker: Blocker
  ): Pipe[F, Byte, Unit] = { in =>
    val s = if (zip) in.through(compression.gzip(BufferSize)) else in
    s.through(io.file.writeAll(p, blocker))
  }

  def fastq[F[_]]: Pipe[F, String, Fastq] =
    _.chunkN(4, allowFewer = false).map(seg => Fastq(seg(0), seg(1), seg(2), seg(3)))

  // from
  // https://gist.github.com/kiambogo/8247a7bbf79f00414d1489b7e6fc90d0
  // and
  // https://gist.github.com/SystemFw/168ff694eecf45a8d0b93ce7ef060cfd
  def groupBy[F[_], A, K](
      groupByF: A => F[K]
  )(implicit F: Concurrent[F], o: Ordering[K]): Pipe[F, A, (K, Stream[F, A])] = { (in: Stream[F, A]) =>
    Stream
      .eval(
        Ref.of[F, mutable.Map[K, Queue[F, Option[A]]]](new mutable.TreeMap[K, Queue[F, Option[A]]]())
      )
      .flatMap { ref =>
        val cleanup: F[Unit] = ref.get.flatMap(_.toList.traverse_(_._2.enqueue1(None)))

        val s: Stream[F, A] = in ++ Stream.eval_(cleanup)

        s.evalMap { el =>
          (groupByF(el), ref.get).mapN { (key, queues) =>
            queues.get(key) match {
              case None =>
                for {
                  newQ <- Queue.unbounded[F, Option[A]] // Create a new queue
                  _ <- ref.modify(m => m.put(key, newQ).as((m, m)).getOrElse((m, m))) // Update the ref of queues
                  _ <- newQ.enqueue1(el.some)
                } yield (key -> newQ.dequeue.unNoneTerminate).some
              case Some(q) =>
                val ret: F[Option[(K, Stream[F, A])]] = q.enqueue1(el.some).as(None)
                ret
            }
          }.flatten
        }.unNone.onFinalize(cleanup)
      }
  }
}
