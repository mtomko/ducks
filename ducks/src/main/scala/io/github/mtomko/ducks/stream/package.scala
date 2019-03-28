package io.github.mtomko.ducks

import java.nio.file.Path

import cats.effect.{ContextShift, Sync}
import fs2.{Pipe, Stream, compress, io}

import scala.concurrent.ExecutionContext

package object stream {

  private[this] val CR = '\r'.toByte
  private[this] val N = '\n'.toByte

  def lines[F[_]: Sync: ContextShift](p: Path)(implicit blockingEc: ExecutionContext): Stream[F, String] = {
    val byteStream: Stream[F, Byte] =
      if (isGzFile(p)) io.file.readAll[F](p, blockingEc, 65536).through(compress.gunzip(8192))
      else io.file.readAll[F](p, blockingEc, 65536)
    byteStream
      .map(_.toChar)
      .filter(_ != CR)
      .split(_ == N)
      .map(bytes => new String(bytes.toArray))
  }

  def fastq[F[_]]: Pipe[F, String, Fastq] = { in =>
    in.chunkN(4, allowFewer = false).map { seg =>
      Fastq(seg(0), seg(1), seg(2), seg(3))
    }
  }

}
