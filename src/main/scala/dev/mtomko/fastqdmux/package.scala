package dev.mtomko

import java.io.{BufferedWriter, FileWriter, Writer}
import java.nio.file.Path

import cats.effect.{ContextShift, IO, Resource}
import fs2.{io, text, Pipe, Stream}
import kantan.csv._
import kantan.csv.ops._

import scala.concurrent.ExecutionContext

package object fastqdmux {

  def conditions(path: Path, blockingEc: ExecutionContext)(
      implicit cs: ContextShift[IO]): Stream[IO, Map[Barcode, Condition]] = {
    val s: Stream[IO, (Barcode, Condition)] =
      for {
        rdr <- Stream.resource(
          Resource.make(IO(path.asCsvReader[(String, String)](rfc)))(r => cs.evalOn(blockingEc)(IO(r.close()))))
        row <- Stream.fromIterator[IO, kantan.csv.ReadResult[(String, String)]](rdr.toIterator)
        (x, y) = row.right.get
      } yield (Barcode(x), Condition(y))
    s.fold(Map.empty[Barcode, Condition]) { case (m, (bc, cond)) => m + (bc -> cond) }
  }

  def outputStreams(conditions: Set[Condition], outputDir: Path, blockingEc: ExecutionContext)(
      implicit cs: ContextShift[IO]): Stream[IO, Map[Condition, BufferedWriter]] =
    Stream.resource {
      Resource.make(IO(conditions.map(cond => cond -> writer(cond, outputDir)).toMap))(ws =>
        cs.evalOn(blockingEc)(IO(flushAndClose(ws.values))))
    }

  def fastq(path: Path, blockingEc: ExecutionContext)(implicit cs: ContextShift[IO]): Stream[IO, Fastq] =
    io.file
      .readAll[IO](path, blockingEc, 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .through(fastq)

  def fastqs(fastq1: Path, fastq2: Path, blockingEc: ExecutionContext)(
      implicit cs: ContextShift[IO]): Stream[IO, (Fastq, Fastq)] =
    fastq(fastq1, blockingEc).zip(fastq(fastq2, blockingEc))

  final def fastq[F[_]]: Pipe[F, String, Fastq] = { in =>
    in.chunkN(4, allowFewer = false).map { seg =>
      seg.toList match {
        case id :: seq :: id2 :: qual :: Nil => Fastq(id, seq, id2, qual)
        case _                               => throw new AssertionError("bug")
      }
    }
  }

  def write(fastq: Fastq, writer: BufferedWriter, blockingEc: ExecutionContext)(
      implicit cs: ContextShift[IO]): Stream[IO, Unit] =
    Stream.eval(cs.evalOn(blockingEc)(IO(write(fastq, writer))))

  //********************************************************************************************************************
  // Unsafe methods (these should only be called within the context of an effect)
  //********************************************************************************************************************
  private[this] def write(fastq: Fastq, writer: BufferedWriter): Unit = {
    writer.write(fastq.id)
    writer.newLine()
    writer.write(fastq.seq)
    writer.newLine()
    writer.write(fastq.id2)
    writer.newLine()
    writer.write(fastq.qual)
    writer.newLine()
  }

  private[this] def writer(cond: Condition, outputDir: Path): BufferedWriter =
    new BufferedWriter(new FileWriter(outputDir.resolve(cond.name + ".txt").toFile))

  private[this] def flushAndClose(ws: TraversableOnce[Writer]): Unit = {
    ws.foreach(flushAndClose)
  }

  private[this] def flushAndClose(w: Writer): Unit = {
    w.flush()
    w.close()
  }
}
