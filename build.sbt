ThisBuild / scalaVersion := "2.13.5"
ThisBuild / organization := "io.github.mtomko"

lazy val libraries = new {
  // plugins
  lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"

  // dependencies
  lazy val catsCore = "org.typelevel" %% "cats-core" % "2.4.2"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.3.3"
  lazy val decline = "com.monovore" %% "decline" % "1.3.0"
  lazy val declineEffect = "com.monovore" %% "decline-effect" % "1.3.0"
  lazy val fs2Core = "co.fs2" %% "fs2-core" % "2.5.3"
  lazy val fs2Io = "co.fs2" %% "fs2-io" % "2.5.3"
  lazy val kantanCodecs = "com.nrinaudo" %% "kantan.codecs" % "0.5.2"
  lazy val kantanCsv = "com.nrinaudo" %% "kantan.csv" % "0.6.1"
  lazy val log4cats = "org.typelevel" %% "log4cats-core" % "1.2.0"
  lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % "1.2.0"
  lazy val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  lazy val newtype = "io.estatico" %% "newtype" % "0.4.4"
  lazy val slf4j = "org.slf4j" % "slf4j-api" % "1.7.30"

  // test dependencies
  lazy val munit = "org.scalameta" %% "munit" % "0.7.22"
  lazy val munitScalaCheck = "org.scalameta" %% "munit-scalacheck" % "0.7.22"
}

lazy val ducks = project
  .in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "ducks",
    version := "0.0.6",
    libraryDependencies ++=
      Seq(
        libraries.catsCore,
        libraries.catsEffect,
        libraries.decline,
        libraries.declineEffect,
        libraries.fs2Core,
        libraries.fs2Io,
        libraries.kantanCodecs,
        libraries.kantanCsv,
        libraries.log4cats,
        libraries.log4catsSlf4j,
        libraries.logbackClassic,
        libraries.logbackCore,
        libraries.newtype,
        libraries.slf4j,
        libraries.munit % Test,
        libraries.munitScalaCheck % Test
      ),
    scalacOptions += "-Ymacro-annotations",
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoPackage := "io.github.mtomko.ducks",
    testFrameworks := List(new TestFramework("munit.Framework")),
    addCompilerPlugin(libraries.betterMonadicFor)
  )
  .enablePlugins(GraalVMNativeImagePlugin)

