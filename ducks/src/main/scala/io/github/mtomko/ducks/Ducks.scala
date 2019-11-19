package io.github.mtomko.ducks

import java.nio.file.{Path, Paths}

import cats.effect.{Blocker, Concurrent, ContextShift, ExitCode, IO, Sync}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import fs2.Stream

object Ducks
  extends CommandIOApp(
    name = "ducks",
    header = "Demultiplexes FASTQ files based on conditions",
    version = BuildInfo.version
  ) {

  private[this] val conditionsFileOpt = Opts.option[Path]("conditions", short = "c", help = "The conditions file")

  private[this] val dmuxFastqOpt =
    Opts.option[Path]("dmux-fastq", short = "1", help = "The FASTQ file containing demultiplexing reads")

  private[this] val dataFastqOpt =
    Opts.option[Path]("data-fastq", short = "2", help = "The FASTQ file containing data reads")

  private[this] val outputDirectoryOpt =
    Opts.option[Path]("output-dir", short = "o", help = "The output directory").withDefault(Paths.get("."))

  override def main: Opts[IO[ExitCode]] =
    (conditionsFileOpt, dmuxFastqOpt, dataFastqOpt, outputDirectoryOpt).mapN {
      (conditionsFile, dmuxFastq, dataFastq, outputDir) =>
        run[IO](Config(conditionsFile, dmuxFastq, dataFastq, outputDir)).compile.drain.as(ExitCode.Success)
    }

  private[this] def run[F[_]: Sync: Concurrent: ContextShift](args: Config): Stream[F, Unit] =
    for {
      implicit0(blocker: Blocker) <- Stream.resource(Blocker[F])
      conds <- conditions[F](args.conditionsFile)
      writers <- Stream.resource(Writers.resource(conds, args.outputDirectory))
      (dmf, daf) <- fastqs[F](args.fastq1, args.fastq2)
      writer <- Stream.emit(writers.writer(Barcode(dmf.seq)))
      _ <- Stream.eval(write[F](dmf, daf, writer))
    } yield ()

}
