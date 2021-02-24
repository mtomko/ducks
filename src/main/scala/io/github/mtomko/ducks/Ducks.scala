package io.github.mtomko.ducks

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Clock, Concurrent, ContextShift, ExitCode, IO, Sync}
import cats.syntax.all._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit

object Ducks
    extends CommandIOApp(
      name = "ducks",
      header = "Demultiplexes FASTQ files based on conditions",
      version = BuildInfo.version
    ) {
  implicit private[this] def unsafeLogger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLoggerFromName[F]("Ducks")

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
        run[IO](Config(conditionsFile, dmuxFastq, dataFastq, outputDir, zipOutput)).as(ExitCode.Success)
    }

  private[this] def run[F[_]: Clock: Concurrent: ContextShift](args: Config): F[Unit] =
    Blocker[F].use { blocker =>
      conditions(args.conditionsFile, blocker).flatMap { conds =>
        def selector(t: (Fastq, Fastq)): F[Condition] =
          Sync[F].delay(conds.getOrElse(t._1.seq, Condition("unmapped")))

        blocker.delay(Files.createDirectories(args.outputDirectory)) *>
          Ref.of(0).flatMap { count =>
            Clock[F].realTime(TimeUnit.MILLISECONDS).flatMap { t0 =>
              val fqs = logChunkN(fastqs[F](args.fastq1, args.fastq2, blocker), 1000000, t0, count)
              fqs
                .prefetch
                .through(stream.groupByChunk(selector))
                .map { case (condition, tupleStream) =>
                  val file = condition.file(args.outputDirectory, args.zipOutput)
                  writeTupleStream(file, args.zipOutput, tupleStream, blocker)
                }
                .parJoin(conds.size + 4)
                .compile
                .drain
            }
          }
      }
    }

}
