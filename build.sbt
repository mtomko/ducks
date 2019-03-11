import Dependencies._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / organization     := "dev.mtomko"
ThisBuild / organizationName := "fastq-dmux"

lazy val root = (project in file("."))
  .settings(
    name := "fastq-dmux",
    libraryDependencies ++=
      Seq(
        catsCore,
        catsEffect,
        console4cats,
        decline,
        fs2Core,
        fs2Io,
        kantanCsv,
        scalaTest % Test
      )
  )
