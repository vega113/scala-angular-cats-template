package com.example.app.auth

import cats.effect.Async
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import java.time.Instant
import java.util.UUID

trait AccountActivationTokenRepository[F[_]] {
  def create(
    userId: UUID,
    tokenHash: String,
    expiresAt: Instant,
    createdAt: Instant
  ): F[AccountActivationToken]

  def findByHash(tokenHash: String): F[Option[AccountActivationToken]]

  def consume(tokenId: UUID, at: Instant): F[Unit]

  def invalidateForUser(userId: UUID, at: Instant): F[Unit]
}

object AccountActivationTokenRepository {
  def doobie[F[_]: Async](xa: Transactor[F]): AccountActivationTokenRepository[F] =
    new DoobieAccountActivationTokenRepository[F](xa)

  private final class DoobieAccountActivationTokenRepository[F[_]: Async](xa: Transactor[F])
      extends AccountActivationTokenRepository[F] {

    override def create(
      userId: UUID,
      tokenHash: String,
      expiresAt: Instant,
      createdAt: Instant
    ): F[AccountActivationToken] =
      for
        id <- Async[F].delay(UUID.randomUUID())
        token = AccountActivationToken(id, userId, tokenHash, expiresAt, None, createdAt)
        _ <-
          sql"""INSERT INTO activation_tokens (id, user_id, token_hash, expires_at, created_at)
                 VALUES ($id, $userId, $tokenHash, $expiresAt, $createdAt)
              """.update.run.transact(xa)
      yield token

    override def findByHash(tokenHash: String): F[Option[AccountActivationToken]] =
      sql"SELECT id, user_id, token_hash, expires_at, consumed_at, created_at FROM activation_tokens WHERE token_hash = $tokenHash"
        .query[(UUID, UUID, String, Instant, Option[Instant], Instant)]
        .map { case (id, userId, hash, expires, consumed, created) =>
          AccountActivationToken(id, userId, hash, expires, consumed, created)
        }
        .option
        .transact(xa)

    override def consume(tokenId: UUID, at: Instant): F[Unit] =
      sql"UPDATE activation_tokens SET consumed_at = $at WHERE id = $tokenId".update.run
        .transact(xa)
        .void

    override def invalidateForUser(userId: UUID, at: Instant): F[Unit] =
      sql"UPDATE activation_tokens SET consumed_at = $at WHERE user_id = $userId AND consumed_at IS NULL".update.run
        .transact(xa)
        .void
  }
}
