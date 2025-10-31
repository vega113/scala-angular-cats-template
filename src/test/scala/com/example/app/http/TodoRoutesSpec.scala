package com.example.app.http

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import com.example.app.http.middleware.BearerAuthMiddleware.AuthUser
import com.example.app.todo.{FieldPatch, Todo, TodoCreate, TodoRepository, TodoService, TodoUpdate}
import com.example.app.todo.TodoModels.given
import io.circe.Json
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.Method.*
import org.http4s.circe.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.circe.CirceEntityDecoder.given
import org.http4s.circe.CirceEntityEncoder.given
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.{AuthMiddleware, Router}

import java.time.Instant
import java.util.UUID

class TodoRoutesSpec extends CatsEffectSuite:
  private val user = AuthUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "user@example.com")
  private val baseUri = uri"/api/todos"
  private type OptionTIO[A] = OptionT[IO, A]

  test("create todo returns payload with defaults"):
    withApp { (app, _) =>
      for
        resp <- app.run(Request[IO](POST, baseUri).withEntity(Json.obj("title" -> "Task".asJson)))
        _ = assertEquals(resp.status, Status.Created)
        todo <- resp.as[Todo]
      yield
        assertEquals(todo.title, "Task")
        assertEquals(todo.completed, false)
    }

  test("list supports completed filter and pagination clamping"):
    withApp { (app, _) =>
      for
        created1 <- createTodo(app, "One")
        _        <- createTodo(app, "Two")
        updateResp <- updateTodo(app, created1.id, Json.obj("completed" -> Json.True))
        _ = assertEquals(updateResp.status, Status.Ok)
        listed   <- app.run(Request[IO](GET, uri"/api/todos?completed=true&limit=999&offset=-5"))
        _ = assertEquals(listed.status, Status.Ok)
        todos <- listed.as[List[Todo]]
      yield
        assertEquals(todos.map(_.id), List(created1.id))
        assertEquals(todos.head.completed, true)
        assertEquals(todos.head.title, "One")
    }

  test("update can clear optional fields via null"):
    withApp { (app, _) =>
      for
        created <- createTodo(app, "With Desc", description = Some("desc"))
        resp    <- updateTodo(app, created.id, Json.obj("description" -> Json.Null))
        _ = assertEquals(resp.status, Status.Ok)
        updated <- resp.as[Todo]
      yield assertEquals(updated.description, None)
    }

  test("toggle flips completion state"):
    withApp { (app, _) =>
      for
        created  <- createTodo(app, "Toggle me")
        toggled1 <- app.run(Request[IO](PATCH, baseUri / created.id.toString / "toggle"))
        _ = assertEquals(toggled1.status, Status.Ok)
        todo1 <- toggled1.as[Todo]
        toggled2 <- app.run(Request[IO](PATCH, baseUri / created.id.toString / "toggle"))
        _ = assertEquals(toggled2.status, Status.Ok)
        todo2 <- toggled2.as[Todo]
      yield
        assertEquals(todo1.completed, true)
        assertEquals(todo2.completed, false)
    }

  test("delete removes todo and returns 204"):
    withApp { (app, _) =>
      for
        created <- createTodo(app, "Delete me")
        deleteResp <- app.run(Request[IO](DELETE, baseUri / created.id.toString))
        _ = assertEquals(deleteResp.status, Status.NoContent)
        getResp <- app.run(Request[IO](GET, baseUri / created.id.toString))
      yield assertEquals(getResp.status, Status.NotFound)
    }

  test("create rejects blank titles"):
    withApp { (app, _) =>
      for
        resp <- app.run(Request[IO](POST, baseUri).withEntity(Json.obj("title" -> "  ".asJson)))
        _ = assertEquals(resp.status, Status.BadRequest)
        body <- resp.as[Json]
      yield assertEquals(errorCode(body), Some("validation_failed"))
    }

  test("get returns 404 for missing todo"):
    withApp { (app, _) =>
      val unknownId = UUID.fromString("00000000-0000-0000-0000-000000001234")
      for
        resp <- app.run(Request[IO](GET, baseUri / unknownId.toString))
        _ = assertEquals(resp.status, Status.NotFound)
        body <- resp.as[Json]
      yield assertEquals(errorCode(body), Some("todo_not_found"))
    }

  private def withApp[A](f: (HttpApp[IO], Ref[IO, Map[UUID, Todo]]) => IO[A]): IO[A] =
    Ref.of[IO, Map[UUID, Todo]](Map.empty).flatMap { ref =>
      val repo       = new InMemoryTodoRepository(ref)
      val service    = TodoService[IO](repo)
      val routes     = new TodoRoutes(service)
      val authenticator = Kleisli[OptionTIO, Request[IO], AuthUser](_ => OptionT.pure[IO](user))
      val middleware = AuthMiddleware(authenticator)
      val app = Router("/api/todos" -> middleware(routes.authedRoutes)).orNotFound
      f(app, ref)
    }

  private def createTodo(app: HttpApp[IO], title: String, description: Option[String] = None): IO[Todo] =
    val baseFields = List("title" -> title.asJson)
    val fields = description.fold(baseFields)(desc => baseFields :+ ("description" -> desc.asJson))
    for
      resp <- app.run(Request[IO](POST, baseUri).withEntity(Json.obj(fields*)))
      _ = assertEquals(resp.status, Status.Created)
      todo <- resp.as[Todo]
    yield todo

  private def updateTodo(app: HttpApp[IO], id: UUID, json: Json): IO[Response[IO]] =
    app.run(Request[IO](PUT, baseUri / id.toString).withEntity(json))

  private def errorCode(json: Json): Option[String] =
    json.hcursor.downField("error").get[String]("code").toOption

  private final class InMemoryTodoRepository(ref: Ref[IO, Map[UUID, Todo]]) extends TodoRepository[IO]:
    override def create(userId: UUID, create: TodoCreate): IO[Todo] =
      for
        id  <- IO(UUID.randomUUID())
        now <- IO.realTimeInstant
        todo = Todo(id, userId, create.title, create.description, create.dueDate, completed = false, createdAt = now, updatedAt = now)
        _   <- ref.update(_ + (id -> todo))
      yield todo

    override def get(userId: UUID, id: UUID): IO[Option[Todo]] =
      ref.get.map(_.get(id).filter(_.userId == userId))

    override def list(userId: UUID, completed: Option[Boolean], limit: Int, offset: Int): IO[List[Todo]] =
      ref.get.map { state =>
        val filtered = state.values.filter(_.userId == userId).toList
        val byStatus = completed match
          case Some(flag) => filtered.filter(_.completed == flag)
          case None       => filtered
        byStatus.sortBy(_.createdAt)(Ordering[Instant].reverse).drop(offset).take(limit)
      }

    override def update(userId: UUID, id: UUID, update: TodoUpdate): IO[Option[Todo]] =
      for
        now <- IO.realTimeInstant
        result <- ref.modify { state =>
          state.get(id) match
            case Some(existing) if existing.userId == userId =>
              val next = existing.copy(
                title = update.title.getOrElse(existing.title),
                description = update.description match
                  case FieldPatch.Unchanged => existing.description
                  case FieldPatch.Set(value) => Some(value)
                  case FieldPatch.Clear      => None,
                dueDate = update.dueDate match
                  case FieldPatch.Unchanged => existing.dueDate
                  case FieldPatch.Set(value) => Some(value)
                  case FieldPatch.Clear      => None,
                completed = update.completed.getOrElse(existing.completed),
                updatedAt = now
              )
              (state.updated(id, next), Some(next))
            case _ =>
              (state, None)
        }
      yield result

    override def delete(userId: UUID, id: UUID): IO[Boolean] =
      ref.modify { state =>
        state.get(id) match
          case Some(todo) if todo.userId == userId =>
            (state - id, true)
          case _ =>
            (state, false)
      }
