package dev.mtomko

import java.io.{BufferedWriter, FileWriter, Writer}
import java.nio.file.Path

import cats.effect.{ContextShift, Resource, Sync}
import fs2.{io, text, Pipe, Stream}
import kantan.csv._
import kantan.csv.ops._

import scala.concurrent.ExecutionContext

package object fastqdmux {

  def conditions[F[_]: Sync: ContextShift](path: Path)(
      implicit blockingEc: ExecutionContext): Stream[F, Map[Barcode, Condition]] = {
    val s: Stream[F, (Barcode, Condition)] =
      for {
        rdr <- Stream.resource(Resource.make(Sync[F].delay(path.asCsvReader[(String, String)](rfc)))(r =>
          ContextShift[F].evalOn(blockingEc)(Sync[F].delay(r.close()))))
        row <- Stream.fromIterator(rdr.toIterator)
        (x, y) = row.right.get
      } yield (Barcode(x), Condition(y))
    s.fold(Map.empty[Barcode, Condition]) { case (m, (bc, cond)) => m + (bc -> cond) }
  }

  def outputStreams[F[_]: Sync: ContextShift](conditions: Set[Condition], outputDir: Path)(
      implicit blockingEc: ExecutionContext): Stream[F, (Map[Condition, BufferedWriter], BufferedWriter)] =
    Stream.resource {
      Resource.make(Sync[F].delay(writers(conditions, outputDir))) {
        case (ws, w) =>
          val close = Sync[F].delay(flushAndClose(ws.valuesIterator ++ Iterator(w)))
          ContextShift[F].evalOn(blockingEc)(close)
      }
    }

  def fastq[F[_]: Sync: ContextShift](path: Path)(implicit blockingEc: ExecutionContext): Stream[F, Fastq] =
    io.file
      .readAll[F](path, blockingEc, 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .through(fastq)

  def fastqs[F[_]: Sync: ContextShift](fastq1: Path, fastq2: Path)(
      implicit blockingEc: ExecutionContext): Stream[F, (Fastq, Fastq)] =
    fastq(fastq1).zip(fastq(fastq2))

  final def fastq[F[_]]: Pipe[F, String, Fastq] = { in =>
    in.chunkN(4, allowFewer = false).map { seg =>
      val arr = seg.toArray
      if (arr.length == 4) Fastq(arr(0), arr(1), arr(2), arr(3))
      else throw new AssertionError("bug")
    }
  }

  def write[F[_]: Sync: ContextShift](fastq: Fastq, writer: BufferedWriter)(
      implicit blockingEc: ExecutionContext): Stream[F, Unit] =
    Stream.eval(ContextShift[F].evalOn(blockingEc)(Sync[F].delay(write1(fastq, writer))))

  //********************************************************************************************************************
  // Unsafe methods (these should only be called within the context of an effect)
  //********************************************************************************************************************
  private[this] def write1(fastq: Fastq, writer: BufferedWriter): Unit = {
    writer.write(fastq.id)
    writer.newLine()
    writer.write(fastq.seq)
    writer.newLine()
    writer.write(fastq.id2)
    writer.newLine()
    writer.write(fastq.qual)
    writer.newLine()
  }

  private[this] def writers(
      conditions: Set[Condition],
      outputDir: Path): (Map[Condition, BufferedWriter], BufferedWriter) = {
    require(!conditions.contains(Condition("unmatched")))
    val conditionWriters = conditions.map(cond => cond -> writer(cond, outputDir)).toMap
    (conditionWriters, writer(Condition("unamatched"), outputDir))
  }

  private[this] def writer(cond: Condition, outputDir: Path): BufferedWriter =
    new BufferedWriter(new FileWriter(cond.file(outputDir).toFile))

  private[this] def flushAndClose(ws: TraversableOnce[Writer]): Unit = {
    ws.foreach(flushAndClose)
  }

  private[this] def flushAndClose(w: Writer): Unit = {
    w.flush()
    w.close()
  }
}
