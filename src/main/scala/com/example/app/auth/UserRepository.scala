package com.example.app.auth

import cats.effect.Async
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import java.time.Instant
import java.util.UUID

trait UserRepository[F[_]] {
  def create(email: String, passwordHash: String): F[User]
  def findByEmail(email: String): F[Option[User]]
  def findById(id: UUID): F[Option[User]]
  def updatePassword(id: UUID, passwordHash: String): F[Unit]
  def markActivated(id: UUID): F[Unit]
}

object UserRepository {
  def doobie[F[_]: Async](xa: Transactor[F]): UserRepository[F] =
    new DoobieUserRepository[F](xa)

  private final class DoobieUserRepository[F[_]: Async](xa: Transactor[F])
      extends UserRepository[F] {
    private def nowF: F[Instant] = Async[F].realTimeInstant

    override def create(email: String, passwordHash: String): F[User] =
      for
        id <- Async[F].delay(UUID.randomUUID())
        now <- nowF
        user = User(id, email, passwordHash, activated = false, now, now)
        _ <-
          sql"INSERT INTO users(id, email, password_hash, activated, created_at, updated_at) VALUES ($id, $email, $passwordHash, FALSE, $now, $now)".update.run
            .transact(xa)
            .void
      yield user

    override def findByEmail(email: String): F[Option[User]] =
      selectUser(
        sql"SELECT id, email, password_hash, activated, created_at, updated_at FROM users WHERE email = $email"
      ).option
        .transact(xa)

    override def findById(id: UUID): F[Option[User]] =
      selectUser(
        sql"SELECT id, email, password_hash, activated, created_at, updated_at FROM users WHERE id = $id"
      ).option
        .transact(xa)

    override def updatePassword(id: UUID, passwordHash: String): F[Unit] =
      for
        now <- nowF
        _ <-
          sql"""UPDATE users
                SET password_hash = $passwordHash,
                    updated_at = $now
                WHERE id = $id
             """.update.run.transact(xa)
      yield ()

    override def markActivated(id: UUID): F[Unit] =
      for
        now <- nowF
        _ <-
          sql"UPDATE users SET activated = TRUE, updated_at = $now WHERE id = $id".update.run
            .transact(xa)
      yield ()

    private def selectUser(query: Fragment): Query0[User] =
      query.query[(UUID, String, String, Boolean, Instant, Instant)].map {
        case (i, e, p, activated, c, u) =>
          User(i, e, p, activated, c, u)
      }
  }
}
