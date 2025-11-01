package com.example.app.auth

import cats.effect.Async
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import java.time.Instant
import java.util.UUID

trait PasswordResetTokenRepository[F[_]] {
  def create(
    userId: UUID,
    tokenHash: String,
    expiresAt: Instant,
    createdAt: Instant
  ): F[PasswordResetToken]

  def invalidateForUser(userId: UUID, at: Instant): F[Unit]

  def findByHash(tokenHash: String): F[Option[PasswordResetToken]]

  def consume(tokenId: UUID, at: Instant): F[Unit]
}

object PasswordResetTokenRepository {
  def doobie[F[_]: Async](xa: Transactor[F]): PasswordResetTokenRepository[F] =
    new DoobiePasswordResetTokenRepository[F](xa)

  private final class DoobiePasswordResetTokenRepository[F[_]: Async](xa: Transactor[F])
      extends PasswordResetTokenRepository[F] {
    private def nowUuid: F[UUID] = Async[F].delay(UUID.randomUUID())

    override def create(
      userId: UUID,
      tokenHash: String,
      expiresAt: Instant,
      createdAt: Instant
    ): F[PasswordResetToken] =
      for
        id <- nowUuid
        _ <-
          sql"""INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at, created_at)
                VALUES ($id, $userId, $tokenHash, $expiresAt, $createdAt)
             """.update.run.transact(xa)
      yield PasswordResetToken(id, userId, tokenHash, expiresAt, None, createdAt)

    override def invalidateForUser(userId: UUID, at: Instant): F[Unit] =
      sql"""UPDATE password_reset_tokens
            SET consumed_at = $at
            WHERE user_id = $userId AND consumed_at IS NULL
         """.update.run.transact(xa).void

    override def findByHash(tokenHash: String): F[Option[PasswordResetToken]] =
      sql"""SELECT id, user_id, token_hash, expires_at, consumed_at, created_at
            FROM password_reset_tokens
            WHERE token_hash = $tokenHash
         """.query[(UUID, UUID, String, Instant, Option[Instant], Instant)]
        .map { case (id, userId, hash, expires, consumed, created) =>
          PasswordResetToken(id, userId, hash, expires, consumed, created)
        }
        .option
        .transact(xa)

    override def consume(tokenId: UUID, at: Instant): F[Unit] =
      sql"""UPDATE password_reset_tokens
            SET consumed_at = $at
            WHERE id = $tokenId
         """.update.run.transact(xa).void
  }
}
