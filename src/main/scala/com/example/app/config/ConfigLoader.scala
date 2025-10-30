package com.example.app.config

import cats.effect.{IO, Resource}
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderException

object ConfigLoader:
  def load: IO[AppConfig] =
    IO(ConfigSource.default.at("app").loadOrThrow[AppConfig])
