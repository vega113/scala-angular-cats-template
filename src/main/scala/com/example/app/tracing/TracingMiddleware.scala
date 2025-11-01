package com.example.app.tracing

import cats.data.Kleisli
import cats.effect.IO
import natchez.EntryPoint
import org.http4s.{HttpApp, Request}
import org.typelevel.ci.CIString

object TracingMiddleware:
  private val RequestIdHeader = CIString("X-Request-Id")

  def apply(entryPoint: EntryPoint[IO], enabled: Boolean)(app: HttpApp[IO]): HttpApp[IO] =
    if !enabled then app
    else
      Kleisli { (req: Request[IO]) =>
        val spanName = s"${req.method.name} ${req.uri.path.renderString}"
        entryPoint.root(spanName).use { span =>
          val requestId = req.headers.get(RequestIdHeader).map(_.head.value).getOrElse("-")
          span.put(
            "http.method" -> req.method.name,
            "http.path" -> req.uri.path.renderString,
            "request.id" -> requestId
          ) *> app(req)
        }
      }
end TracingMiddleware
