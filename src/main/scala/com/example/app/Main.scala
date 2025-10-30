package com.example.app

import cats.effect.{IO, IOApp, Resource}
import com.example.app.config.*

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    for
      cfg <- ConfigLoader.load
      _   <- IO.println(s"App config loaded: http=${cfg.http.port}, angular=${cfg.angular.mode}@${cfg.angular.port}")
      _   <- Server.resource(cfg).useForever
    yield ()
