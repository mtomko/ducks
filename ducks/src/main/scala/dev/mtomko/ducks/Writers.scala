package dev.mtomko.ducks

import java.io._
import java.nio.file.Path

import cats.effect.{ContextShift, Resource, Sync}
import dev.mtomko.ducks.Writers._

import scala.concurrent.ExecutionContext

final class Writers(conditions: Map[Barcode, Condition], outputDir: Path) extends Closeable {
  require(!conditions.values.exists(_ == Unmatched))

  private[this] val resources: Map[Condition, (PrintWriter, PrintWriter)] = {
    conditions.map { case (_, condition) =>
      condition -> makeWritersFor(condition, outputDir)
    }
  }
  private[this] val unmatched: (PrintWriter, PrintWriter) = makeWritersFor(Unmatched, outputDir)

  def writer(b: Barcode): (PrintWriter, PrintWriter) =
    conditions.get(b).flatMap(resources.get).getOrElse(unmatched)

  override def close(): Unit = {
    resources.foreach {
      case (_, (p1, p2)) =>
        flushAndClose(p1)
        flushAndClose(p2)
    }
    flushAndClose(unmatched._1)
    flushAndClose(unmatched._2)
  }
}

object Writers {

  def resource[F[_]: Sync: ContextShift](conditions: Map[Barcode, Condition], outputDir: Path)(
      implicit blockingEc: ExecutionContext): Resource[F, Writers] =
    Resource.make(Sync[F].delay(new Writers(conditions, outputDir)))(w =>
      ContextShift[F].evalOn(blockingEc)(Sync[F].delay(w.close())))

  private[Writers] val Unmatched = Condition("unmatched")

  private[Writers] def makeWritersFor(condition: Condition, outputDir: Path): (PrintWriter, PrintWriter) =
    (new PrintWriter(new BufferedWriter(new FileWriter(condition.file(".sample", outputDir).toFile))),
     new PrintWriter(new BufferedWriter(new FileWriter(condition.file(".construct", outputDir).toFile))))

  private[Writers] def flushAndClose(w: Writer): Unit = {
    w.flush()
    w.close()
  }

}
