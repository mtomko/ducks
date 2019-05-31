package io.github.mtomko.ducks

import java.net.URLEncoder
import java.nio.file.Path

final case class Barcode(barcode: String) extends AnyVal

final case class Condition(name: String) extends AnyVal {
  def filename(suffix: String): String = URLEncoder.encode(name + suffix, "UTF-8") + ".fastq"
  def file(suffix: String, outputDir: Path): Path = outputDir.resolve(filename(suffix))
}

final case class Fastq(id: String, seq: String, id2: String, qual: String)
