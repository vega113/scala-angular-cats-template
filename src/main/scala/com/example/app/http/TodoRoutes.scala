package com.example.app.http

import cats.effect.IO
import cats.syntax.all.*
import com.example.app.config.TodoConfig
import com.example.app.http.middleware.BearerAuthMiddleware.AuthUser
import com.example.app.todo.{FieldPatch, TodoCreate, TodoError, TodoModels, TodoService, TodoUpdate}
import com.example.app.todo.TodoModels.given
import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.{CirceEntityEncoder, jsonOf}
import org.http4s.dsl.io.*
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger

import java.util.UUID

final class TodoRoutes(
  todoService: TodoService[IO],
  pagination: TodoConfig
)(using logger: Logger[IO]):
  import TodoRoutes.{given, *}
  import CirceEntityEncoder.given

  val authedRoutes: AuthedRoutes[AuthUser, IO] = AuthedRoutes.of:
    case req @ POST -> Root as authed =>
      respond(authed)(handleCreate(authed, req.req))

    case GET -> Root :? CompletedFilter(completed) +& Limit(limit) +& Offset(offset) as authed =>
      val requestedLimit = limit.getOrElse(pagination.defaultPageSize)
      val sanitizedLimit = sanitizeLimit(requestedLimit)
      val sanitizedOffset = sanitizeOffset(offset.getOrElse(defaultOffset))
      respond(authed) {
        todoService
          .list(authed.userId, completed, sanitizedLimit, sanitizedOffset)
          .flatMap(todos => ApiResponse.success(todos.asJson))
      }

    case GET -> Root / UUIDVar(id) as authed =>
      respond(authed) {
        todoService
          .get(authed.userId, id)
          .flatMap:
            case Some(todo) => ApiResponse.success(todo.asJson)
            case None => ApiResponse.error(ApiError.notFound("todo_not_found", "Todo not found"))
      }

    case req @ PUT -> Root / UUIDVar(id) as authed =>
      respond(authed)(handleUpdate(authed.userId, id, req.req))

    case PATCH -> Root / UUIDVar(id) / "toggle" as authed =>
      respond(authed)(toggleTodo(authed.userId, id))

    case DELETE -> Root / UUIDVar(id) as authed =>
      respond(authed) {
        todoService
          .delete(authed.userId, id)
          .attempt
          .flatMap:
            case Right(_) => ApiResponse.noContent
            case Left(TodoError.NotFound) =>
              ApiResponse.error(ApiError.notFound("todo_not_found", "Todo not found"))
            case Left(_) =>
              ApiResponse.error(ApiError.internal("todo_delete_failed", "Unable to delete todo"))
      }

  private def handleCreate(user: AuthUser, req: Request[IO]): IO[Response[IO]] =
    req.as[TodoCreate].flatMap { body =>
      todoService
        .create(user.userId, body)
        .attempt
        .flatMap:
          case Right(todo) => ApiResponse.success(Status.Created, todo.asJson)
          case Left(err: IllegalArgumentException) =>
            ApiResponse.error(ApiError.unprocessableEntity("validation_failed", err.getMessage))
          case Left(_) =>
            ApiResponse.error(ApiError.internal("todo_create_failed", "Unable to create todo"))
    }

  private def handleUpdate(userId: UUID, id: UUID, req: Request[IO]): IO[Response[IO]] =
    req.as[TodoUpdate].flatMap { body =>
      todoService
        .update(userId, id, body)
        .attempt
        .flatMap:
          case Right(todo) => ApiResponse.success(todo.asJson)
          case Left(TodoError.NotFound) =>
            ApiResponse.error(ApiError.notFound("todo_not_found", "Todo not found"))
          case Left(err: IllegalArgumentException) =>
            ApiResponse.error(ApiError.unprocessableEntity("validation_failed", err.getMessage))
          case Left(_) =>
            ApiResponse.error(ApiError.internal("todo_update_failed", "Unable to update todo"))
    }

  private def toggleTodo(userId: UUID, id: UUID): IO[Response[IO]] =
    todoService
      .get(userId, id)
      .flatMap:
        case Some(existing) =>
          val desired = !existing.completed
          val patch = TodoUpdate(
            title = None,
            description = FieldPatch.Unchanged,
            dueDate = FieldPatch.Unchanged,
            completed = Some(desired)
          )
          todoService
            .update(userId, id, patch)
            .attempt
            .flatMap:
              case Right(todo) =>
                logger.info(s"Toggled todo ${todo.id} for user $userId to ${todo.completed}") *>
                  ApiResponse.success(todo.asJson)
              case Left(TodoError.NotFound) =>
                logger.warn(s"Toggle failed for todo $id and user $userId: not found") *>
                  ApiResponse.error(ApiError.notFound("todo_not_found", "Todo not found"))
              case Left(_) =>
                logger.error(s"Toggle failed for todo $id and user $userId") *>
                  ApiResponse.error(
                    ApiError.internal("todo_toggle_failed", "Unable to toggle todo")
                  )
        case None =>
          logger.warn(s"Toggle requested for missing todo $id and user $userId") *>
            ApiResponse.error(ApiError.notFound("todo_not_found", "Todo not found"))

  private def sanitizeLimit(raw: Int): Int =
    math.max(1, math.min(raw, pagination.maxPageSize))

  private def sanitizeOffset(raw: Int): Int =
    math.max(0, raw)

  private val userIdHeader = CIString("X-User-Id")

  private def respond[A](user: AuthUser)(response: IO[Response[IO]]): IO[Response[IO]] =
    response.map(_.putHeaders(Header.Raw(userIdHeader, user.userId.toString)))

object TodoRoutes:
  import com.example.app.todo.TodoModels.given

  private val defaultOffset = 0

  given EntityDecoder[IO, TodoCreate] = jsonOf[IO, TodoCreate]
  given EntityDecoder[IO, TodoUpdate] = jsonOf[IO, TodoUpdate]

  private object CompletedFilter extends OptionalQueryParamDecoderMatcher[Boolean]("completed")
  private object Limit extends OptionalQueryParamDecoderMatcher[Int]("limit")
  private object Offset extends OptionalQueryParamDecoderMatcher[Int]("offset")
