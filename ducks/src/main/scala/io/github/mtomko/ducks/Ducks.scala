package io.github.mtomko.ducks

import java.nio.file.{Path, Paths}
import java.util.concurrent.Executors

import cats.effect.{Concurrent, ContextShift, ExitCode, IO, IOApp, Resource, Sync}
import cats.effect.Console.io._
import cats.implicits._
import cats.temp.par._
import com.monovore.decline.{Command, Help, Opts}
import fs2.Stream

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object Ducks extends IOApp {

  // TODO: is a fixed thread pool of size 2 optimal? This is what's given in the fs2 example, but we generally use an
  //       unbounded fork/join pool for blocking operations in other situations
  private[this] def blockingExecutionContext[F[_]: Sync]: Resource[F, ExecutionContextExecutorService] =
    Resource.make(Sync[F].delay(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))))(ec => Sync[F].delay(ec.shutdown()))

  def help(h: Help): IO[ExitCode] = putStrLn(h.toString()).map(_ => ExitCode.Error)

  private[this] val command: Command[Config] =
    Command(name = "ducks", header = "Demultiplexes FASTQ files based on conditions") {
      val conditionsFileOpt =
        Opts.option[Path]("conditions", short = "c", help = "The conditions file")
      val dmuxFastqOpt =
        Opts.option[Path]("dmux-fastq", short = "1", help = "The FASTQ file containing demultiplexing reads")
      val dataFastqOpt =
        Opts.option[Path]("data-fastq", short = "2", help = "The FASTQ file containing data reads")
      val outputDirectoryOpt =
        Opts.option[Path]("output-dir", short = "o", help = "The output directory").withDefault(Paths.get("."))

      (conditionsFileOpt, dmuxFastqOpt, dataFastqOpt, outputDirectoryOpt).mapN {
        (conditionsFile, dmuxFastq, dataFastq, outputDir) =>
          Config(conditionsFile, dmuxFastq, dataFastq, outputDir)
      }
    }

  def run[F[_]: Concurrent: Par: ContextShift](args: Config): F[ExitCode] =
    Stream
      .resource(blockingExecutionContext)
      .flatMap { implicit blockingEc =>
        for {
          conds <- conditions(args.conditionsFile)
          writers <- Stream.resource(Writers.resource(conds, args.outputDirectory))
          (dmf, daf) <- fastqs(args.fastq1, args.fastq2)
          writer <- Stream.emit(writers.writer(Barcode(dmf.seq)))
          _ <- write(dmf, daf, writer)
        } yield ()
      }
      .compile
      .drain
      .as(ExitCode.Success)

  override def run(args: List[String]): IO[ExitCode] = command.parse(args).fold(help, run[IO])

}
