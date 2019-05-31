import build._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / organization     := "dev.mtomko"
ThisBuild / organizationName := "fastq-dmux"

lazy val root = project.in(file("."))
  .aggregate(ducks, bench)
  .disablePlugins(AssemblyPlugin)

lazy val ducks = project.in(file("ducks"))
  .settings(
    name := "ducks",
    libraryDependencies ++=
      Seq(
        libraries.catsCore,
        libraries.catsEffect,
        libraries.catsPar,
        libraries.console4cats,
        libraries.decline,
        libraries.fs2Core,
        libraries.fs2Io,
        libraries.log4s,
        libraries.logbackClassic,
        libraries.logbackCore,
        libraries.kantanCsv,
        libraries.scalaTest % Test
      ),
      addCompilerPlugin(libraries.betterMonadicFor)
  )

lazy val bench = project.in(file("bench"))
  .dependsOn(ducks)
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JmhPlugin)
