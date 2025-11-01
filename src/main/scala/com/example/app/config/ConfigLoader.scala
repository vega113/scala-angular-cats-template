package com.example.app.config

import cats.effect.{IO, Resource}
import pureconfig.ConfigSource
import com.example.app.db.DatabaseUrlParser

object ConfigLoader:
  def load: IO[AppConfig] =
    IO(ConfigSource.default.at("app").loadOrThrow[AppConfig])
      .map(overrideWithEnv)
      .map(cfg => cfg.copy(db = DatabaseUrlParser.normalize(cfg.db)))

  private def overrideWithEnv(cfg: AppConfig): AppConfig =
    val angularMode = sys.env.get("ANGULAR_MODE").map(_.trim).filter(_.nonEmpty)
    val angularPort = sys.env
      .get("ANGULAR_PORT")
      .flatMap(_.trim match
        case s if s.nonEmpty => s.toIntOption
        case _               => None
      )
    val tracingEnabled = sys.env
      .get("TRACING_ENABLED")
      .flatMap(_.trim.toLowerCase match
        case "true"  => Some(true)
        case "false" => Some(false)
        case _        => None
      )

    val updatedAngular = cfg.angular.copy(
      mode = angularMode.getOrElse(cfg.angular.mode),
      port = angularPort.getOrElse(cfg.angular.port)
    )

    val updatedTracing = cfg.tracing.copy(
      enabled = tracingEnabled.getOrElse(cfg.tracing.enabled)
    )

    cfg.copy(angular = updatedAngular, tracing = updatedTracing)
