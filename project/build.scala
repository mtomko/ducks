import sbt._

object build {

  object libraries {
    // plugins
    lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"

    // dependencies
    lazy val catsCore = "org.typelevel" %% "cats-core" % "2.0.0"
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.0.0"
    lazy val console4cats = "dev.profunktor" %% "console4cats" % "0.8.0"
    lazy val decline = "com.monovore" %% "decline" % "1.0.0"
    lazy val declineEffect = "com.monovore" %% "decline-effect" % "1.0.0"
    lazy val fs2Core = "co.fs2" %% "fs2-core" % "2.1.0"
    lazy val fs2Io = "co.fs2" %% "fs2-io" % "2.1.0"
    lazy val kantanCsv = "com.nrinaudo" %% "kantan.csv" % "0.6.0"
    lazy val newtype = "io.estatico" %% "newtype" % "0.4.3"

    // test dependencies
    lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8"
  }

}
