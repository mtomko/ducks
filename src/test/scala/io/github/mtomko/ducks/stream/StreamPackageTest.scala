package io.github.mtomko.ducks.stream

import cats.effect.IO
//import cats.syntax.all._
import fs2.Stream
import munit.CatsEffectSuite

class StreamPackageTest extends CatsEffectSuite {

  test("fastq") {
    val fq = List(
      "@SEQ_ID",
      "GATTTGGGGTTCAAAGCAGTATCGATCAAATAGTAAATCCATTTGTTCAACTCACAGTTT",
      "+",
      "!''*((((***+))%%%++)(%%%%).1***-+*''))**55CCF>>>>>>CCCCCCC65"
    )

    val records = Stream.emits(fq).repeatN(4).through(fastq).compile.toList

    assertEquals(records.size, 4)
    records.foreach { fq =>
      assertEquals(fq.id, "@SEQ_ID")
      assertEquals(fq.seq, "GATTTGGGGTTCAAAGCAGTATCGATCAAATAGTAAATCCATTTGTTCAACTCACAGTTT")
      assertEquals(fq.id2, "+")
      assertEquals(fq.qual, "!''*((((***+))%%%++)(%%%%).1***-+*''))**55CCF>>>>>>CCCCCCC65")
    }
  }

  test("groupByChunk") {
    sealed trait Mod3 {
      def n: Int
    }
    case object Zero extends Mod3 { val n = 0 }
    case object One extends Mod3 { val n = 1 }
    case object Two extends Mod3 { val n = 2 }
    object Mod3 {
      def apply(x: Int): IO[Mod3] = (math.abs(x) % 3) match {
        case 0 => IO.pure(Zero)
        case 1 => IO.pure(One)
        case 2 => IO.pure(Two)
        case x => IO.raiseError(new Exception(s"Unexpected $x"))
      }
      implicit val ordering: Ordering[Mod3] = Ordering.by(_.n)
    }

    val f =
      Stream
        .iterate(0)(_ + 1)
        .take(11)
        .through(groupByChunk(Mod3.apply))
        .map { case (m, s) => Stream.eval(s.compile.toList.map(l => (m, l))) }
        .parJoinUnbounded
        .compile
        .toList
        .map(_.sortBy(_._1))

    assertIO(f, List((Zero, List(0, 3, 6, 9)), (One, List(1, 4, 7, 10)), (Two, List(2, 5, 8))))
  }

}
