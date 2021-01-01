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
        run[IO](Config(conditionsFile, dmuxFastq, dataFastq, outputDir, zipOutput)).as(ExitCode.Success)
    }

  private[this] def selector[F[_]: Sync](conds: Map[Barcode, Condition])(t: (Fastq, Fastq)): F[Condition] =
    Sync[F].delay {
      conds.getOrElse(Barcode(t._1.seq), Condition("unmapped"))
    }

  private[this] def run[F[_]: Sync: Clock: Concurrent: ContextShift](args: Config): F[Unit] =
    Blocker[F].use { implicit blocker =>
      Ref.of(0).flatMap { count =>
        conditions(args.conditionsFile).flatMap { conds =>
          Clock[F].realTime(TimeUnit.MILLISECONDS).flatMap { t0 =>
            val fqs = logChunkN(fastqs[F](args.fastq1, args.fastq2), 100, t0, count).prefetchN(16)
            fqs
              .through(stream.groupBy(selector[F](conds)))
              .map {
                case (condition, tupleStream) =>
                  writeTupleStream(condition.file(args.outputDirectory, args.zipOutput), args.zipOutput, tupleStream)
              }
              .parJoinUnbounded
              .compile
              .drain
          }
        }
      }
    }

  private[this] def writeTupleStream[F[_]: Sync: Concurrent: ContextShift](
      outputFile: Path,
      zipOutput: Boolean,
      tupleStream: Stream[F, (Fastq, Fastq)]
  )(implicit b: Blocker): Stream[F, Unit] =
    tupleStream
      .map(_._2.toString)
      .through(text.utf8Encode)
      .through(stream.writeFile(outputFile, zipOutput))

  private[this] def logChunkN[F[_]: Sync: Clock: Concurrent, A](
      s: Stream[F, A],
      n: Int,
      t0: Long,
      count: Ref[F, Int]
  ): Stream[F, A] =
    s.chunkN(n, allowFewer = true)
      .prefetchN(4)
      .evalTap { chunk =>
        val ncf = count.modify { c =>
          val nc = c + chunk.size
          (nc, nc)
        }
        ncf.flatMap { nc =>
          if (nc % 100000 === 0)
            Clock[F].realTime(TimeUnit.MILLISECONDS).flatMap { tn =>
              val dt = tn - t0
              val avg = nc.toFloat / dt
              Logger[F].info(s"processed $nc reads ($avg reads/ms)")
            }
          else ().pure[F]
        }
      }
      .flatMap(Stream.chunk)
}
