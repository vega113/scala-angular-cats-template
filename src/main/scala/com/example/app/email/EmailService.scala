package com.example.app.email

import cats.effect.Sync
import cats.syntax.all._
import com.example.app.config.EmailConfig
import org.typelevel.log4cats.Logger

trait EmailService[F[_]] {
  def sendPasswordReset(to: String, subject: String, resetUrl: String, token: String): F[Unit]
  def sendActivationLink(to: String, subject: String, activationUrl: String): F[Unit]
}

object EmailService {
  def logging[F[_]: Sync: Logger]: EmailService[F] = new EmailService[F] {
    override def sendPasswordReset(
      to: String,
      subject: String,
      resetUrl: String,
      token: String
    ): F[Unit] =
      Logger[F].info(
        s"""Sending password reset email
           | To: $to
           | Subject: $subject
           | Reset URL: $resetUrl
           | Token: $token
           |""".stripMargin
      )

    override def sendActivationLink(
      to: String,
      subject: String,
      activationUrl: String
    ): F[Unit] =
      Logger[F].info(
        s"""Sending account activation email
           | To: $to
           | Subject: $subject
           | Activation URL: $activationUrl
           |""".stripMargin
      )
  }

  /** Placeholder external provider that logs a warning if invoked without configuration. */
  def external[F[_]: Sync: Logger](config: EmailConfig): EmailService[F] = new EmailService[F] {
    override def sendPasswordReset(
      to: String,
      subject: String,
      resetUrl: String,
      token: String
    ): F[Unit] =
      Logger[F].warn(
        s"""Email provider '${config.provider}' not yet implemented.
           | Fallback: no email sent to $to with subject '$subject' and reset url $resetUrl.
           | Configure a supported provider to enable delivery.
           |""".stripMargin
      )

    override def sendActivationLink(
      to: String,
      subject: String,
      activationUrl: String
    ): F[Unit] =
      Logger[F].warn(
        s"""Email provider '${config.provider}' not yet implemented.
           | Fallback: no email sent to $to with subject '$subject' and activation url $activationUrl.
           | Configure a supported provider to enable delivery.
           |""".stripMargin
      )
  }

  def fromConfig[F[_]: Sync: Logger](config: EmailConfig): EmailService[F] =
    config.provider.toLowerCase match
      case "logging" => logging[F]
      case _ => external[F](config)
}
