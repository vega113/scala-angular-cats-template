package com.example.app.http

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.implicits._
import com.example.app.auth.{
  AccountActivationService,
  AuthService,
  PasswordResetService,
  User,
  UserRepository
}
import com.example.app.config.JwtConfig
import com.example.app.auth.AccountActivationService
import com.example.app.auth.AccountActivationService.Error as ActivationError
import scala.concurrent.duration._
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
    JwtService[IO](JwtConfig(secret = Some("test-secret"), ttl = 3600.seconds)).unsafeRunSync()

  private def setupRoutes(
    passwordReset: PasswordResetService[IO] = stubPasswordResetService(),
    activationFactory: InMemoryUserRepo => AccountActivationService[IO] = _ =>
      stubActivationService()
  ): IO[(AuthRoutes, InMemoryUserRepo, AccountActivationService[IO])] =
    for
      ref <- Ref.of[IO, Map[UUID, User]](Map.empty)
      repo = new InMemoryUserRepo(ref)
      activation = activationFactory(repo)
      service = AuthService[IO](repo, passwordHasher, jwtService, activation)
    yield (new AuthRoutes(service, passwordReset, activation), repo, activation)

  test("signup requires activation and triggers email") {
    Ref.of[IO, Option[String]](None).flatMap { seenEmail =>
      val activationStub =
        stubActivationService(issueHandler = user => seenEmail.set(Some(user.email)))
      setupRoutes(activationFactory = _ => activationStub).flatMap { case (authRoutes, _, _) =>
        val request = Request[IO](POST, uri"/signup").withEntity(
          Json.obj(
            "email" -> Json.fromString("user@example.com"),
            "password" -> Json.fromString("secret")
          )
        )

        authRoutes.routes.run(request).value.flatMap {
          case Some(response) =>
            for
              _ <- IO(assertEquals(response.status, Status.Accepted))
              body <- response.as[Json]
              _ <- IO(
                assertEquals(
                  body.hcursor.downField("status").as[String],
                  Right("activation_required")
                )
              )
              email <- seenEmail.get
            yield assertEquals(email, Some("user@example.com"))
          case None => fail("No response")
        }
      }
    }
  }

  test("password reset request returns accepted and triggers notifier") {
    Ref.of[IO, Option[String]](None).flatMap { captured =>
      val stub = stubPasswordResetService { email => captured.set(Some(email)) }
      setupRoutes(passwordReset = stub).flatMap { case (authRoutes, _, _) =>
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
      val stub = stubPasswordResetService(confirmHandler =
        (token, password) => captured.update(list => (token, password) :: list)
      )
      setupRoutes(passwordReset = stub).flatMap { case (authRoutes, _, _) =>
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
    val stub = stubPasswordResetService(confirmHandler =
      (_, _) => IO.raiseError(PasswordResetService.Error.InvalidToken)
    )
    setupRoutes(passwordReset = stub).flatMap { case (authRoutes, _, _) =>
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
    val stub = stubPasswordResetService(confirmHandler =
      (_, _) => IO.raiseError(PasswordResetService.Error.PasswordTooWeak("too short"))
    )
    setupRoutes(passwordReset = stub).flatMap { case (authRoutes, _, _) =>
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
    setupRoutes().flatMap { case (authRoutes, _, _) =>
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

  test("login issues token after activation") {
    setupRoutes(activationFactory =
      repo => stubActivationService(issueHandler = user => repo.markActivated(user.id))
    ).flatMap { case (authRoutes, _, _) =>
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
    setupRoutes().flatMap { case (authRoutes, _, _) =>
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

  test("activation endpoint activates account") {
    val activatedUser = User(
      id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
      email = "activated@example.com",
      passwordHash = "hash",
      activated = true,
      createdAt = Instant.EPOCH,
      updatedAt = Instant.EPOCH
    )
    val activationStub = stubActivationService(
      issueHandler = _ => IO.unit,
      confirmHandler = token =>
        if token == "activation-token" then IO.pure(activatedUser)
        else IO.raiseError(ActivationError.InvalidToken)
    )
    setupRoutes(activationFactory = _ => activationStub).flatMap { case (authRoutes, _, _) =>
      val activateReq = Request[IO](POST, uri"/activation/confirm").withEntity(
        Json.obj("token" -> Json.fromString("activation-token"))
      )

      authRoutes.routes.run(activateReq).value.flatMap {
        case Some(response) =>
          for
            _ <- IO(assertEquals(response.status, Status.Ok))
            json <- response.as[Json]
            _ <- IO(assertEquals(json.hcursor.get[String]("token").isRight, true))
            email <- IO.fromEither(json.hcursor.downField("user").get[String]("email"))
          yield assertEquals(email, activatedUser.email)
        case None => fail("missing response")
      }
    }
  }

  test("activation endpoint handles invalid token") {
    val activationStub =
      stubActivationService(confirmHandler = _ => IO.raiseError(ActivationError.InvalidToken))
    setupRoutes(activationFactory = _ => activationStub).flatMap { case (authRoutes, _, _) =>
      val activateReq = Request[IO](POST, uri"/activation/confirm").withEntity(
        Json.obj("token" -> Json.fromString("bad-token"))
      )

      authRoutes.routes.run(activateReq).value.flatMap {
        case Some(response) =>
          for
            _ <- IO(assertEquals(response.status, Status.NotFound))
            json <- response.as[Json]
          yield assertEquals(
            json.hcursor.downField("error").downField("code").as[String],
            Right("activation_invalid")
          )
        case None => fail("missing response")
      }
    }
  }

  test("login rejects when account not activated") {
    setupRoutes().flatMap { case (authRoutes, repo, _) =>
      val email = "inactive@example.com"
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
        resp <- IO.fromOption(respOpt)(new RuntimeException("missing response"))
        _ <- IO(assertEquals(resp.status, Status.Forbidden))
        json <- resp.as[Json]
      yield assertEquals(
        json.hcursor.downField("error").downField("code").as[String],
        Right("account_not_activated")
      )
    }
  }

  test("me returns user info when token valid") {
    setupRoutes(activationFactory =
      repo => stubActivationService(issueHandler = user => repo.markActivated(user.id))
    ).flatMap { case (authRoutes, _, _) =>
      val email = "me@example.com"
      val password = "secret"
      val signupReq = Request[IO](POST, uri"/signup").withEntity(
        Json.obj("email" -> email.asJson, "password" -> password.asJson)
      )
      val loginReq = Request[IO](POST, uri"/login").withEntity(
        Json.obj("email" -> email.asJson, "password" -> password.asJson)
      )

      for
        _ <- authRoutes.routes.run(signupReq).value
        loginRespOpt <- authRoutes.routes.run(loginReq).value
        loginResp <- IO.fromOption(loginRespOpt)(new RuntimeException("missing login response"))
        loginJson <- loginResp.as[Json]
        token <- IO.fromEither(loginJson.hcursor.get[String]("token"))
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
        user = User(id, email, passwordHash, activated = false, now, now)
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
            case Some(user) =>
              users.updated(id, user.copy(passwordHash = passwordHash, updatedAt = now))
            case None => users
        }
      yield ()

    override def markActivated(id: UUID): IO[Unit] =
      for
        now <- IO.realTimeInstant
        _ <- ref.update { users =>
          users.get(id) match
            case Some(user) => users.updated(id, user.copy(activated = true, updatedAt = now))
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

      override def confirm(token: String, newPassword: String): IO[Unit] =
        confirmHandler(token, newPassword)
    }

  private def stubActivationService(
    issueHandler: User => IO[Unit] = _ => IO.unit,
    confirmHandler: String => IO[User] = _ => IO.raiseError(ActivationError.InvalidToken)
  ): AccountActivationService[IO] =
    new AccountActivationService[IO] {
      override def issueToken(user: User): IO[Unit] = issueHandler(user)

      override def activate(token: String): IO[User] = confirmHandler(token)
    }
}
