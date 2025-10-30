package com.example.app.http.middleware

import cats.data.Kleisli
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import io.circe.Json
import org.http4s.circe.CirceEntityEncoder._

object ErrorHandler:
  def apply(app: HttpApp[IO]): HttpApp[IO] = Kleisli { (req: Request[IO]) =>
    app(req).handleErrorWith { th =>
      val body = Json.obj(
        "error" -> Json.obj(
          "code" -> Json.fromString("internal_error"),
          "message" -> Json.fromString(Option(th.getMessage).getOrElse("unexpected error"))
        )
      )
      IO.pure(Response[IO](status = Status.InternalServerError).withEntity(body))
    }
  }
