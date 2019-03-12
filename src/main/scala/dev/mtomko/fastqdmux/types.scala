package dev.mtomko.fastqdmux

import java.net.URLEncoder
import java.nio.file.Path

final case class Barcode(barcode: String) extends AnyVal

final case class Condition(name: String) extends AnyVal {
  def filename: String = URLEncoder.encode(name, "UTF-8") + ".fastq"
  def file(outputDir: Path): Path = outputDir.resolve(filename)
}

final case class Fastq(id: String, seq: String, id2: String, qual: String)