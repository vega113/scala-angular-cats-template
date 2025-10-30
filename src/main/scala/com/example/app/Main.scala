package com.example.app

import cats.effect.IOApp
import cats.effect.IO
import com.example.app.config.*

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    for
      cfg <- ConfigLoader.load
      _   <- IO.println(s"App config loaded: http=${cfg.http.port}, angular=${cfg.angular.mode}@${cfg.angular.port}")
    yield ()
