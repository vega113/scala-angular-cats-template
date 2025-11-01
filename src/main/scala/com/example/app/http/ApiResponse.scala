package com.example.app.http

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import org.http4s.{Response, Status}
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder

final case class ApiError(
  status: Status,
  code: String,
  message: String,
  details: Option[Json] = None
)

object ApiError:
  def badRequest(code: String, message: String, details: Option[Json] = None): ApiError =
    ApiError(Status.BadRequest, code, message, details)

  def unauthorized(code: String, message: String): ApiError =
    ApiError(Status.Unauthorized, code, message)

  def forbidden(code: String, message: String): ApiError =
    ApiError(Status.Forbidden, code, message)

  def notFound(code: String, message: String): ApiError =
    ApiError(Status.NotFound, code, message)

  def conflict(code: String, message: String): ApiError =
    ApiError(Status.Conflict, code, message)

  def unprocessableEntity(code: String, message: String, details: Option[Json] = None): ApiError =
    ApiError(Status.UnprocessableEntity, code, message, details)

  def serviceUnavailable(code: String, message: String): ApiError =
    ApiError(Status.ServiceUnavailable, code, message)

  def internal(code: String, message: String): ApiError =
    ApiError(Status.InternalServerError, code, message)

  private[http] def toJson(error: ApiError): Json =
    val base = Json.obj(
      "code" -> error.code.asJson,
      "message" -> error.message.asJson
    )
    val payload = error.details.fold(base)(d => base.deepMerge(Json.obj("details" -> d)))
    Json.obj("error" -> payload)

object ApiResponse:
  def success(status: Status, body: Json): IO[Response[IO]] =
    IO.pure(Response[IO](status = status).withEntity(body))

  def success(body: Json): IO[Response[IO]] = success(Status.Ok, body)

  def noContent: IO[Response[IO]] = IO.pure(Response[IO](Status.NoContent))

  def error(apiError: ApiError): IO[Response[IO]] =
    success(apiError.status, ApiError.toJson(apiError))
