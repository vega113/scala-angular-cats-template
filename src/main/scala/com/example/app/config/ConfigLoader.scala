package com.example.app.config

import cats.effect.{IO, Resource}
import pureconfig.ConfigSource
import com.example.app.db.DatabaseUrlParser

object ConfigLoader:
  def load: IO[AppConfig] =
    IO(ConfigSource.default.at("app").loadOrThrow[AppConfig])
      .map(cfg => cfg.copy(db = DatabaseUrlParser.normalize(cfg.db)))
