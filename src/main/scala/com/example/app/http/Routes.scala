package com.example.app.http

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import io.circe.Json

class Routes() {
  private val api: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok")))
    case GET -> Root / "ready" =>
      Ok(Json.obj("db" -> Json.fromString("unknown"), "migrations" -> Json.fromString("unknown")))
  }

  val httpApp: HttpApp[IO] = Router(
    "/" -> api
  ).orNotFound
}
