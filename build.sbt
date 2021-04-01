import ReleaseTransformations._

ThisBuild / scalaVersion := "2.13.5"
ThisBuild / organization := "io.github.mtomko"

lazy val libraries = new {
  // plugins
  lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"

  // dependencies
  lazy val catsCore = "org.typelevel" %% "cats-core" % "2.5.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.0.1"
  lazy val catsEffectStd = "org.typelevel" %% "cats-effect-std" % "3.0.1"
  lazy val decline = "com.monovore" %% "decline" % "2.0.0-RC1"
  lazy val declineEffect = "com.monovore" %% "decline-effect" % "2.0.0-RC1"
  lazy val fs2Core = "co.fs2" %% "fs2-core" % "3.0.1"
  lazy val fs2Io = "co.fs2" %% "fs2-io" % "3.0.1"
  lazy val kantanCodecs = "com.nrinaudo" %% "kantan.codecs" % "0.5.2"
  lazy val kantanCsv = "com.nrinaudo" %% "kantan.csv" % "0.6.1"
  lazy val log4cats = "org.typelevel" %% "log4cats-core" % "2.0.0"
  lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % "2.0.0"
  lazy val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.3"
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  lazy val newtype = "io.estatico" %% "newtype" % "0.4.4"
  lazy val slf4j = "org.slf4j" % "slf4j-api" % "1.7.30"

  // test dependencies
  lazy val munit = "org.scalameta" %% "munit" % "0.7.23"
  lazy val munitCatsEffect = "org.typelevel" %% "munit-cats-effect-3" % "1.0.1"
  lazy val munitScalaCheck = "org.scalameta" %% "munit-scalacheck" % "0.7.23"
}

lazy val ducks = project
  .in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "ducks",
    libraryDependencies ++=
      Seq(
        libraries.catsCore,
        libraries.catsEffect,
        libraries.catsEffectStd,
        libraries.decline,
        libraries.declineEffect,
        libraries.fs2Core,
        libraries.fs2Io,
        libraries.kantanCodecs,
        libraries.kantanCsv,
        libraries.log4cats,
        libraries.log4catsSlf4j,
        libraries.newtype,
        libraries.logbackClassic % Runtime,
        libraries.logbackCore % Runtime,
        libraries.slf4j % Runtime,
        libraries.munit % Test,
        libraries.munitCatsEffect % Test,
        libraries.munitScalaCheck % Test
      ),
    scalacOptions += "-Ymacro-annotations",
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoPackage := "io.github.mtomko.ducks",
    testFrameworks := List(new TestFramework("munit.Framework")),
    addCompilerPlugin(libraries.betterMonadicFor),
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,              // : ReleaseStep
      inquireVersions,                        // : ReleaseStep
      runClean,                               // : ReleaseStep
      runTest,                                // : ReleaseStep
      setReleaseVersion,                      // : ReleaseStep
      commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
      tagRelease,                             // : ReleaseStep
      //publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
      setNextVersion,                         // : ReleaseStep
      commitNextVersion,                      // : ReleaseStep
      pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
    )
  )
  .enablePlugins(GraalVMNativeImagePlugin)
