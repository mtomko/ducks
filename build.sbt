import build._

ThisBuild / scalaVersion     := "2.13.4"
ThisBuild / organization     := "io.github.mtomko"

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
        libraries.munit % Test,
        libraries.munitScalaCheck % Test
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
