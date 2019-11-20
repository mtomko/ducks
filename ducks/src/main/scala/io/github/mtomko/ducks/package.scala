package io.github.mtomko

import java.io.Writer
import java.net.URLEncoder
import java.nio.file.Path

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync}
import cats.syntax.apply._
import fs2.Stream
import io.estatico.newtype.macros.newtype
import kantan.csv._
import kantan.csv.ops._

package object ducks {

  @newtype final case class Barcode(barcode: String)

  @newtype final case class Condition(name: String) {
    def filename(suffix: String): String = URLEncoder.encode(name + suffix, "UTF-8") + ".fastq"
    def file(suffix: String, outputDir: Path): Path = outputDir.resolve(filename(suffix))
  }

  def conditions[F[_]: Sync: ContextShift](path: Path): Stream[F, Map[Barcode, Condition]] = {
    val s: Stream[F, (Barcode, Condition)] =
      for {
        rdr <- Stream.resource(Resource.fromAutoCloseable(Sync[F].delay(path.asCsvReader[(String, String)](rfc))))
        row <- Stream.fromIterator(rdr.toIterable.iterator)
        (bc, cond) <- Stream.fromEither(row)
      } yield (Barcode(bc), Condition(cond))
    s.fold(Map.empty[Barcode, Condition]) { case (m, (bc, cond)) => m + (bc -> cond) }
  }

  def fastq[F[_]: Sync: Concurrent: ContextShift](path: Path)(implicit blocker: Blocker): Stream[F, Fastq] =
    stream.lines[F](path).through(stream.fastq)

  def fastqs[F[_]: Sync: Concurrent: ContextShift](p1: Path, p2: Path)(
      implicit blocker: Blocker): Stream[F, (Fastq, Fastq)] =
    fastq(p1).zip(fastq(p2))

  def write[F[_]: Sync: ContextShift](fastq1: Fastq, fastq2: Fastq, writer: (Writer, Writer))(
      implicit blocker: Blocker): F[Unit] = {

    def writeFastq(fastq: Fastq, writer: Writer): Unit = {
      val lines = fastq.id + "\n" + fastq.seq + "\n" + fastq.id2 + "\n" + fastq.qual + "\n"
      writer.write(lines)
    }

    val c1 = Sync[F].delay(writeFastq(fastq1, writer._1))
    val c2 = Sync[F].delay(writeFastq(fastq2, writer._2))
    blocker.blockOn(c1 *> c2)
  }

  def isGzFile(p: Path): Boolean = p.getFileName.toString.toLowerCase.endsWith(".gz")

}
