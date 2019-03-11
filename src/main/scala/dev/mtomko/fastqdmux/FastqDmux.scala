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

  private[this] val blockingExecutionContext =
    Resource.make(IO(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))))(ec => IO(ec.shutdown()))

  def help(h: Help): IO[ExitCode] = putStrLn(h.toString()).map(_ => ExitCode.Error)

  private[this] val command: Command[Config] = Command(
    name = "fastq-dmux",
    header = "Demultiplexes FASTQ files based on conditions") {
    val conditionsFileOpt =
      Opts.option[Path]("conditions", help = "The conditions file")
    val fastqFileOpt =
      Opts.option[Path]("fastq", help = "The FASTQ file")
    val outputDirectoryOpt =
      Opts.option[Path]("output-dir", short = "o", help = "The output directory").withDefault(Paths.get("."))

    (conditionsFileOpt, fastqFileOpt, outputDirectoryOpt)
      .mapN { (conditionsFile, fastqFile, outputDir) => Config(conditionsFile, fastqFile, outputDir) }
  }

  private[this] def run(args: Config): IO[ExitCode] = {
    Stream.resource(blockingExecutionContext).flatMap { blockingEc =>
      for {
        conds <- conditions(args.conditionsFile, blockingEc)
        _ <- outputStreams(conds, args.outputDirectory)
      } yield ()
    }.compile.drain.as(ExitCode.Success)
  }

  override def run(args: List[String]): IO[ExitCode] = command.parse(args).fold(help, run)

}
