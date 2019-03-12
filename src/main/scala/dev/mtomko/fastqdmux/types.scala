package dev.mtomko.fastqdmux

final case class Barcode(barcode: String) extends AnyVal

final case class Condition(name: String) extends AnyVal

final case class Fastq(id: String, seq: String, id2: String, qual: String)