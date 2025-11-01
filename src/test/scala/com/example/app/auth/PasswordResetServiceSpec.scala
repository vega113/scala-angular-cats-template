package com.example.app.auth

import cats.effect.{Clock, IO}
import cats.effect.kernel.Ref
import cats.syntax.all._
import com.example.app.config.{EmailConfig, PasswordResetConfig}
import com.example.app.email.EmailService
import com.example.app.security.PasswordHasher
import munit.CatsEffectSuite

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

class PasswordResetServiceSpec extends CatsEffectSuite {

  private val hasher = PasswordHasher.bcrypt[IO]()
  private val resetConfig = PasswordResetConfig(
    resetUrlBase = "https://app.example.com/reset-password",
    tokenTtl = 1.hour
  )
  private val emailConfig = EmailConfig(
    provider = "logging",
    fromAddress = Some("no-reply@example.com"),
    apiKey = None,
    resetSubject = "Reset your password"
  )

  private def service(using
    userRepo: InMemoryUserRepository,
    tokenRepo: InMemoryTokenRepository,
    emailService: RecordingEmailService
  ): PasswordResetService[IO] =
    PasswordResetService[IO](
      PasswordResetService.Dependencies[IO](
        userRepository = userRepo,
        tokenRepository = tokenRepo,
        passwordHasher = hasher,
        emailService = emailService,
        emailConfig = emailConfig,
        config = resetConfig
      )
    )

  test("request generates token and notifies user") {
    for
      usersRef <- Ref.of[IO, Map[UUID, User]](Map.empty)
      tokensRef <- Ref.of[IO, Map[UUID, PasswordResetToken]](Map.empty)
      notificationsRef <- Ref.of[IO, List[(String, String, String, String)]](Nil)
      userRepo = new InMemoryUserRepository(usersRef)
      tokenRepo = new InMemoryTokenRepository(tokensRef)
      emailService = new RecordingEmailService(notificationsRef)
      _ <- userRepo.create("reset@example.com", "hash")
      svc = service(using userRepo, tokenRepo, emailService)
      _ <- svc.request("reset@example.com")
      tokens <- tokensRef.get
      notifications <- notificationsRef.get
    yield {
      assert(tokens.values.nonEmpty)
      val stored = tokens.values.head
      assertEquals(stored.consumedAt, None)
      assert(stored.expiresAt.isAfter(Instant.now.minusSeconds(10)))
      val (to, subject, resetUrl, token) = notifications.head
      assertEquals(to, "reset@example.com")
      assert(subject.contains("Reset"))
      assert(resetUrl.contains(token))
      assertEquals(stored.tokenHash.length, 64)
    }
  }

  test("request is a no-op for unknown user") {
    for
      usersRef <- Ref.of[IO, Map[UUID, User]](Map.empty)
      tokensRef <- Ref.of[IO, Map[UUID, PasswordResetToken]](Map.empty)
      notificationsRef <- Ref.of[IO, List[(String, String, String, String)]](Nil)
      userRepo = new InMemoryUserRepository(usersRef)
      tokenRepo = new InMemoryTokenRepository(tokensRef)
      emailService = new RecordingEmailService(notificationsRef)
      svc = service(using userRepo, tokenRepo, emailService)
      _ <- svc.request("missing@example.com")
      tokens <- tokensRef.get
      notifications <- notificationsRef.get
    yield {
      assertEquals(tokens.values.size, 0)
      assertEquals(notifications, Nil)
    }
  }

  test("confirm resets password and consumes token") {
    for
      usersRef <- Ref.of[IO, Map[UUID, User]](Map.empty)
      tokensRef <- Ref.of[IO, Map[UUID, PasswordResetToken]](Map.empty)
      notificationsRef <- Ref.of[IO, List[(String, String, String, String)]](Nil)
      userRepo = new InMemoryUserRepository(usersRef)
      tokenRepo = new InMemoryTokenRepository(tokensRef)
      emailService = new RecordingEmailService(notificationsRef)
      user <- userRepo.create("reset-success@example.com", "initial")
      svc = service(using userRepo, tokenRepo, emailService)
      _ <- svc.request(user.email)
      token <- notificationsRef.get.map(_.head._4)
      _ <- svc.confirm(token, "new-password")
      updatedUser <- userRepo.findById(user.id)
      consumed <- tokensRef.get.map(_.values.forall(_.consumedAt.isDefined))
      passwordValid <- updatedUser match
        case Some(u) => hasher.verify("new-password", u.passwordHash)
        case None => IO.pure(false)
    yield {
      assert(consumed)
      assert(passwordValid)
    }
  }

