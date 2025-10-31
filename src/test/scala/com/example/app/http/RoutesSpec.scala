package com.example.app.http

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import com.example.app.auth.{AuthResult, AuthService, User}
import com.example.app.http.middleware.BearerAuthMiddleware.AuthUser
import com.example.app.security.jwt.JwtPayload
import com.example.app.todo.{Todo, TodoCreate, TodoService, TodoUpdate}
import org.http4s.server.AuthMiddleware
import java.util.UUID

class RoutesSpec extends CatsEffectSuite:
  private val stubAuthService = new AuthService[IO] {
    override def signup(email: String, password: String): IO[AuthResult] = IO.raiseError(new NotImplementedError)
    override def login(email: String, password: String): IO[AuthResult] = IO.raiseError(new NotImplementedError)
    override def currentUser(userId: UUID): IO[Option[User]] = IO.pure(None)
    override def authenticate(token: String): IO[Option[JwtPayload]] = IO.pure(None)
  }
  private val stubTodoService = new TodoService[IO] {
    private def fail[A]: IO[A] = IO.raiseError(new NotImplementedError)
    override def create(userId: UUID, input: TodoCreate): IO[Todo] = fail
    override def list(userId: UUID, completed: Option[Boolean], limit: Int, offset: Int): IO[List[Todo]] = fail
    override def get(userId: UUID, id: UUID): IO[Option[Todo]] = fail
    override def update(userId: UUID, id: UUID, update: TodoUpdate): IO[Todo] = fail
    override def delete(userId: UUID, id: UUID): IO[Unit] = fail
  }
  private val todoRoutes = new TodoRoutes(stubTodoService)
  private val noopAuth: AuthMiddleware[IO, AuthUser] = AuthMiddleware(Kleisli(_ => OptionT.none[IO, AuthUser]))
  private val app = new Routes(new AuthRoutes(stubAuthService), todoRoutes, noopAuth).httpApp

  test("GET /health returns 200") {
    val req = Request[IO](method = Method.GET, uri = uri"/health")
    app.run(req).map { res => assertEquals(res.status, Status.Ok) }
  }
