package io.github.mtomko

import java.net.URLEncoder
import java.nio.file.Path

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync}
import fs2.Stream
import io.estatico.newtype.macros.newtype
import kantan.csv._
import kantan.csv.ops._

package object ducks {
  @newtype final case class Barcode(barcode: String)

  @newtype final case class Condition(name: String) {
    private[this] def encoded(name: String) = URLEncoder.encode(name, "UTF-8")
    def filename(zip: Boolean): String = encoded(name) + (if (zip) ".fastq.gz" else ".fastq")
    def file(outputDir: Path, zip: Boolean): Path = outputDir.resolve(filename(zip))
  }

  def conditions[F[_]: Sync: ContextShift](
      path: Path
  )(implicit blocker: Blocker): F[Map[Barcode, Condition]] = {
    val s: Stream[F, (Barcode, Condition)] =
      for {
        rdr <- Stream.resource(Resource.fromAutoCloseable(Sync[F].delay(path.asCsvReader[(String, String)](rfc))))
        row <- Stream.fromBlockingIterator(blocker, rdr.toIterable.iterator)
        (bc, cond) <- Stream.fromEither(row)
      } yield (Barcode(bc), Condition(cond))
    s.fold(Map.empty[Barcode, Condition]) { case (m, (bc, cond)) => m + (bc -> cond) }.compile.lastOrError
  }

  def fastq[F[_]: Sync: Concurrent: ContextShift](path: Path)(implicit blocker: Blocker): Stream[F, Fastq] =
    stream.lines[F](path).prefetchN(16).through(stream.fastq)

  def fastqs[F[_]: Sync: Concurrent: ContextShift](p1: Path, p2: Path)(implicit
      blocker: Blocker
  ): Stream[F, (Fastq, Fastq)] =
    fastq(p1).zip(fastq(p2))

  def isGzFile(p: Path): Boolean = p.getFileName.toString.toLowerCase.endsWith(".gz")
}
