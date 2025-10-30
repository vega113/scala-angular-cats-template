package com.example.app.http.middleware

import cats.data.Kleisli
import cats.effect.IO
import cats.syntax.all._
import org.http4s._
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LoggingMiddleware:
  def apply(app: HttpApp[IO]): HttpApp[IO] = Kleisli { (req: Request[IO]) =>
    for
      logger <- Slf4jLogger.create[IO]
      rid     = req.headers.get(CIString("X-Request-Id")).flatMap(_.headOption.map(_.value)).getOrElse("-")
      _      <- logger.info(s"--> ${req.method.name} ${req.uri} rid=$rid")
      res    <- app(req)
      _      <- logger.info(s"<-- ${res.status.code} ${req.method.name} ${req.uri} rid=$rid")
    yield res
  }
