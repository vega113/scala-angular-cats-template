package com.example.app.http

import cats.effect._
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.{AuthMiddleware, Router}

import com.example.app.http.middleware.BearerAuthMiddleware.AuthUser

class Routes(
    authRoutes: AuthRoutes,
    todoRoutes: TodoRoutes,
    authMiddleware: AuthMiddleware[IO, AuthUser]
) {
  private val ops: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok")))
    case GET -> Root / "ready" =>
      Ok(Json.obj("db" -> Json.fromString("unknown"), "migrations" -> Json.fromString("unknown")))
  }

  private val secureTodos: HttpRoutes[IO] = authMiddleware(todoRoutes.authedRoutes)

  val httpApp: HttpApp[IO] = Router(
    "/" -> ops,
    "/api/auth" -> authRoutes.routes,
    "/api/todos" -> secureTodos
  ).orNotFound
}
