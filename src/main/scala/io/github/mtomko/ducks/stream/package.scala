package io.github.mtomko.ducks

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Concurrent, ContextShift, Sync}
import cats.syntax.all._
import fs2.concurrent.{NoneTerminatedQueue, Queue}
import fs2.{compression, io, text, Chunk, Pipe, Stream}

import java.nio.file.Path
import scala.collection.mutable

package object stream {
  private[this] val BufferSize = 8192

  def lines[F[_]: Sync: ContextShift](p: Path, blocker: Blocker): Stream[F, String] = {
    val byteStream: Stream[F, Byte] =
      if (isGzFile(p))
        io.file
          .readAll[F](p, blocker, BufferSize)
          .through(compression.gunzip[F](BufferSize / 2))
          .flatMap(g => g.content)
      else io.file.readAll[F](p, blocker, BufferSize)
    byteStream
      .mapChunks(c => Chunk(new String(c.toBytes.toArray, ASCII)))
      .through(text.lines)
  }

  def writeFile[F[_]: Sync: ContextShift](p: Path, zip: Boolean, blocker: Blocker): Pipe[F, Byte, Unit] = {
    in =>
      val s = if (zip) in.through(compression.gzip(BufferSize)) else in
      s.through(io.file.writeAll(p, blocker))
  }

  // does no validation; this is garbage-in, garbage-out
  def fastq[F[_]]: Pipe[F, String, Fastq] =
    _.chunkN(4, allowFewer = false).map(seg => Fastq(seg(0), seg(1), seg(2), seg(3)))

  // from
  // https://gist.github.com/kiambogo/8247a7bbf79f00414d1489b7e6fc90d0
  // and
  // https://gist.github.com/SystemFw/168ff694eecf45a8d0b93ce7ef060cfd
  def groupByChunk[F[_], A, K](
    groupByF: A => F[K]
  )(implicit F: Concurrent[F], o: Ordering[K]): Pipe[F, A, (K, Stream[F, A])] = { (in: Stream[F, A]) =>
    Stream
      .eval(Ref.of[F, mutable.Map[K, NoneTerminatedQueue[F, A]]](new mutable.TreeMap))
      .flatMap { ref =>
        val cleanup: F[Unit] = ref.get.flatMap(_.toList.traverse_(_._2.enqueue1(None)))

        val stream: Stream[F, A] = in ++ Stream.eval_(cleanup)

        stream
          .evalMapChunk { el =>
            (groupByF(el), ref.get).mapN { (key, queues) =>
              queues.get(key) match {
                case Some(q) => q.enqueue1(el.some).as(none[(K, Stream[F, A])])
                case None =>
                  for {
                    q <- Queue.noneTerminated[F, A] // create a new queue
                    _ <- ref.modify { m =>
                      // update the ref of queues
                      val _ = m.put(key, q)
                      (m, m)
                    }
                    _ <- q.enqueue1(el.some)
                  } yield (key -> q.dequeue).some
              }
            }.flatten
          }
          .unNone
          .onFinalize(cleanup)
      }
  }
}
