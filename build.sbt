import scala.sys.process._
import sbt._
import Keys._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging

ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "com.example"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
  .settings(
    name := "scala-angular-cats-template",
    // keep deps minimal for T-101; others added in later tasks
    libraryDependencies ++= Seq(
      // cats-effect base will be added in later tasks when needed
    )
  )
