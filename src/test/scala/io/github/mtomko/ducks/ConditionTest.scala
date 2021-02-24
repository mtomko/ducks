package io.github.mtomko.ducks

import munit.FunSuite

class ConditionTest extends FunSuite {

  test("filename") {
    assertEquals(Condition("This is a thing").filename(false), "This+is+a+thing.fastq")
    assertEquals(Condition("This is a thing").filename(true), "This+is+a+thing.fastq.gz")
  }

}
