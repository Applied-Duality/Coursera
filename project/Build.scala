
import sbt._
import Keys._

object BuildSettings {
  val buildScalaVersion = "2.10.2"
  val buildVersion = "0.1"

  val buildSettings = Defaults.defaultSettings ++ Seq (
    scalaVersion := buildScalaVersion,
    version      := buildVersion
  )
}


object Dependencies {
  val compileDeps = Seq(
    "org.scala-lang" % "scala-library" % "2.10.2",
    "org.scala-lang" % "scala-reflect" % "2.10.2"
  )
  val testDeps = Seq(
    "org.scalatest" % "scalatest_2.10" % "1.9.2" % "test"
  )
  val allDeps = compileDeps ++ testDeps
}


object ExerciseBuild extends Build {
  import Dependencies._
  import BuildSettings._

  lazy val exercise3 = Project(
    "exercise3",
    file("."),
    settings = buildSettings ++ Seq(
      libraryDependencies := allDeps
    )
  )
}