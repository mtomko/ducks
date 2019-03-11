package dev.mtomko

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Path

import cats.effect.{ContextShift, IO, Resource}
import fs2.{io, text, Stream}
import kantan.csv.ops._

import scala.concurrent.ExecutionContext

package object fastqdmux {

  def conditions(
      path: Path, blockingEc: ExecutionContext)(implicit cs: ContextShift[IO]): Stream[IO, Map[Barcode, Condition]] = {
    io.file
      .readAll[IO](path, blockingEc, 4096)
      .through(text.utf8Decode)
      .through(text.lines).drop(1) // drop the header
      .map(line => line.asCsvRow).collect { case barcode +: condition +: _ => Barcode(barcode) -> Condition(condition) }
      .fold(Map.empty[Barcode, Condition]) { case (m, (bc, cond)) => m + (bc -> cond) }
  }

  def outputStreams(
      conditions: Map[Barcode, Condition],
      outputDir: Path): Stream[IO, Map[Barcode, BufferedWriter]] = {
    Stream.resource {
      Resource.make(
        IO(conditions.map { case (bc, cond) =>
          bc -> new BufferedWriter(new FileWriter(outputDir.resolve(cond.name + ".txt").toFile))
        })
      )(ws => IO(ws.values.foreach { w =>
        w.flush()
        w.close()
      }))
    }
  }

}

