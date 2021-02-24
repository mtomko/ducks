package io.github.mtomko.ducks.stream

//import cats.syntax.all._
import fs2.Stream
import munit.FunSuite

class StreamPackageTest extends FunSuite {

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
      assertEquals(fq.seq,"GATTTGGGGTTCAAAGCAGTATCGATCAAATAGTAAATCCATTTGTTCAACTCACAGTTT")
      assertEquals(fq.id2, "+")
      assertEquals(fq.qual, "!''*((((***+))%%%++)(%%%%).1***-+*''))**55CCF>>>>>>CCCCCCC65")
    }
  }

}
