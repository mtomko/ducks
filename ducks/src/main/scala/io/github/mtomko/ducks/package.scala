package io.github.mtomko

import java.io.PrintWriter
import java.nio.file.Path

import cats.effect.{ContextShift, Resource, Sync}
import cats.syntax.apply._
import fs2.Stream
import kantan.csv._
import kantan.csv.ops._

import scala.concurrent.ExecutionContext

package object ducks {

  def conditions[F[_]: Sync: ContextShift](path: Path)(
      implicit blockingEc: ExecutionContext): Stream[F, Map[Barcode, Condition]] = {
    val s: Stream[F, (Barcode, Condition)] =
      for {
        rdr <- Stream.resource(Resource.make(Sync[F].delay(path.asCsvReader[(String, String)](rfc)))(r =>
          ContextShift[F].evalOn(blockingEc)(Sync[F].delay(r.close()))))
        row <- Stream.fromIterator(rdr.toIterator)
        (bc, cond) <- Stream.fromEither(row)
      } yield (Barcode(bc), Condition(cond))
    s.fold(Map.empty[Barcode, Condition]) { case (m, (bc, cond)) => m + (bc -> cond) }
  }

  def fastq[F[_]: Sync: ContextShift](path: Path)(implicit blockingEc: ExecutionContext): Stream[F, Fastq] =
    stream.lines[F](path).through(stream.fastq)

  def fastqs[F[_]: Sync: ContextShift](p1: Path, p2: Path)(
      implicit blockingEc: ExecutionContext): Stream[F, (Fastq, Fastq)] =
    fastq(p1).buffer(16384).zip(fastq(p2).buffer(16384))

  def write[F[_]: Sync: ContextShift](fastq1: Fastq, fastq2: Fastq, writer: (PrintWriter, PrintWriter))(
      implicit blockingEc: ExecutionContext): F[Unit] = {

    def writeFastq(fastq: Fastq, writer: PrintWriter): Unit = {
      val lines = fastq.id + "\n" + fastq.seq + "\n" + fastq.id2 + "\n" + fastq.qual
      writer.println(lines)
    }

    val c1 = Sync[F].delay(writeFastq(fastq1, writer._1))
    val c2 = Sync[F].delay(writeFastq(fastq2, writer._2))
    ContextShift[F].evalOn(blockingEc)(c1 *> c2)
  }

  def isGzFile(p: Path): Boolean = p.getFileName.toString.toLowerCase.endsWith(".gz")

}
