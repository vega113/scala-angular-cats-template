package com.example.app.http.middleware

import cats.data.Kleisli
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.typelevel.ci.CIString
import java.util.UUID

object RequestIdMiddleware:
  private val HeaderName = CIString("X-Request-Id")

  def apply(app: HttpApp[IO]): HttpApp[IO] =
    Kleisli { (req: Request[IO]) =>
      val reqId = req.headers.get(HeaderName).map(_.head.value).getOrElse(UUID.randomUUID().toString)
      val reqWithId = req.putHeaders(Header.Raw(HeaderName, reqId))
      app(reqWithId).map(_.putHeaders(Header.Raw(HeaderName, reqId)))
    }
