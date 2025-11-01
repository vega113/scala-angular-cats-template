package com.example.app.tracing

import cats.effect.{IO, Resource}
import com.example.app.config.TracingConfig
import natchez.EntryPoint
import natchez.noop.NoopEntrypoint
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Tracing:
  private given cats.Applicative[IO] = cats.effect.IO.asyncForIO

  private val noopEntryPoint: EntryPoint[IO] = NoopEntrypoint[IO]()

  def entryPoint(cfg: TracingConfig): Resource[IO, EntryPoint[IO]] =
    if cfg.enabled then
      Resource.eval(
        for
          logger <- Slf4jLogger.create[IO]
          _      <- logger.info("Tracing enabled (noop entry point – replace with real exporter as needed)")
        yield noopEntryPoint
      )
    else Resource.pure[IO, EntryPoint[IO]](noopEntryPoint)
