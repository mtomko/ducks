import sbt._

object build {

  object libraries {
    // plugins
    lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.0"

    // dependencies
    lazy val catsCore = "org.typelevel" %% "cats-core" % "1.6.0"
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % "1.3.1"
    lazy val catsPar = "io.chrisdavenport" %% "cats-par" % "0.2.1"
    lazy val console4cats = "com.github.gvolpe" %% "console4cats" % "0.6.0"
    lazy val decline = "com.monovore" %% "decline" % "0.6.2"
    lazy val fs2Core = "co.fs2" %% "fs2-core" % "1.0.4"
    lazy val fs2Io = "co.fs2" %% "fs2-io" % "1.0.4"
    lazy val log4s = "org.log4s" %% "log4s" % "1.8.0"
    lazy val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
    lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
    lazy val kantanCsv = "com.nrinaudo" %% "kantan.csv" % "0.5.0"

    // test dependencies
    lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.7"
  }

}
