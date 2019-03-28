package io.github.mtomko.ducks.bench

import java.util.concurrent.TimeUnit

import fs2.{Pure, Stream}
import io.github.mtomko.ducks.pipe
import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@State(Scope.Benchmark)
class FastqBenchmark {

  @inline
  private[this] final def baseline10000: Stream[Pure, String] = Stream("id", "seq", "id2", "qual").repeat.take(10000)

  @Benchmark
  @OperationsPerInvocation(10000)
  def baseline(): Unit = {
    val _ = baseline10000.toList
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def fastq(): Unit = {
    val _ = baseline10000.through(pipe.fastq).toList
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def zipped(): Unit = {
    val _ = baseline10000.zip(baseline10000).toList
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def zippedFastq(): Unit = {
    val _ = baseline10000.through(pipe.fastq).zip(baseline10000.through(pipe.fastq)).toList
  }

}
