package io.github.mtomko.ducks

import cats.syntax.all._
import munit.FunSuite

import java.nio.file.Paths

class DucksPackageTest extends FunSuite {

  test("isGzFile") {
    assert(isGzFile(Paths.get("foo.bar.gz")))
    assert(isGzFile(Paths.get("foo.bar.GZ")))
    assert(isGzFile(Paths.get("foo.bar.gZ")))
    assert(isGzFile(Paths.get("foo.bar.Gz")))
    assert(isGzFile(Paths.get("foo.bar.gazump")) === false)
  }

}
