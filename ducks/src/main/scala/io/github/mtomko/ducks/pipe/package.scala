package io.github.mtomko.ducks

import fs2.Pipe

package object pipe {

  def fastq[F[_]]: Pipe[F, String, Fastq] = { in =>
    in.chunkN(4, allowFewer = false).map { seg =>
      val arr = seg.toArray
      if (arr.length == 4) Fastq(arr(0), arr(1), arr(2), arr(3))
      else throw new AssertionError("bug")
    }
  }

}
