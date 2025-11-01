package com.example.app.http

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.implicits._
import com.example.app.auth.{AuthService, PasswordResetService, User, UserRepository}
import com.example.app.config.{EmailConfig, JwtConfig}
import com.example.app.email.EmailService
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
  private val jwtService =
    JwtService[IO](JwtConfig(secret = Some("test-secret"), ttl = 3600)).unsafeRunSync()
  private val emailConfig = EmailConfig()
  private val noopEmailService = new EmailService[IO] {
    override def sendPasswordReset(
      to: String,
      subject: String,
      resetUrl: String,
      token: String
    ): IO[Unit] = IO.unit

    override def sendActivationLink(to: String, subject: String, activationUrl: String): IO[Unit] =
      IO.unit
  }

  private def setupRoutes(passwordReset: PasswordResetService[IO] = stubPasswordResetService()): IO[(AuthRoutes, InMemoryUserRepo)] =
    for
      ref <- Ref.of[IO, Map[UUID, User]](Map.empty)
      repo = new InMemoryUserRepo(ref)
      service = AuthService[IO](repo, passwordHasher, jwtService, noopEmailService, emailConfig)
    yield (new AuthRoutes(service, passwordReset), repo)

  test("signup returns token and user") {
    setupRoutes().flatMap { case (authRoutes, _) =>
      val request = Request[IO](POST, uri"/signup").withEntity(
        Json.obj(
          "email" -> Json.fromString("user@example.com"),
          "password" -> Json.fromString("secret")
        )
      )

      authRoutes.routes.run(request).value.flatMap {
        case Some(response) =>
          for
            _ <- IO(assertEquals(response.status, Status.Created))
            body <- response.as[Json]
          yield assert(body.hcursor.downField("token").succeeded)
        case None => fail("No response")
      }
    }
  }

  test("password reset request returns accepted and triggers notifier") {
    Ref.of[IO, Option[String]](None).flatMap { captured =>
      val stub = stubPasswordResetService { email => captured.set(Some(email)) }
      setupRoutes(stub).flatMap { case (authRoutes, _) =>
        val request = Request[IO](POST, uri"/password-reset/request").withEntity(
          Json.obj("email" -> Json.fromString("reset@example.com"))
        )

        for
          responseOpt <- authRoutes.routes.run(request).value
          response <- IO.fromOption(responseOpt)(new RuntimeException("missing response"))
          _ <- IO(assertEquals(response.status, Status.Accepted))
          body <- response.as[Json]
          _ <- IO(assertEquals(body.hcursor.downField("status").as[String], Right("ok")))
          seen <- captured.get
        yield assertEquals(seen, Some("reset@example.com"))
      }
    }
  }

  test("password reset confirm succeeds") {
    Ref.of[IO, List[(String, String)]](List.empty).flatMap { captured =>
      val stub = stubPasswordResetService(confirmHandler = (token, password) =>
        captured.update(list => (token, password) :: list)
      )
      setupRoutes(stub).flatMap { case (authRoutes, _) =>
        val request = Request[IO](POST, uri"/password-reset/confirm").withEntity(
          Json.obj(
            "token" -> Json.fromString("token-123"),
            "password" -> Json.fromString("new-password")
          )
        )

        for
          respOpt <- authRoutes.routes.run(request).value
          response <- IO.fromOption(respOpt)(new RuntimeException("missing response"))
          _ <- IO(assertEquals(response.status, Status.NoContent))
          records <- captured.get
        yield assertEquals(records.headOption, Some("token-123" -> "new-password"))
      }
    }
  }

  test("password reset confirm handles invalid token") {
    val stub = stubPasswordResetService(confirmHandler = (_, _) =>
      IO.raiseError(PasswordResetService.Error.InvalidToken)
    )
    setupRoutes(stub).flatMap { case (authRoutes, _) =>
      val request = Request[IO](POST, uri"/password-reset/confirm").withEntity(
        Json.obj(
          "token" -> Json.fromString("bad-token"),
          "password" -> Json.fromString("new-password")
        )
      )

      authRoutes.routes.run(request).value.flatMap {
        case Some(response) =>
          for
            _ <- IO(assertEquals(response.status, Status.NotFound))
            json <- response.as[Json]
          yield assertEquals(
            json.hcursor.downField("error").downField("code").as[String],
            Right("password_reset_invalid")
          )
        case None => fail("missing response")
      }
    }
  }

  test("password reset confirm handles weak passwords") {
    val stub = stubPasswordResetService(confirmHandler = (_, _) =>
      IO.raiseError(PasswordResetService.Error.PasswordTooWeak("too short"))
    )
    setupRoutes(stub).flatMap { case (authRoutes, _) =>
      val request = Request[IO](POST, uri"/password-reset/confirm").withEntity(
        Json.obj(
          "token" -> Json.fromString("some-token"),
          "password" -> Json.fromString("short")
        )
      )

      authRoutes.routes.run(request).value.flatMap {
        case Some(response) =>
          for
            _ <- IO(assertEquals(response.status, Status.UnprocessableEntity))
            json <- response.as[Json]
          yield assertEquals(
            json.hcursor.downField("error").downField("code").as[String],
            Right("password_too_weak")
          )
        case None => fail("missing response")
      }
    }
  }

  test("signup rejects duplicate email (case-insensitive)") {
    setupRoutes().flatMap { case (authRoutes, _) =>
      val payload = Json.obj(
        "email" -> Json.fromString("dup@example.com"),
        "password" -> Json.fromString("secret")
      )
      val request = Request[IO](POST, uri"/signup").withEntity(payload)
      val second = Request[IO](POST, uri"/signup").withEntity(
        Json.obj(
          "email" -> Json.fromString("DUP@EXAMPLE.COM"),
          "password" -> Json.fromString("secret")
        )
      )

      for
        _ <- authRoutes.routes.run(request).value
        respOpt <- authRoutes.routes.run(second).value
        response <- IO.fromOption(respOpt)(new RuntimeException("missing response"))
        body <- response.as[Json]
      yield {
        assertEquals(response.status, Status.Conflict)
        assertEquals(
          body.hcursor.downField("error").downField("code").as[String],
          Right("email_exists")
        )
      }
    }
  }

  test("login issues token") {
    setupRoutes().flatMap { case (authRoutes, repo) =>
      val email = "login@example.com"
      val password = "secret"
      val signupReq = Request[IO](POST, uri"/signup").withEntity(
        Json.obj("email" -> email.asJson, "password" -> password.asJson)
      )
      val loginReq = Request[IO](POST, uri"/login").withEntity(
        Json.obj("email" -> email.asJson, "password" -> password.asJson)
      )

      for
        _ <- authRoutes.routes.run(signupReq).value
        respOpt <- authRoutes.routes.run(loginReq).value
        response <- IO.fromOption(respOpt)(new RuntimeException("missing response"))
        _ = assertEquals(response.status, Status.Ok)
        json <- response.as[Json]
        token <- IO.fromEither(json.hcursor.get[String]("token"))
      yield assert(token.nonEmpty)
    }
  }

  test("login rejects unknown email with generic message") {
    setupRoutes().flatMap { case (authRoutes, _) =>
      val loginReq = Request[IO](POST, uri"/login").withEntity(
        Json.obj(
          "email" -> "unknown@example.com".asJson,
          "password" -> "secret".asJson
        )
      )

      authRoutes.routes.run(loginReq).value.flatMap {
        case Some(resp) =>
          for
            _ <- IO(assertEquals(resp.status, Status.Unauthorized))
            json <- resp.as[Json]
          yield assertEquals(
            json.hcursor.downField("error").downField("code").as[String],
            Right("invalid_credentials")
          )
        case None => fail("missing response")
      }
    }
  }

  test("me returns user info when token valid") {
    setupRoutes().flatMap { case (authRoutes, _) =>
      val email = "me@example.com"
      val password = "secret"
      val signupReq = Request[IO](POST, uri"/signup").withEntity(
        Json.obj("email" -> email.asJson, "password" -> password.asJson)
      )

      for
        signupRespOpt <- authRoutes.routes.run(signupReq).value
        signupResp <- IO.fromOption(signupRespOpt)(new RuntimeException("missing response"))
        json <- signupResp.as[Json]
        token <- IO.fromEither(json.hcursor.get[String]("token"))
        meReq = Request[IO](GET, uri"/me").putHeaders(
          org.http4s.headers.Authorization(
            org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, token)
          )
        )
        meRespOpt <- authRoutes.routes.run(meReq).value
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

    override def updatePassword(id: UUID, passwordHash: String): IO[Unit] =
      for
        now <- IO.realTimeInstant
        _ <- ref.update { users =>
          users.get(id) match
            case Some(user) => users.updated(id, user.copy(passwordHash = passwordHash, updatedAt = now))
            case None => users
        }
      yield ()
  }

  private def stubPasswordResetService(
    requestHandler: String => IO[Unit] = _ => IO.unit,
    confirmHandler: (String, String) => IO[Unit] = (_, _) => IO.unit
  ): PasswordResetService[IO] =
    new PasswordResetService[IO] {
      override def request(email: String): IO[Unit] = requestHandler(email)

      override def confirm(token: String, newPassword: String): IO[Unit] = confirmHandler(token, newPassword)
    }
}
