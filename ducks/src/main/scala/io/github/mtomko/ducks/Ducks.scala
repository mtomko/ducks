package io.github.mtomko.ducks

import java.nio.file.{Path, Paths}

import cats.effect.{Blocker, Concurrent, ContextShift, ExitCode, IO, Sync}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import fs2.{io, text, Stream}

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

  private[this] def selector[F[_]: Sync](conds: Map[Barcode, Condition])(t: (Fastq, Fastq)): F[Condition] =
    Sync[F].delay {
      conds.getOrElse(Barcode(t._1.seq), Condition("unmapped"))
    }

  private[this] def run[F[_]: Sync: Concurrent: ContextShift](args: Config): Stream[F, Unit] = {
    val s: Stream[F, Stream[F, Unit]] =
      for {
        implicit0(blocker: Blocker) <- Stream.resource(Blocker[F])
        conds <- conditions[F](args.conditionsFile)
        (condition, tupleStream) <- fastqs[F](args.fastq1, args.fastq2).through(stream.groupBy(selector[F](conds)))
      } yield
        tupleStream
          .map(_._2.toString)
          .through(text.utf8Encode)
          .through(io.file.writeAll(condition.file(".data", args.outputDirectory), blocker))
    s.parJoinUnbounded
  }

}
