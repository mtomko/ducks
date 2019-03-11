package dev.mtomko.fastqdmux

import java.nio.file.Path

final case class Config(conditionsFile: Path, fastq: Path, outputDirectory: Path)
