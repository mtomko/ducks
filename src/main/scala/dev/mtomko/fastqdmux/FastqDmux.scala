package dev.mtomko.fastqdmux

import java.nio.file.{Path, Paths}
import java.util.concurrent.Executors

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.effect.Console.io._
import cats.implicits._
import com.monovore.decline.{Command, Help, Opts}
import fs2.Stream

import scala.concurrent.ExecutionContext

object FastqDmux extends IOApp {

  // TODO: is a fixed thread pool of size 2 optimal? This is what's given in the fs2 example, but we generally use an
  //       unbounded fork/join pool for blocking operations in other situations
  private[this] val blockingExecutionContext =
    Resource.make(IO(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))))(ec => IO(ec.shutdown()))

  def help(h: Help): IO[ExitCode] = putStrLn(h.toString()).map(_ => ExitCode.Error)

  private[this] val command: Command[Config] =
    Command(name = "fastq-dmux", header = "Demultiplexes FASTQ files based on conditions") {
      val conditionsFileOpt =
        Opts.option[Path]("conditions", help = "The conditions file")
      val dmuxFastqOpt =
        Opts.option[Path]("dmux-fastq", short = "m", help = "The FASTQ file containing demultiplexing reads")
      val dataFastqOpt =
        Opts.option[Path]("data-fastq", short = "d", help = "The FASTQ file containing data reads")
      val outputDirectoryOpt =
        Opts.option[Path]("output-dir", short = "o", help = "The output directory").withDefault(Paths.get("."))

      (conditionsFileOpt, dmuxFastqOpt, dataFastqOpt, outputDirectoryOpt).mapN {
        (conditionsFile, dmuxFastq, dataFastq, outputDir) =>
          Config(conditionsFile, dmuxFastq, dataFastq, outputDir)
      }
    }

  private[this] def run(args: Config): IO[ExitCode] =
    Stream
      .resource(blockingExecutionContext)
      .flatMap { blockingEc =>
        for {
          conds <- conditions(args.conditionsFile, blockingEc)
          (m, u) <- outputStreams(conds.values.toSet, args.outputDirectory, blockingEc)
          (dmf, daf) <- fastqs(args.fastq1, args.fastq2, blockingEc)
          writer <- Stream.emit(conds.get(Barcode(dmf.seq)).flatMap(m.get).getOrElse(u))
          _ <- write(daf, writer, blockingEc)
        } yield ()
      }
      .compile
      .drain
      .as(ExitCode.Success)

  override def run(args: List[String]): IO[ExitCode] = command.parse(args).fold(help, run)

}
