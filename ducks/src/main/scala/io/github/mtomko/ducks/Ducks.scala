package io.github.mtomko.ducks

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Clock, Concurrent, ContextShift, ExitCode, IO, Sync}
import cats.syntax.all._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import fs2.{text, Stream}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

object Ducks
  extends CommandIOApp(
    name = "ducks",
    header = "Demultiplexes FASTQ files based on conditions",
    version = BuildInfo.version
  ) {
  implicit private[this] def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]

  private[this] val conditionsFileOpt = Opts.option[Path]("conditions", short = "c", help = "The conditions file")

  private[this] val dmuxFastqOpt =
    Opts.option[Path]("dmux-fastq", short = "1", help = "The FASTQ file containing demultiplexing reads")

  private[this] val dataFastqOpt =
    Opts.option[Path]("data-fastq", short = "2", help = "The FASTQ file containing data reads")

  private[this] val outputDirectoryOpt =
    Opts.option[Path]("output-dir", short = "o", help = "The output directory").withDefault(Paths.get("."))

  private[this] val zipOutputOpt =
    Opts.flag("zip-output", short = "z", help = "Zip output files").orFalse

  override def main: Opts[IO[ExitCode]] =
    (conditionsFileOpt, dmuxFastqOpt, dataFastqOpt, outputDirectoryOpt, zipOutputOpt).mapN {
      (conditionsFile, dmuxFastq, dataFastq, outputDir, zipOutput) =>
        run[IO](Config(conditionsFile, dmuxFastq, dataFastq, outputDir, zipOutput)).compile.drain.as(ExitCode.Success)
    }

  private[this] def selector[F[_]: Sync](conds: Map[Barcode, Condition])(t: (Fastq, Fastq)): F[Condition] =
    Sync[F].delay {
      conds.getOrElse(Barcode(t._1.seq), Condition("unmapped"))
    }

  private[this] def run[F[_]: Sync: Clock: Concurrent: ContextShift](args: Config): Stream[F, Unit] = {
    val s: Stream[F, Stream[F, Unit]] =
      for {
        implicit0(blocker: Blocker) <- Stream.resource(Blocker[F])
        count <- Stream.eval(Ref.of(0))
        conds <- conditions[F](args.conditionsFile)
        t0 <- Stream.eval(Clock[F].realTime(TimeUnit.MILLISECONDS))
        fqs = logChunkN(fastqs[F](args.fastq1, args.fastq2), 100000, t0, count)
        (condition, tupleStream) <- fqs.through(stream.groupBy(selector[F](conds)))
      } yield {
        val outputFile = condition.file(args.outputDirectory, args.zipOutput)
        tupleStream
          .map(_._2.toString)
          .through(text.utf8Encode)
          .through(stream.writeFile(outputFile, args.zipOutput))
      }
    s.parJoinUnbounded
  }

  private[this] def logChunkN[F[_]: Sync: Clock, A](
      s: Stream[F, A],
      n: Int,
      t0: Long,
      count: Ref[F, Int]
  ): Stream[F, A] =
    s.chunkN(n, allowFewer = true)
      .evalTap { chunk =>
        val ncf = count.modify { c =>
          val nc = c + chunk.size
          (nc, nc)
        }
        (Clock[F].realTime(TimeUnit.MILLISECONDS), ncf).tupled.flatMap {
          case (tn, nc) =>
            val dt = tn - t0
            val avg = nc.toFloat / dt
            Logger[F].info(s"processed $nc reads ($avg reads/ms)")
        }
      }
      .flatMap(Stream.chunk)
}
