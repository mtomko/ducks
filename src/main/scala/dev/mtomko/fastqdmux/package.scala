package dev.mtomko

import java.io._
import java.nio.file.Path
import java.util.zip.GZIPInputStream

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.temp.par._
import fs2.{Pipe, Stream, io}
import kantan.csv._
import kantan.csv.ops._

import scala.concurrent.ExecutionContext

package object fastqdmux {

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

  def fastq[F[_]: Sync: ContextShift](in: InputStream)(implicit blockingEc: ExecutionContext): Stream[F, Fastq] =
    io.readInputStream(Sync[F].delay(in), 4096, blockingEc, closeAfterUse = false)
      .map(_.toChar)
      .filter(_ != CR)
      .split(_ == N)
      .map(bytes => new String(bytes.toArray))
      .through(fastq)

  def fastqs[F[_]: Sync: ContextShift](fastq1: Path, fastq2: Path)(
      implicit blockingEc: ExecutionContext): Stream[F, (Fastq, Fastq)] =
    Stream
      .resource((inputStreamResource(fastq1), inputStreamResource(fastq2)).mapN { (i1, i2) =>
        fastq(i1).zip(fastq(i2))
      })
      .flatten

  final def fastq[F[_]]: Pipe[F, String, Fastq] = { in =>
    in.chunkN(4, allowFewer = false).map { seg =>
      val arr = seg.toArray
      if (arr.length == 4) Fastq(arr(0), arr(1), arr(2), arr(3))
      else throw new AssertionError("bug")
    }
  }

  def write[F[_]: Concurrent: Par : ContextShift](
      fastq1: Fastq,
      fastq2: Fastq,
      writer: (PrintWriter, PrintWriter))(implicit blockingEc: ExecutionContext): Stream[F, Unit] = {
    val c1 = Concurrent[F].delay(writeFastq(fastq1, writer._1))
    val c2 = Concurrent[F].delay(writeFastq(fastq2, writer._2))
    val c3 = ContextShift[F].evalOn(blockingEc)((c1, c2).parTupled.void)
    Stream.eval(c3)
  }

  private[this] def inputStreamResource[F[_]: Sync](p: Path): Resource[F, InputStream] = {
    if (p.getFileName.toString.toLowerCase.endsWith(".gz")) {
      Resource.make(Sync[F].delay(gzInputSteam(p)))(is => Sync[F].delay(is.close()))
    } else {
      Resource.make(Sync[F].delay(inputStream(p)))(is => Sync[F].delay(is.close()))
    }
  }

  //********************************************************************************************************************
  // Unsafe methods (these should only be called within the context of an effect)
  //********************************************************************************************************************

  def inputStream(p: Path): InputStream = new BufferedInputStream(new FileInputStream(p.toFile))
  def gzInputSteam(p: Path): InputStream = new GZIPInputStream(inputStream(p))

  private[this] def writeFastq(fastq: Fastq, writer: PrintWriter): Unit = {
    writer.println(fastq.id)
    writer.println(fastq.seq)
    writer.println(fastq.id2)
    writer.println(fastq.qual)
  }
}
