package com.example.app.http

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.implicits._
import com.example.app.auth.{AuthService, User, UserRepository}
import com.example.app.config.JwtConfig
import com.example.app.security.PasswordHasher
import com.example.app.security.jwt.{JwtPayload, JwtService}
import munit.CatsEffectSuite
import org.http4s.Method._
import org.http4s.Status
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.{Request, Response, Uri}
import io.circe.Json
import io.circe.syntax._
import java.time.Instant
import java.util.UUID

class AuthRoutesSpec extends CatsEffectSuite {
  private val passwordHasher = PasswordHasher.bcrypt[IO]()
  private val jwtService     = JwtService[IO](JwtConfig(secret = Some("test-secret"), ttl = 3600)).unsafeRunSync()

  private def setupRoutes: IO[(AuthRoutes, InMemoryUserRepo)] =
    for
      ref <- Ref.of[IO, Map[UUID, User]](Map.empty)
      repo = new InMemoryUserRepo(ref)
      service = AuthService[IO](repo, passwordHasher, jwtService)
    yield (new AuthRoutes(service), repo)

  test("signup returns token and user") {
    setupRoutes.flatMap { case (routes, _) =>
      val request = Request[IO](POST, uri"/signup").withEntity(Json.obj(
        "email" -> Json.fromString("user@example.com"),
        "password" -> Json.fromString("secret")
      ))

      routes.routes.run(request).value.flatMap {
        case Some(response) =>
          for
            _ <- IO(assertEquals(response.status, Status.Created))
            body <- response.as[Json]
          yield assert(body.hcursor.downField("token").succeeded)
        case None => fail("No response")
      }
    }
  }

  test("signup rejects duplicate email (case-insensitive)") {
    setupRoutes.flatMap { case (routes, _) =>
      val payload = Json.obj("email" -> Json.fromString("dup@example.com"), "password" -> Json.fromString("secret"))
      val request = Request[IO](POST, uri"/signup").withEntity(payload)
      val second = Request[IO](POST, uri"/signup").withEntity(Json.obj(
        "email" -> Json.fromString("DUP@EXAMPLE.COM"),
        "password" -> Json.fromString("secret")
      ))

      for
        _ <- routes.routes.run(request).value
        respOpt <- routes.routes.run(second).value
        response <- IO.fromOption(respOpt)(new RuntimeException("missing response"))
        body <- response.as[Json]
      yield {
        assertEquals(response.status, Status.Conflict)
        assertEquals(body.hcursor.downField("error").downField("code").as[String], Right("email_exists"))
      }
    }
  }

  test("login issues token") {
    setupRoutes.flatMap { case (routes, repo) =>
      val email = "login@example.com"
      val password = "secret"
      val signupReq = Request[IO](POST, uri"/signup").withEntity(Json.obj("email" -> email.asJson, "password" -> password.asJson))
      val loginReq = Request[IO](POST, uri"/login").withEntity(Json.obj("email" -> email.asJson, "password" -> password.asJson))

      for
        _ <- routes.routes.run(signupReq).value
        respOpt <- routes.routes.run(loginReq).value
        response <- IO.fromOption(respOpt)(new RuntimeException("missing response"))
        _ = assertEquals(response.status, Status.Ok)
        json <- response.as[Json]
        token <- IO.fromEither(json.hcursor.get[String]("token"))
      yield assert(token.nonEmpty)
    }
  }

  test("me returns user info when token valid") {
    setupRoutes.flatMap { case (routes, _) =>
      val email = "me@example.com"
      val password = "secret"
      val signupReq = Request[IO](POST, uri"/signup").withEntity(Json.obj("email" -> email.asJson, "password" -> password.asJson))

      for
        signupRespOpt <- routes.routes.run(signupReq).value
        signupResp <- IO.fromOption(signupRespOpt)(new RuntimeException("missing response"))
        json <- signupResp.as[Json]
        token <- IO.fromEither(json.hcursor.get[String]("token"))
        meReq = Request[IO](GET, uri"/me").putHeaders(org.http4s.headers.Authorization(org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, token)))
        meRespOpt <- routes.routes.run(meReq).value
        meResp <- IO.fromOption(meRespOpt)(new RuntimeException("missing response"))
        body <- meResp.as[Json]
      yield {
        assertEquals(meResp.status, Status.Ok)
        assertEquals(body.hcursor.downField("email").as[String], Right(email))
      }
    }
  }

  private final class InMemoryUserRepo(ref: Ref[IO, Map[UUID, User]]) extends UserRepository[IO] {
    override def create(email: String, passwordHash: String): IO[User] =
      for
        id <- IO(UUID.randomUUID())
        now <- IO.realTimeInstant
        user = User(id, email, passwordHash, now, now)
        _ <- ref.update(_ + (id -> user))
      yield user

    override def findByEmail(email: String): IO[Option[User]] =
      ref.get.map(_.values.find(_.email == email))

    override def findById(id: UUID): IO[Option[User]] =
      ref.get.map(_.get(id))
  }
}
