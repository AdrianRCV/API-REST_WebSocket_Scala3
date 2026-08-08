ThisBuild / scalaVersion := "3.3.8"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.adrian"

val http4sVersion      = "0.23.35"
val tapirVersion       = "1.13.29"
val doobieVersion      = "1.0.0-RC13"
val catsEffectVersion  = "3.7.0"
val fs2Version         = "3.13.0"
val circeVersion       = "0.14.15"
val log4catsVersion    = "2.8.0"
val logbackVersion     = "1.5.13"
val munitCEVersion     = "2.2.0"

lazy val root = (project in file("."))
  .settings(
    name := "event-metrics-system",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "co.fs2"        %% "fs2-core"    % fs2Version,
      "co.fs2"        %% "fs2-io"      % fs2Version,

      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
      "org.http4s" %% "http4s-circe"        % http4sVersion,

      "com.softwaremill.sttp.tapir" %% "tapir-core"              % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"     % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,

      "org.typelevel" %% "doobie-core"     % doobieVersion,
      "org.typelevel" %% "doobie-postgres" % doobieVersion,
      "org.typelevel" %% "doobie-hikari"   % doobieVersion,

      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,

      "org.typelevel"  %% "log4cats-slf4j"  % log4catsVersion,
      "ch.qos.logback"  % "logback-classic" % logbackVersion,

      "org.typelevel" %% "munit-cats-effect" % munitCEVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
