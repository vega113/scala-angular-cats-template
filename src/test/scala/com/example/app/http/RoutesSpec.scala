package com.example.app.http

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import com.example.app.auth.{AuthResult, AuthService, User}
import com.example.app.security.jwt.JwtPayload
import java.util.UUID

class RoutesSpec extends CatsEffectSuite:
  private val stubAuthService = new AuthService[IO] {
    override def signup(email: String, password: String): IO[AuthResult] = IO.raiseError(new NotImplementedError)
    override def login(email: String, password: String): IO[AuthResult] = IO.raiseError(new NotImplementedError)
    override def currentUser(userId: UUID): IO[Option[User]] = IO.pure(None)
    override def authenticate(token: String): IO[Option[JwtPayload]] = IO.pure(None)
  }
  private val app = new Routes(new AuthRoutes(stubAuthService)).httpApp

  test("GET /health returns 200") {
    val req = Request[IO](method = Method.GET, uri = uri"/health")
    app.run(req).map { res => assertEquals(res.status, Status.Ok) }
  }
