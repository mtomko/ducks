package dev.mtomko.ducks.bench

import java.util.concurrent.TimeUnit

import dev.mtomko.ducks.fastq1
import fs2.{Pure, Stream}
import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@State(Scope.Benchmark)
class FastqBenchmark {

  @Benchmark
  @OperationsPerInvocation(10000)
  def fastq(): Unit = {
    val s = Stream("1", "2", "3", "4").repeat.take(10000)
    val _ = s.through(fastq1[Pure]).toList
  }

}
