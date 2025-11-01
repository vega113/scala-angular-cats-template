package com.example.app.http

import cats.effect._
import cats.syntax.all.*
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
  authMiddleware: AuthMiddleware[IO, AuthUser],
  readinessCheck: IO[Unit]
) {
  private val ops: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      ApiResponse.success(Json.obj("status" -> Json.fromString("ok")))
    case GET -> Root / "ready" =>
      readinessCheck.attempt.flatMap {
        case Right(_) => Ok(Json.obj("status" -> Json.fromString("ok")))
        case Left(err) =>
          val message = Option(err.getMessage).getOrElse(err.getClass.getSimpleName)
          ApiResponse.error(ApiError.serviceUnavailable("ready_check_failed", message))
      }
  }

  private val secureTodos: HttpRoutes[IO] = authMiddleware(todoRoutes.authedRoutes)

  val routes: HttpRoutes[IO] = Router(
    "/" -> ops,
    "/api/auth" -> authRoutes.routes,
    "/api/todos" -> secureTodos
  )

  val httpApp: HttpApp[IO] = routes.orNotFound
}
