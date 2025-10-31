package com.example.app.http

import cats.effect.IO
import cats.syntax.all.*
import com.example.app.http.middleware.BearerAuthMiddleware.AuthUser
import com.example.app.todo.{FieldPatch, TodoCreate, TodoError, TodoModels, TodoService, TodoUpdate}
import com.example.app.todo.TodoModels.given
import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.{CirceEntityEncoder, jsonOf}
import org.http4s.dsl.io.*

import java.util.UUID

final class TodoRoutes(todoService: TodoService[IO]):
  import TodoRoutes.{given, *}
  import CirceEntityEncoder.given

  val authedRoutes: AuthedRoutes[AuthUser, IO] = AuthedRoutes.of:
    case req @ POST -> Root as authed =>
      handleCreate(authed, req.req)

    case GET -> Root :? CompletedFilter(completed) +& Limit(limit) +& Offset(offset) as authed =>
      val clampedLimit  = limit.getOrElse(defaultLimit)
      val clampedOffset = offset.getOrElse(defaultOffset)
      todoService
        .list(authed.userId, completed, sanitizeLimit(clampedLimit), sanitizeOffset(clampedOffset))
        .flatMap(todos => Ok(todos.asJson))

    case GET -> Root / UUIDVar(id) as authed =>
      todoService.get(authed.userId, id).flatMap:
        case Some(todo) => Ok(todo.asJson)
        case None       => NotFound(errorBody("todo_not_found", "Todo not found"))

    case req @ PUT -> Root / UUIDVar(id) as authed =>
      handleUpdate(authed.userId, id, req.req)

    case PATCH -> Root / UUIDVar(id) / "toggle" as authed =>
      toggleTodo(authed.userId, id)

    case DELETE -> Root / UUIDVar(id) as authed =>
      todoService.delete(authed.userId, id).attempt.flatMap:
        case Right(_)                  => NoContent()
        case Left(TodoError.NotFound)  => NotFound(errorBody("todo_not_found", "Todo not found"))
        case Left(other)               => InternalServerError(errorBody("todo_delete_failed", other.getMessage))

  private def handleCreate(user: AuthUser, req: Request[IO]): IO[Response[IO]] =
    req.as[TodoCreate].flatMap { body =>
      todoService.create(user.userId, body).attempt.flatMap:
        case Right(todo)                                => Created(todo.asJson)
        case Left(err: IllegalArgumentException)        => BadRequest(errorBody("validation_failed", err.getMessage))
        case Left(other)                                => InternalServerError(errorBody("todo_create_failed", other.getMessage))
    }

  private def handleUpdate(userId: UUID, id: UUID, req: Request[IO]): IO[Response[IO]] =
    req.as[TodoUpdate].flatMap { body =>
      todoService.update(userId, id, body).attempt.flatMap:
        case Right(todo)                   => Ok(todo.asJson)
        case Left(TodoError.NotFound)      => NotFound(errorBody("todo_not_found", "Todo not found"))
        case Left(err: IllegalArgumentException) =>
          BadRequest(errorBody("validation_failed", err.getMessage))
        case Left(other)                   =>
          InternalServerError(errorBody("todo_update_failed", other.getMessage))
    }

  private def toggleTodo(userId: UUID, id: UUID): IO[Response[IO]] =
    todoService.get(userId, id).flatMap:
      case Some(existing) =>
        val desired = !existing.completed
        val patch = TodoUpdate(
          title = None,
          description = FieldPatch.Unchanged,
          dueDate = FieldPatch.Unchanged,
          completed = Some(desired)
        )
        todoService.update(userId, id, patch).attempt.flatMap:
          case Right(todo)              => Ok(todo.asJson)
          case Left(TodoError.NotFound) => NotFound(errorBody("todo_not_found", "Todo not found"))
          case Left(other)              => InternalServerError(errorBody("todo_toggle_failed", other.getMessage))
      case None =>
        NotFound(errorBody("todo_not_found", "Todo not found"))

object TodoRoutes:
  import com.example.app.todo.TodoModels.given

  private val defaultLimit  = 20
  private val defaultOffset = 0

  private def sanitizeLimit(raw: Int): Int  = math.max(1, math.min(raw, 100))
  private def sanitizeOffset(raw: Int): Int = math.max(0, raw)

  given EntityDecoder[IO, TodoCreate] = jsonOf[IO, TodoCreate]
  given EntityDecoder[IO, TodoUpdate] = jsonOf[IO, TodoUpdate]

  private object CompletedFilter extends OptionalQueryParamDecoderMatcher[Boolean]("completed")
  private object Limit          extends OptionalQueryParamDecoderMatcher[Int]("limit")
  private object Offset         extends OptionalQueryParamDecoderMatcher[Int]("offset")

  private def errorBody(code: String, message: String): Json =
    Json.obj("error" -> Json.obj("code" -> code.asJson, "message" -> message.asJson))
