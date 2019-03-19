package dev.mtomko.ducks

import java.nio.file.Path

final case class Config(conditionsFile: Path, fastq1: Path, fastq2: Path, outputDirectory: Path)
