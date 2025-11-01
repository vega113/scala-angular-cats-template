package com.example.app.email

import cats.effect.Sync
import cats.syntax.all._
import com.example.app.config.EmailConfig
import org.typelevel.log4cats.Logger

trait EmailService[F[_]] {
  def sendPasswordReset(to: String, subject: String, resetUrl: String, token: String): F[Unit]
}

object EmailService {
  def logging[F[_]: Sync: Logger]: EmailService[F] =
    (to, subject, resetUrl, token) =>
      Logger[F].info(
        s"""Sending password reset email
           | To: $to
           | Subject: $subject
           | Reset URL: $resetUrl
           | Token: $token
           |""".stripMargin
      )

  /** Placeholder external provider that logs a warning if invoked without configuration. */
  def external[F[_]: Sync: Logger](config: EmailConfig): EmailService[F] =
    (to, subject, resetUrl, _) =>
      Logger[F].warn(
        s"""Email provider '${config.provider}' not yet implemented.
           | Fallback: no email sent to $to with subject '$subject' and reset url $resetUrl.
           | Configure a supported provider to enable delivery.
           |""".stripMargin
      )

  def fromConfig[F[_]: Sync: Logger](config: EmailConfig): EmailService[F] =
    config.provider.toLowerCase match
      case "logging" => logging[F]
      case _ => external[F](config)
}
