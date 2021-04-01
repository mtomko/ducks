package io.github.mtomko.ducks

import cats.effect.std.Queue
import cats.effect.{Async, Concurrent, Ref}
import cats.syntax.all._
import fs2.compression.Compression
import fs2.io.file.Files
import fs2.{text, Chunk, Pipe, Stream}

import java.nio.file.Path
import scala.collection.mutable

package object stream {
  private[this] val BufferSize = 8192

  def lines[F[_]: Async](p: Path): Stream[F, String] = {
    val byteStream: Stream[F, Byte] =
      if (isGzFile(p))
        Files[F]
          .readAll(p, BufferSize)
          .through(Compression[F].gunzip(BufferSize / 2))
          .flatMap(g => g.content)
      else Files[F].readAll(p, BufferSize)
    byteStream
      .mapChunks { c =>
        val a = Array.ofDim[Byte](c.size)
        c.copyToArray(a)
        Chunk(new String(a, ASCII))
      }
      .through(text.lines)
  }

  def writeFile[F[_]: Async](p: Path, zip: Boolean): Pipe[F, Byte, Unit] = { in =>
    val s = if (zip) in.through(Compression[F].gzip(BufferSize)) else in
    s.through(Files[F].writeAll(p))
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
      .eval(Ref.of[F, mutable.Map[K, Queue[F, Option[A]]]](new mutable.TreeMap))
      .flatMap { ref: Ref[F, mutable.Map[K, Queue[F, Option[A]]]] =>
        val cleanup: F[Unit] = ref.get.flatMap(_.toList.traverse_(_._2.offer(None)))

        val stream: Stream[F, A] = in ++ Stream.exec(cleanup)

        stream
          .evalMapChunk { el =>
            (groupByF(el), ref.get).mapN { (key, queues) =>
              queues.get(key) match {
                case Some(q) => q.offer(el.some).as(none[(K, Stream[F, A])])
                case None =>
                  for {
                    q <- Queue.unbounded[F, Option[A]] // create a new queue
                    _ <- ref.modify { m =>
                      // update the ref of queues
                      val _ = m.put(key, q)
                      (m, m)
                    }
                    _ <- q.offer(el.some)
                  } yield (key -> Stream.fromQueueNoneTerminated(q)).some
              }
            }.flatten
          }
          .unNone
          .onFinalize(cleanup)
      }
  }
}
