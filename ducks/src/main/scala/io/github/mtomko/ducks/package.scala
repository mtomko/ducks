package io.github.mtomko

import java.io.{BufferedReader, FileInputStream, InputStreamReader, PrintWriter}
import java.nio.file.Path
import java.util.zip.GZIPInputStream

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

  def fastq[F[_]: Sync: ContextShift](p: Path)(implicit blockingEc: ExecutionContext): Stream[F, Fastq] = {
    @inline
    def gzipReader(p: Path): BufferedReader =
      new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(p.toFile), 65536)))

    @inline
    def reader(p: Path): BufferedReader =
      new BufferedReader(new InputStreamReader(new FileInputStream(p.toFile)), 65536)
  
    val inputStreamResource: Resource[F, BufferedReader] = Resource.fromAutoCloseable {
      Sync[F].delay {
        if (isGzFile(p)) gzipReader(p)
        else reader(p)
      }
    }

    @inline
    def fastqStream(r: BufferedReader): Stream[F, Fastq] =
      Stream.unfoldEval(r) { s =>
        ContextShift[F].evalOn(blockingEc) {
          Sync[F].delay {
            val l1 = s.readLine()
            val l2 = s.readLine()
            val l3 = s.readLine()
            val l4 = s.readLine()
            if (l4 == null) None
            else Some((Fastq(l1, l2, l3, l4), s))
          }
        }
      }

    for {
      s <- Stream.resource(inputStreamResource)
      f <- fastqStream(s)
    } yield f
  }

  def fastqs[F[_]: Sync: ContextShift](p1: Path, p2: Path)(
      implicit blockingEc: ExecutionContext): Stream[F, (Fastq, Fastq)] =
    fastq(p1).buffer(4096).zip(fastq(p2).buffer(4096))

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
