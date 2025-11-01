package com.example.app.auth

import cats.effect.{Clock, Sync}
import cats.syntax.all._
import com.example.app.config.{EmailConfig, PasswordResetConfig}
import com.example.app.security.PasswordHasher
import com.example.app.email.EmailService
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.http4s.Uri

trait PasswordResetService[F[_]] {
  def request(email: String): F[Unit]
  def confirm(token: String, newPassword: String): F[Unit]
}

object PasswordResetService {
  sealed abstract class Error(message: String) extends RuntimeException(message)
  object Error {
    case object InvalidToken extends Error("password reset token is invalid")
    case object TokenExpired extends Error("password reset token has expired")
    final case class PasswordTooWeak(message: String) extends Error(message)
  }

  final case class Dependencies[F[_]](
    userRepository: UserRepository[F],
    tokenRepository: PasswordResetTokenRepository[F],
    passwordHasher: PasswordHasher[F],
    emailService: EmailService[F],
    emailConfig: EmailConfig,
    config: PasswordResetConfig
  )

  def apply[F[_]: Sync: Clock](
    deps: Dependencies[F]
  ): PasswordResetService[F] =
    new PasswordResetService[F] {
      private val F = Sync[F]
      private val random = SecureRandom()
      private val hexFormat = java.util.HexFormat.of()

      override def request(email: String): F[Unit] =
        val normalized = normalizeEmail(email)
        deps.userRepository.findByEmail(normalized).flatMap {
          case Some(user) =>
            for
              now <- Clock[F].realTimeInstant
              _ <- deps.tokenRepository.invalidateForUser(user.id, now)
              token <- generateToken
              tokenHash = hashToken(token)
              expiresAt = now.plus(deps.config.tokenTtl.toMillis, ChronoUnit.MILLIS)
              _ <- deps.tokenRepository.create(user.id, tokenHash, expiresAt, now)
              resetUrl <- buildResetUrl(token)
              _ <- deps.emailService.sendPasswordReset(
                to = user.email,
                subject = deps.emailConfig.resetSubject,
                resetUrl = resetUrl,
                token = token
              )
            yield ()
         case None =>
            // Avoid disclosing whether a user exists.
            F.unit
        }

      override def confirm(token: String, newPassword: String): F[Unit] =
        val trimmedToken = token.trim
        if trimmedToken.isEmpty then F.raiseError(Error.InvalidToken)
        else
          for
            maybeToken <- deps.tokenRepository.findByHash(hashToken(trimmedToken))
            now <- Clock[F].realTimeInstant
            resetToken <- maybeToken match
              case Some(record) if record.consumedAt.isDefined =>
                F.raiseError[PasswordResetToken](Error.InvalidToken)
              case Some(record) if record.expiresAt.isBefore(now) =>
                deps.tokenRepository.consume(record.id, now) *> F
                  .raiseError[PasswordResetToken](Error.TokenExpired)
              case Some(record) =>
                F.pure(record)
              case None =>
                F.raiseError[PasswordResetToken](Error.InvalidToken)
            _ <- validatePassword(newPassword)
            hashed <- deps.passwordHasher.hash(newPassword)
            _ <- deps.userRepository.updatePassword(resetToken.userId, hashed)
            _ <- deps.tokenRepository.consume(resetToken.id, now)
            _ <- deps.tokenRepository.invalidateForUser(resetToken.userId, now)
          yield ()

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

      private def normalizeEmail(value: String): String =
        value.trim.toLowerCase

      private def buildResetUrl(token: String): F[String] =
        F.fromEither(Uri.fromString(deps.config.resetUrlBase)).map(_.withQueryParam("token", token).renderString)

      private def validatePassword(password: String): F[Unit] =
        val trimmed = password.trim
        if trimmed.length >= 8 then F.unit
        else F.raiseError(Error.PasswordTooWeak("password must be at least 8 characters long"))
    }
}
