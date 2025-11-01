package com.example.app.config

import cats.effect.IO
import pureconfig.ConfigSource
import com.example.app.db.DatabaseUrlParser
import scala.concurrent.duration.{Duration, FiniteDuration}

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
        case _ => None
      )
    val tracingEnabled = sys.env
      .get("TRACING_ENABLED")
      .flatMap(_.trim.toLowerCase match
        case "true" => Some(true)
        case "false" => Some(false)
        case _ => None
      )
    val passwordResetUrlBase =
      sys.env.get("PASSWORD_RESET_URL_BASE").map(_.trim).filter(_.nonEmpty)
    val passwordResetTtl =
      sys.env
        .get("PASSWORD_RESET_TOKEN_TTL")
        .flatMap(parseFiniteDuration)

    val updatedAngular = cfg.angular.copy(
      mode = angularMode.getOrElse(cfg.angular.mode),
      port = angularPort.getOrElse(cfg.angular.port)
    )

    val updatedTracing = cfg.tracing.copy(
      enabled = tracingEnabled.getOrElse(cfg.tracing.enabled)
    )

    val updatedPasswordReset = cfg.passwordReset.copy(
      resetUrlBase = passwordResetUrlBase.getOrElse(cfg.passwordReset.resetUrlBase),
      tokenTtl = passwordResetTtl.getOrElse(cfg.passwordReset.tokenTtl)
    )

    cfg.copy(
      angular = updatedAngular,
      tracing = updatedTracing,
      passwordReset = updatedPasswordReset
    )

  private def parseFiniteDuration(value: String): Option[FiniteDuration] =
    scala.util.Try(Duration(value)).toOption.collect { case fd: FiniteDuration => fd }
