package com.example.app.http.middleware

import cats.data.Kleisli
import cats.effect.IO
import cats.syntax.all.*
import org.http4s.*
import org.typelevel.ci.CIString
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.FiniteDuration

object LoggingMiddleware:
  private val RequestIdHeader = CIString("X-Request-Id")
  private val UserIdHeader    = CIString("X-User-Id")

  def apply(app: HttpApp[IO]): HttpApp[IO] = Kleisli { (req: Request[IO]) =>
    for
      logger   <- Slf4jLogger.create[IO]
      rid       = headerOrFallback(req.headers, RequestIdHeader)
      start    <- IO.realTime
      _        <- logger.info(
                    Map(
                      "event" -> "request.start",
                      "method" -> req.method.name,
                      "path" -> req.uri.path.renderString,
                      "requestId" -> rid
                    )
                  )(s"${req.method.name} ${req.uri}")
      res      <- app(req)
      duration <- IO.realTime.map(_ - start)
      userId    = headerOrFallback(res.headers, UserIdHeader)
      _        <- logger.info(
                    Map(
                      "event" -> "request.finish",
                      "method" -> req.method.name,
                      "path" -> req.uri.path.renderString,
                      "status" -> res.status.code.toString,
                      "requestId" -> rid,
                      "userId" -> userId,
                      "latencyMs" -> millis(duration)
                    )
                  )(s"${res.status.code} ${req.method.name} ${req.uri}")
    yield res
  }

  private def headerOrFallback(headers: Headers, header: CIString): String =
    headers.get(header).map(_.head.value).getOrElse("-")

  private def millis(d: FiniteDuration): String = d.toMillis.toString
