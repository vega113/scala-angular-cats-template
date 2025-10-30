package com.example.app

import cats.effect.{IO, IOApp}
import com.example.app.config.*
import com.example.app.db.{MigrationRunner, TransactorBuilder}

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    for
      cfg <- ConfigLoader.load
      _   <- IO.println(s"App config loaded: http=${cfg.http.port}, angular=${cfg.angular.mode}@${cfg.angular.port}")
      _   <- MigrationRunner.migrate(cfg)
      _   <- TransactorBuilder.optional(cfg)
                .flatMap(_ => Server.resource(cfg))
                .useForever
    yield ()
