import build._

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / organization     := "dev.mtomko"

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
        libraries.console4cats,
        libraries.decline,
        libraries.declineEffect,
        libraries.fs2Core,
        libraries.fs2Io,
        libraries.kantanCsv,
        libraries.scalaTest % Test
      ),
      addCompilerPlugin(libraries.betterMonadicFor)
  )

lazy val bench = project.in(file("bench"))
  .dependsOn(ducks)
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JmhPlugin)
