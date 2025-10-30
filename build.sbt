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

ThisBuild / resolvers += Resolver.mavenCentral

libraryDependencies ++= Seq(
  "org.typelevel" %% "log4cats-slf4j" % "2.7.0",
  "ch.qos.logback" %  "logback-classic" % "1.5.6",
  "net.logstash.logback" % "logstash-logback-encoder" % "7.4"
)

libraryDependencies ++= Seq(
  "org.flywaydb" % "flyway-core" % "10.12.0"
)

libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core"    % "1.0.0-RC5",
  "org.tpolecat" %% "doobie-hikari"  % "1.0.0-RC5",
  "org.tpolecat" %% "doobie-postgres"% "1.0.0-RC5",
  "com.zaxxer"   %  "HikariCP"       % "5.1.0",
  "org.postgresql" % "postgresql"    % "42.7.4"
)

// Tests
libraryDependencies ++= Seq(
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
)

libraryDependencies ++= Seq(
  "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.4" % Test
)

libraryDependencies ++= Seq(
  "com.github.pureconfig" %% "pureconfig-core" % "0.17.6",
  "org.typelevel" %% "cats-effect" % "3.5.4"
)

// UI build task and stage integration
lazy val uiBuild = taskKey[Unit]("Build Angular UI (prod)")

uiBuild := {
  val log = streams.value.log
  val uiDir = baseDirectory.value / "ui"
  if ((uiDir / "package.json").exists) {
    def run(cmd: String) = Process(cmd, uiDir).!
    log.info("[uiBuild] npm ci ...")
    val c1 = run("npm ci")
    if (c1 != 0) sys.error(s"npm ci failed: $c1")
    log.info("[uiBuild] npm run build:prod ...")
    val c2 = run("npm run build:prod -- --output-path ../src/main/resources/static")
    if (c2 != 0) sys.error(s"npm run build:prod failed: $c2")
  } else log.info("[uiBuild] ui/package.json not found; skipping UI build")
}

// Ensure Native Packager stage runs after UI build
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
Universal / stage := (Universal / stage).dependsOn(uiBuild).value

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % "0.23.26",
  "org.http4s" %% "http4s-dsl" % "0.23.26",
  "org.http4s" %% "http4s-circe" % "0.23.26",
  "io.circe" %% "circe-generic" % "0.14.9"
)
