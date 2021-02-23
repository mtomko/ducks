package io.github.mtomko

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Clock, Concurrent, ContextShift, Resource, Sync}
import cats.syntax.all._
import fs2.{text, Stream}
import io.chrisdavenport.log4cats.Logger
import io.estatico.newtype.macros.newtype
import kantan.csv._
import kantan.csv.ops._

import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.TimeUnit

package object ducks {

  val ASCII = Charset.forName("ASCII")

  @newtype final case class Condition(name: String) {
    private[this] def encoded = URLEncoder.encode(name, "UTF-8")
    def filename(zip: Boolean): String = encoded + (if (zip) ".fastq.gz" else ".fastq")
    def file(outputDir: Path, zip: Boolean): Path = outputDir.resolve(filename(zip))
  }

  object Condition {
    implicit val ord: Ordering[Condition] = Ordering.by(_.name)
  }

  // this function is not in the hot path
  def conditions[F[_]: Sync: ContextShift](path: Path, blocker: Blocker): F[Map[String, Condition]] = {
    val acquire = Sync[F].delay(path.asCsvReader[(String, String)](rfc))
    val s: Stream[F, (String, Condition)] =
      for {
        rdr <- Stream.resource(Resource.fromAutoCloseableBlocking(blocker)(acquire))
        row <- Stream.fromBlockingIterator(blocker, rdr.toIterable.iterator)
        (bc, cond) <- Stream.fromEither(row)
      } yield (bc, Condition(cond))
    s.fold(Map.empty[String, Condition]) { case (m, (bc, cond)) => m + (bc -> cond) }.compile.lastOrError
  }

  private[ducks] def writeTupleStream[F[_]: Sync: Concurrent: ContextShift](
    outputFile: Path,
    zipOutput: Boolean,
    tupleStream: Stream[F, (Fastq, Fastq)],
    blocker: Blocker
  ): Stream[F, Unit] =
    tupleStream
      .prefetchN(4)
      .map(_._2.toString)
      .through(text.encode(ASCII))
      .through(stream.writeFile(outputFile, zipOutput, blocker))

  private[ducks] def logChunkN[F[_]: Sync: Clock: Concurrent, A](s: Stream[F, A], n: Int, t0: Long, count: Ref[F, Int])(
    implicit log: Logger[F]
  ): Stream[F, A] =
    s.chunkN(100, allowFewer = true)
      .prefetchN(4)
      .evalTapChunk { chunk =>
        val updatedCountF = count.modify { prevCount =>
          val nextCount = prevCount + chunk.size
          (nextCount, nextCount)
        }
        updatedCountF.flatMap { currentCount =>
          if (currentCount % n === 0)
            Clock[F].realTime(TimeUnit.MILLISECONDS).flatMap { tn =>
              val dt = tn - t0
              val avg = currentCount.toFloat / dt
              log.info(s"processed $currentCount reads ($avg reads/ms)")
            }
          else ().pure[F]
        }
      }
      .flatMap(Stream.chunk)

  def fastq[F[_]: Sync: Concurrent: ContextShift](path: Path, blocker: Blocker): Stream[F, Fastq] =
    stream.lines[F](path, blocker).prefetchN(16).through(stream.fastq)

  // no amount or combination of prefetching here seems to help with performance
  def fastqs[F[_]: Sync: Concurrent: ContextShift](p1: Path, p2: Path, blocker: Blocker): Stream[F, (Fastq, Fastq)] =
    fastq(p1, blocker).zip(fastq(p2, blocker))

  def isGzFile(p: Path): Boolean = p.getFileName.toString.toLowerCase.endsWith(".gz")
}
