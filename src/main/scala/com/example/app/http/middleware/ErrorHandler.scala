package com.example.app.http.middleware

import cats.data.Kleisli
import cats.effect.IO
import com.example.app.http.{ApiError, ApiResponse}
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.typelevel.ci.CIString
import org.typelevel.log4cats.slf4j.Slf4jLogger

object ErrorHandler:
  private val RequestIdHeader = CIString("X-Request-Id")

  def apply(app: HttpApp[IO]): HttpApp[IO] = Kleisli { (req: Request[IO]) =>
    app(req).handleErrorWith { th =>
      for
        logger <- Slf4jLogger.create[IO]
        rid = req.headers.get(RequestIdHeader).map(_.head.value).getOrElse("-")
        _ <- logger.error(
          Map(
            "event" -> "request.error",
            "method" -> req.method.name,
            "path" -> req.uri.path.renderString,
            "requestId" -> rid
          ),
          th
        )("Unhandled exception in request pipeline")
        res <- ApiResponse.error(ApiError.internal("internal_error", "Internal server error"))
      yield res.putHeaders(Header.Raw(RequestIdHeader, rid))
    }
  }
