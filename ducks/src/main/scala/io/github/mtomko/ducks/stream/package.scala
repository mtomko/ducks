package io.github.mtomko.ducks

import java.nio.file.Path

import cats.effect.{Blocker, Concurrent, ContextShift, Sync}
import fs2.{compress, io, text, Pipe, Stream}

package object stream {

  def lines[F[_]: Sync: Concurrent: ContextShift](p: Path)(implicit blocker: Blocker): Stream[F, String] = {
    val byteStream: Stream[F, Byte] =
      if (isGzFile(p)) io.file.readAll[F](p, blocker, 65536).through(compress.gunzip(65536))
      else io.file.readAll[F](p, blocker, 65536)
    byteStream
      .through(text.utf8Decode)
      .prefetchN(4096)
      .through(text.lines)
      .prefetchN(1024)
  }

  def fastq[F[_]]: Pipe[F, String, Fastq] =
    _.chunkN(4, allowFewer = false).map(seg => Fastq(seg(0), seg(1), seg(2), seg(3)))

}
