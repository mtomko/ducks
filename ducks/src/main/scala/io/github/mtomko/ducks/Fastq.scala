package io.github.mtomko.ducks

final case class Fastq(id: String, seq: String, id2: String, qual: String) {

  override def toString: String = id + "\n" + seq + "\n" + id2 + "\n" + qual + "\n"

}
