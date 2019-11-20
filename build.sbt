import build._

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / organization     := "dev.mtomko"

lazy val root = project.in(file("."))
  .aggregate(ducks, bench)
  .disablePlugins(AssemblyPlugin)

lazy val ducks = project.in(file("ducks"))
  .enablePlugins(BuildInfoPlugin)
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
        libraries.newtype,
        libraries.scalaTest % Test
      ),
    scalacOptions += "-Ymacro-annotations",
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoPackage := "io.github.mtomko.ducks",
    addCompilerPlugin(libraries.betterMonadicFor)
  )

lazy val bench = project.in(file("bench"))
  .dependsOn(ducks)
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JmhPlugin)