  test("confirm fails when token expired") {
    for
      usersRef <- Ref.of[IO, Map[UUID, User]](Map.empty)
      tokensRef <- Ref.of[IO, Map[UUID, PasswordResetToken]](Map.empty)
      notificationsRef <- Ref.of[IO, List[(String, String, String, String)]](Nil)
      userRepo = new InMemoryUserRepository(usersRef)
      tokenRepo = new InMemoryTokenRepository(tokensRef)
      emailService = new RecordingEmailService(notificationsRef)
      user <- userRepo.create("expired@example.com", "initial")
      svc = service(using userRepo, tokenRepo, emailService)
      _ <- svc.request(user.email)
      token <- notificationsRef.get.map(_.head._4)
      _ <- tokensRef.update(_.view.mapValues(_.copy(expiresAt = Instant.EPOCH)).toMap)
      result <- svc.confirm(token, "another-pass").attempt
      stored <- tokensRef.get
    yield {
      assertEquals(result.left.toOption, Some(PasswordResetService.Error.TokenExpired))
      assert(stored.values.forall(_.consumedAt.isDefined))
    }
  }

  test("confirm fails for unknown token") {
    for
      usersRef <- Ref.of[IO, Map[UUID, User]](Map.empty)
      tokensRef <- Ref.of[IO, Map[UUID, PasswordResetToken]](Map.empty)
      notificationsRef <- Ref.of[IO, List[(String, String, String, String)]](Nil)
      userRepo = new InMemoryUserRepository(usersRef)
      tokenRepo = new InMemoryTokenRepository(tokensRef)
      emailService = new RecordingEmailService(notificationsRef)
      svc = service(using userRepo, tokenRepo, emailService)
      result <- svc.confirm("missing-token", "password123").attempt
    yield assertEquals(result.left.toOption, Some(PasswordResetService.Error.InvalidToken))
  }

  private final class InMemoryUserRepository(state: Ref[IO, Map[UUID, User]]) extends UserRepository[IO] {
    override def create(email: String, passwordHash: String): IO[User] =
      for
        id <- IO(UUID.randomUUID())
        now <- IO.realTimeInstant
        user = User(id, email, passwordHash, now, now)
        _ <- state.update(_ + (id -> user))
      yield user

    override def findByEmail(email: String): IO[Option[User]] =
      state.get.map(_.values.find(_.email == email))

    override def findById(id: UUID): IO[Option[User]] =
      state.get.map(_.get(id))

    override def updatePassword(id: UUID, passwordHash: String): IO[Unit] =
      for
        now <- IO.realTimeInstant
        _ <- state.update { users =>
          users.get(id) match
            case Some(user) => users.updated(id, user.copy(passwordHash = passwordHash, updatedAt = now))
            case None => users
        }
      yield ()
  }

  private final class InMemoryTokenRepository(state: Ref[IO, Map[UUID, PasswordResetToken]])
      extends PasswordResetTokenRepository[IO] {

    override def create(
      userId: UUID,
      tokenHash: String,
      expiresAt: Instant,
      createdAt: Instant
    ): IO[PasswordResetToken] =
      for
        id <- IO(UUID.randomUUID())
        token = PasswordResetToken(id, userId, tokenHash, expiresAt, None, createdAt)
        _ <- state.update(_ + (id -> token))
      yield token

    override def invalidateForUser(userId: UUID, at: Instant): IO[Unit] =
      state.update(_.view.mapValues { token =>
        if token.userId == userId && token.consumedAt.isEmpty then token.copy(consumedAt = Some(at))
        else token
      }.toMap)

    override def findByHash(tokenHash: String): IO[Option[PasswordResetToken]] =
      state.get.map(_.values.find(_.tokenHash == tokenHash))

    override def consume(tokenId: UUID, at: Instant): IO[Unit] =
      state.update(_.updatedWith(tokenId)(_.map(_.copy(consumedAt = Some(at)))))
  }

  private final class RecordingEmailService(ref: Ref[IO, List[(String, String, String, String)]])
      extends EmailService[IO] {
    override def sendPasswordReset(
      to: String,
      subject: String,
      resetUrl: String,
      token: String
    ): IO[Unit] =
      ref.update(entries => (to, subject, resetUrl, token) :: entries)
  }
}
