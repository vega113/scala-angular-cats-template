package com.example.app.http.middleware

import cats.effect.IO
import com.example.app.auth.{AuthResult, AuthService, User}
import com.example.app.security.jwt.JwtPayload
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.AuthedRoutes

import java.time.Instant
import java.util.UUID

class BearerAuthMiddlewareSpec extends CatsEffectSuite {
  private val validToken = "valid-token"
  private val payload = JwtPayload(UUID.randomUUID(), "user@example.com")

  private val authService = new AuthService[IO] {
    override def signup(email: String, password: String): IO[User] =
      IO.raiseError(new NotImplementedError)
    override def login(email: String, password: String): IO[AuthResult] =
      IO.raiseError(new NotImplementedError)
    override def issueToken(user: User): IO[AuthResult] =
      IO.raiseError(new NotImplementedError)
    override def currentUser(userId: UUID): IO[Option[User]] = IO.pure(None)
    override def authenticate(token: String): IO[Option[JwtPayload]] =
      if (token == validToken) IO.pure(Some(payload)) else IO.pure(None)
  }

  private val middleware = BearerAuthMiddleware(authService)

  private val authedRoutes = AuthedRoutes.of[BearerAuthMiddleware.AuthUser, IO] {
    case GET -> Root / "protected" as user => Ok(user.email)
  }

  private val service = middleware(authedRoutes)

  test("allows request with valid token") {
    val req = Request[IO](GET, uri"/protected").putHeaders(
      headers.Authorization(Credentials.Token(AuthScheme.Bearer, validToken))
    )
    service.run(req).value.flatMap {
      case Some(resp) =>
        resp.as[String].map(body => assertEquals((resp.status, body), (Status.Ok, payload.email)))
      case None => fail("expected response")
    }
  }

  test("rejects request with invalid token") {
    val req = Request[IO](GET, uri"/protected").putHeaders(
      headers.Authorization(Credentials.Token(AuthScheme.Bearer, "bad"))
    )
    service.run(req).value.flatMap {
      case Some(resp) => IO(assertEquals(resp.status, Status.Unauthorized))
      case None => fail("expected response")
    }
  }

  test("rejects request without authorization header") {
    val req = Request[IO](GET, uri"/protected")
    service.run(req).value.flatMap {
      case Some(resp) => IO(assertEquals(resp.status, Status.Unauthorized))
      case None => fail("expected response")
    }
  }
}
