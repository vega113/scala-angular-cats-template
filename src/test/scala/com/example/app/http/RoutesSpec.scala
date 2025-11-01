package com.example.app.http

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import cats.syntax.all.*
import com.example.app.auth.{AuthResult, AuthService, PasswordResetService, User}
import com.example.app.config.TodoConfig
import com.example.app.http.middleware.BearerAuthMiddleware.AuthUser
import com.example.app.security.jwt.JwtPayload
import com.example.app.todo.{Todo, TodoCreate, TodoModels, TodoService, TodoUpdate}
import com.example.app.todo.TodoModels.given
import io.circe.Json
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.given
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.AuthMiddleware
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import java.time.Instant
import java.util.UUID

class RoutesSpec extends CatsEffectSuite:
  private given Logger[IO] = NoOpLogger[IO]

  private val stubAuthService = new AuthService[IO] {
    override def signup(email: String, password: String): IO[AuthResult] =
      IO.raiseError(new NotImplementedError)
    override def login(email: String, password: String): IO[AuthResult] =
      IO.raiseError(new NotImplementedError)
    override def currentUser(userId: UUID): IO[Option[User]] = IO.pure(None)
    override def authenticate(token: String): IO[Option[JwtPayload]] = IO.pure(None)
  }

  private val stubPasswordResetService = new PasswordResetService[IO] {
    override def request(email: String): IO[Unit] = IO.unit

    override def confirm(token: String, newPassword: String): IO[Unit] = IO.unit
  }

  private val sampleTodo = Todo(
    id = UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
    userId = UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
    title = "Sample",
    description = Some("placeholder"),
    dueDate = None,
    completed = false,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH
  )

  private val stubTodoService = new TodoService[IO] {
    override def create(userId: UUID, input: TodoCreate): IO[Todo] =
      IO.pure(sampleTodo.copy(id = UUID.randomUUID(), userId = userId, title = input.title))
    override def list(
      userId: UUID,
      completed: Option[Boolean],
      limit: Int,
      offset: Int
    ): IO[List[Todo]] =
      IO.pure(List(sampleTodo.copy(userId = userId)))
    override def get(userId: UUID, id: UUID): IO[Option[Todo]] =
      IO.pure(Some(sampleTodo.copy(userId = userId, id = id)))
    override def update(userId: UUID, id: UUID, update: TodoUpdate): IO[Todo] =
      IO.pure(
        sampleTodo.copy(userId = userId, id = id, title = update.title.getOrElse(sampleTodo.title))
      )
    override def delete(userId: UUID, id: UUID): IO[Unit] = IO.unit
  }

  private val todoRoutes =
    new TodoRoutes(stubTodoService, TodoConfig(defaultPageSize = 20, maxPageSize = 100))

  private def makeApp(
    authMiddleware: AuthMiddleware[IO, AuthUser],
    readiness: IO[Unit] = IO.unit
  ): HttpApp[IO] =
    new Routes(new AuthRoutes(stubAuthService, stubPasswordResetService), todoRoutes, authMiddleware, readiness).httpApp

  private val unauthenticatedMiddleware: AuthMiddleware[IO, AuthUser] =
    AuthMiddleware(Kleisli(_ => OptionT.none[IO, AuthUser]))

  private val authenticatedUser =
    AuthUser(UUID.fromString("00000000-0000-0000-0000-0000000000cc"), "tester@example.com")

  private val authenticatedMiddleware: AuthMiddleware[IO, AuthUser] =
    AuthMiddleware(Kleisli(_ => OptionT.pure[IO](authenticatedUser)))

  test("GET /health returns 200 with ok status") {
    val app = makeApp(unauthenticatedMiddleware)
    val req = Request[IO](Method.GET, uri"/health")
    for
      res <- app.run(req)
      body <- res.as[Json]
    yield {
      assertEquals(res.status, Status.Ok)
      assertEquals(body.hcursor.get[String]("status"), Right("ok"))
    }
  }

  test("GET /ready returns 200 when readiness succeeds") {
    val app = makeApp(unauthenticatedMiddleware, readiness = IO.unit)
    val req = Request[IO](Method.GET, uri"/ready")
    for
      res <- app.run(req)
      body <- res.as[Json]
    yield {
      assertEquals(res.status, Status.Ok)
      assertEquals(body.hcursor.get[String]("status"), Right("ok"))
    }
  }

  test("GET /ready returns 503 when readiness fails") {
    val failure = IO.raiseError[Unit](new RuntimeException("db down"))
    val app = makeApp(unauthenticatedMiddleware, readiness = failure)
    val req = Request[IO](Method.GET, uri"/ready")
    for
      res <- app.run(req)
      body <- res.as[Json]
    yield {
      assertEquals(res.status, Status.ServiceUnavailable)
      assertEquals(body.hcursor.downField("error").get[String]("code"), Right("ready_check_failed"))
      assertEquals(body.hcursor.downField("error").get[String]("message"), Right("db down"))
    }
  }

  test("GET /api/todos requires authentication") {
    val app = makeApp(unauthenticatedMiddleware)
    val req = Request[IO](Method.GET, uri"/api/todos")
    app.run(req).map(res => assertEquals(res.status, Status.Unauthorized))
  }

  test("GET /api/todos returns todos when authenticated") {
    val app = makeApp(authenticatedMiddleware)
    val req = Request[IO](Method.GET, uri"/api/todos")
    for
      res <- app.run(req)
      todos <- res.as[List[Todo]]
    yield {
      assertEquals(res.status, Status.Ok)
      assertEquals(todos.map(_.title), List("Sample"))
    }
  }
