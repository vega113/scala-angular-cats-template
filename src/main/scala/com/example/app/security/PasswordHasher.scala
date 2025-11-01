package com.example.app.security

import cats.effect.Sync
import org.mindrot.jbcrypt.BCrypt

trait PasswordHasher[F[_]] {
  def hash(plain: String): F[String]
  def verify(plain: String, hash: String): F[Boolean]
}

object PasswordHasher {
  private val fallbackHash =
    "$2a$10$7EqJtq98hPqEX7fNZaFWo.QaEmbrUPO/8u6U/NhOgrn0H0m7pO5lK" // hash for "password"

  def bcrypt[F[_]: Sync](logRounds: Int = 10): PasswordHasher[F] =
    new PasswordHasher[F] {
      override def hash(plain: String): F[String] =
        Sync[F].delay(BCrypt.hashpw(plain, BCrypt.gensalt(logRounds)))

      override def verify(plain: String, hash: String): F[Boolean] =
        Sync[F].delay(BCrypt.checkpw(plain, hash))
    }

  def constantTimeFailure[F[_]: Sync](plain: String): F[Unit] =
    Sync[F].delay {
      BCrypt.checkpw(plain, fallbackHash)
      ()
    }
}
