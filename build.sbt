import scala.sys.process._
import sbt._
import Keys._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging

ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "com.example"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// Ensure IOApp runs in a forked JVM to avoid non-main-thread warning when using `sbt run`
Compile / run / fork := true

lazy val runtimeDependencies = Seq(
  "org.typelevel" %% "log4cats-slf4j" % "2.7.0",
  "ch.qos.logback" % "logback-classic" % "1.5.6",
  "net.logstash.logback" % "logstash-logback-encoder" % "7.4",
  "org.flywaydb" % "flyway-core" % "10.12.0",
  "org.flywaydb" % "flyway-database-postgresql" % "10.12.0",
  "org.tpolecat" %% "doobie-core" % "1.0.0-RC5",
  "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC5",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC5",
  "com.zaxxer" % "HikariCP" % "5.1.0",
  "org.postgresql" % "postgresql" % "42.7.4",
  "org.mindrot" % "jbcrypt" % "0.4",
  "com.github.jwt-scala" %% "jwt-circe" % "10.0.1",
  "com.github.pureconfig" %% "pureconfig-core" % "0.17.6",
  "org.typelevel" %% "cats-effect" % "3.5.4",
  "org.http4s" %% "http4s-ember-server" % "0.23.26",
  "org.http4s" %% "http4s-dsl" % "0.23.26",
  "org.http4s" %% "http4s-circe" % "0.23.26",
  "io.circe" %% "circe-generic" % "0.14.9"
)

lazy val testDependencies = Seq(
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
  "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.4" % Test
)

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
  .settings(
    name := "scala-angular-cats-template",
    libraryDependencies ++= runtimeDependencies ++ testDependencies
  )

ThisBuild / resolvers += Resolver.mavenCentral

lazy val uiBuild = taskKey[Unit]("Build Angular UI assets")
lazy val startFrontend = taskKey[Unit]("Start Angular dev server when running the backend in dev mode")

uiBuild := {
  val log = streams.value.log
  val uiDir = baseDirectory.value / "ui"
  val packageJson = uiDir / "package.json"

  if (!packageJson.exists()) {
    log.info("[uiBuild] ui/package.json not found; skipping UI build")
  } else if (sys.env.get("SKIP_UI_BUILD").contains("true")) {
    log.info("[uiBuild] SKIP_UI_BUILD=true; skipping UI build")
  } else {
    def runCommand(label: String, command: String): Unit = {
      val stdout = new StringBuilder
      val stderr = new StringBuilder
      log.info(s"[$label] Executing: $command")
      val exitCode = Process(command, uiDir).!(ProcessLogger(
        out => {
          stdout.append(out).append('\n')
          log.info(s"[$label][stdout] $out")
        },
        err => {
          stderr.append(err).append('\n')
          log.error(s"[$label][stderr] $err")
        }
      ))
      if (exitCode != 0) {
        val summary = s"$label failed with exit code $exitCode"
        val details = new StringBuilder(summary)
        if (stdout.nonEmpty) details.append("\nstdout:\n").append(stdout.toString)
        if (stderr.nonEmpty) details.append("\nstderr:\n").append(stderr.toString)
        sys.error(details.toString)
      }
    }

    val angularMode = sys.env.getOrElse("ANGULAR_MODE", "prod")
    val buildCommand = angularMode match {
      case "heroku-local" => FrontendCommands.buildHerokuLocal
      case "prod"         => FrontendCommands.buildProd
      case _              => FrontendCommands.buildDev
    }

    runCommand("npm ci", FrontendCommands.dependencyInstall)

    runCommand(s"Angular build (ANGULAR_MODE=$angularMode)", buildCommand)
  }
}

startFrontend := {
  val log = streams.value.log
  val angularMode = sys.env.getOrElse("ANGULAR_MODE", "dev")
  if (angularMode == "dev") {
    log.info("Ensuring Angular dev server is running for sbt run...")
    FrontendRunHook(baseDirectory.value)()
  } else {
    log.info(s"Skipping Angular dev server startup for ANGULAR_MODE=$angularMode")
  }
}

watchSources ++= {
  val uiSrcDir = baseDirectory.value / "ui" / "src"
  PathFinder(uiSrcDir).allPaths.get
}

// Ensure dev mode starts the Angular proxy and packaging builds the correct assets
run := (Compile / run).dependsOn(startFrontend).evaluated
runMain := (Compile / runMain).dependsOn(startFrontend).evaluated

import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
Universal / stage := (Universal / stage).dependsOn(uiBuild).value
