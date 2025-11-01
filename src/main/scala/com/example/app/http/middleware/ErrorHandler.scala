package com.example.app.http.middleware

import cats.data.Kleisli
import cats.effect.IO
import io.circe.Json
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
        rid     = req.headers.get(RequestIdHeader).map(_.head.value).getOrElse("-")
        _      <- logger.error(
                    Map(
                      "event" -> "request.error",
                      "method" -> req.method.name,
                      "path" -> req.uri.path.renderString,
                      "requestId" -> rid
                    ),
                    th
                  )("Unhandled exception in request pipeline")
        body    = Json.obj(
                    "error" -> Json.obj(
                      "code" -> Json.fromString("internal_error"),
                      "message" -> Json.fromString("Internal server error"),
                      "ref" -> Json.fromString(rid)
                    )
                  )
      yield Response[IO](status = Status.InternalServerError)
        .withEntity(body)
        .putHeaders(Header.Raw(RequestIdHeader, rid))
    }
  }
