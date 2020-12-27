import sbt._

object build {

  object libraries {
    // plugins
    lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"

    // dependencies
    lazy val catsCore = "org.typelevel" %% "cats-core" % "2.3.1"
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.3.1"
    lazy val console4cats = "dev.profunktor" %% "console4cats" % "0.8.1"
    lazy val decline = "com.monovore" %% "decline" % "1.3.0"
    lazy val declineEffect = "com.monovore" %% "decline-effect" % "1.3.0"
    lazy val fs2Core = "co.fs2" %% "fs2-core" % "2.4.6"
    lazy val fs2Io = "co.fs2" %% "fs2-io" % "2.4.6"
    lazy val kantanCsv = "com.nrinaudo" %% "kantan.csv" % "0.6.1"
    lazy val newtype = "io.estatico" %% "newtype" % "0.4.4"

    // test dependencies
    lazy val munit = "org.scalameta" %% "munit" % "0.7.20"
    lazy val munitScalaCheck = "org.scalameta" %% "munit-scalacheck" % "0.7.20"
  }

}
