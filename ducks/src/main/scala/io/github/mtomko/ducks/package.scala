package io.github.mtomko

import java.io.PrintWriter
import java.nio.file.Path

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.temp.par._
import fs2.{Stream, compress, io}
import kantan.csv._
import kantan.csv.ops._

import scala.concurrent.ExecutionContext

package object ducks {

  private[this] val CR = '\r'.toByte
  private[this] val N = '\n'.toByte

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

  def fastq[F[_]: Sync: ContextShift](p: Path)(implicit blockingEc: ExecutionContext): Stream[F, Fastq] = {
    val byteStream: Stream[F, Byte] =
      if (p.endsWith(".gz")) io.file.readAll[F](p, blockingEc, 65536).through(compress.gunzip(8192))
      else io.file.readAll[F](p, blockingEc, 65536)
    byteStream
      .map(_.toChar)
      .filter(_ != CR)
      .split(_ == N)
      .map(bytes => new String(bytes.toArray))
      .through(pipe.fastq)
  }

  def fastqs[F[_]: Sync: ContextShift](fastq1: Path, fastq2: Path)(
      implicit blockingEc: ExecutionContext): Stream[F, (Fastq, Fastq)] = fastq(fastq1).zip(fastq(fastq2))

  def write[F[_]: Concurrent: Par: ContextShift](fastq1: Fastq, fastq2: Fastq, writer: (PrintWriter, PrintWriter))(
      implicit blockingEc: ExecutionContext): Stream[F, Unit] = {
    val c1 = Concurrent[F].delay(writeFastq(fastq1, writer._1))
    val c2 = Concurrent[F].delay(writeFastq(fastq2, writer._2))
    val c3 = ContextShift[F].evalOn(blockingEc)((c1, c2).parTupled.void)
    Stream.eval(c3)
  }

  //********************************************************************************************************************
  // Unsafe methods (these should only be called within the context of an effect)
  //********************************************************************************************************************

  private[this] def writeFastq(fastq: Fastq, writer: PrintWriter): Unit = {
    writer.println(fastq.id)
    writer.println(fastq.seq)
    writer.println(fastq.id2)
    writer.println(fastq.qual)
  }
}