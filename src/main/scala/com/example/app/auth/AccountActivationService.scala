package com.example.app.auth

import cats.effect.{Clock, Sync}
import cats.syntax.all._
import com.example.app.config.EmailConfig
import com.example.app.email.EmailService
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.typelevel.log4cats.Logger

trait AccountActivationService[F[_]] {
  def issueToken(user: User): F[Unit]
  def activate(token: String): F[User]
}

object AccountActivationService {
  sealed abstract class Error(message: String) extends RuntimeException(message)
  object Error {
    case object InvalidToken extends Error("activation token is invalid")
    case object TokenExpired extends Error("activation token has expired")
  }

  final case class Config(tokenTtl: scala.concurrent.duration.FiniteDuration)

  final case class Dependencies[F[_]](
    userRepository: UserRepository[F],
    tokenRepository: AccountActivationTokenRepository[F],
    emailService: EmailService[F],
    emailConfig: EmailConfig,
    config: Config
  )

  def apply[F[_]: Sync: Clock: Logger](deps: Dependencies[F]): AccountActivationService[F] =
    new AccountActivationService[F] {
      private val F = Sync[F]
      private val random = SecureRandom()
      private val hexFormat = java.util.HexFormat.of()

      override def issueToken(user: User): F[Unit] =
        for
          now <- Clock[F].realTimeInstant
          _ <- deps.tokenRepository.invalidateForUser(user.id, now)
          token <- generateToken
          hash = hashToken(token)
          expires = now.plus(deps.config.tokenTtl.toMillis, ChronoUnit.MILLIS)
          _ <- deps.tokenRepository.create(user.id, hash, expires, now)
          activationUrl = buildActivationUrl(token, user.email)
          _ <- deps.emailService
            .sendActivationLink(user.email, deps.emailConfig.activationSubject, activationUrl)
            .handleErrorWith(err => Logger[F].warn(err)("Failed to send activation email"))
        yield ()

      override def activate(token: String): F[User] =
        val trimmed = token.trim
        if trimmed.isEmpty then F.raiseError[User](Error.InvalidToken)
        else
          for
            maybeToken <- deps.tokenRepository.findByHash(hashToken(trimmed))
            now <- Clock[F].realTimeInstant
            activation <- maybeToken match
              case Some(record) if record.consumedAt.isDefined =>
                F.raiseError[AccountActivationToken](Error.InvalidToken)
              case Some(record) if record.expiresAt.isBefore(now) =>
                deps.tokenRepository.consume(record.id, now) *> F
                  .raiseError[AccountActivationToken](Error.TokenExpired)
              case Some(record) => F.pure(record)
              case None => F.raiseError[AccountActivationToken](Error.InvalidToken)
            _ <- deps.userRepository.markActivated(activation.userId)
            _ <- deps.tokenRepository.consume(activation.id, now)
            _ <- deps.tokenRepository.invalidateForUser(activation.userId, now)
            maybeUser <- deps.userRepository.findById(activation.userId)
            user <- maybeUser match
              case Some(value) => F.pure(value)
              case None => F.raiseError[User](Error.InvalidToken)
          yield user

      private def generateToken: F[String] =
        F.delay {
          val bytes = new Array[Byte](32)
          random.nextBytes(bytes)
          hexFormat.formatHex(bytes)
        }

      private def hashToken(token: String): String =
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(token.getBytes("UTF-8"))
        hexFormat.formatHex(bytes)

      private def buildActivationUrl(token: String, email: String): String =
        val base = deps.emailConfig.activationUrlBase
        val separator = if base.contains('?') then "&" else "?"
        s"$base${separator}token=$token&email=${urlEncode(email)}"

      private def urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
    }
}
