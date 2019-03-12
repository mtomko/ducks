import build._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / organization     := "dev.mtomko"
ThisBuild / organizationName := "fastq-dmux"

lazy val root = (project in file("."))
  .settings(
    name := "fastq-dmux",
    libraryDependencies ++=
      Seq(
        libraries.catsCore,
        libraries.catsEffect,
        libraries.console4cats,
        libraries.decline,
        libraries.fs2Core,
        libraries.fs2Io,
        libraries.kantanCsv,
        libraries.scalaTest % Test
      ),
      addCompilerPlugin(libraries.betterMonadicFor)
  )
