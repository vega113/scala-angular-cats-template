package com.example.app.todo

import cats.effect.Async
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import java.time.Instant
import java.util.UUID

trait TodoRepository[F[_]] {
  def create(userId: UUID, create: TodoCreate): F[Todo]
  def get(userId: UUID, id: UUID): F[Option[Todo]]
  def list(userId: UUID, completed: Option[Boolean], limit: Int, offset: Int): F[List[Todo]]
  def update(userId: UUID, id: UUID, update: TodoUpdate): F[Option[Todo]]
  def delete(userId: UUID, id: UUID): F[Boolean]
}

object TodoRepository {
  def doobie[F[_]: Async](xa: Transactor[F]): TodoRepository[F] = new DoobieTodoRepository[F](xa)

  private final class DoobieTodoRepository[F[_]: Async](xa: Transactor[F]) extends TodoRepository[F] {
    private def nowF: F[Instant] = Async[F].realTimeInstant

    override def create(userId: UUID, create: TodoCreate): F[Todo] =
      for
        id <- Async[F].delay(UUID.randomUUID())
        now <- nowF
        todo = Todo(
          id = id,
          userId = userId,
          title = create.title,
          description = create.description,
          dueDate = create.dueDate,
          completed = false,
          createdAt = now,
          updatedAt = now
        )
        _ <- sql"INSERT INTO todos (id, user_id, title, description, due_date, completed, created_at, updated_at) VALUES ($id, $userId, ${create.title}, ${create.description}, ${create.dueDate}, false, $now, $now)".update.run.transact(xa)
      yield todo

    override def get(userId: UUID, id: UUID): F[Option[Todo]] =
      selectBase(userId).++(fr"AND id = $id").query[Todo].option.transact(xa)

    override def list(userId: UUID, completed: Option[Boolean], limit: Int, offset: Int): F[List[Todo]] = {
      val base = selectBase(userId)
      val filtered = completed match
        case Some(flag) => base ++ fr"AND completed = $flag"
        case None       => base
      (filtered ++ fr"ORDER BY created_at DESC LIMIT $limit OFFSET $offset").query[Todo].to[List].transact(xa)
    }

    override def update(userId: UUID, id: UUID, update: TodoUpdate): F[Option[Todo]] = {
      val setFragments = List(
        update.title.map(t => fr"title = $t"),
        update.description.map {
          case Some(desc) => fr"description = $desc"
          case None       => fr"description = NULL"
        },
        update.dueDate.map {
          case Some(due) => fr"due_date = $due"
          case None      => fr"due_date = NULL"
        },
        update.completed.map(flag => fr"completed = $flag"),
        Some(fr"updated_at = CURRENT_TIMESTAMP")
      ).flatten

      for
        _ <- if (setFragments.isEmpty) Async[F].unit
             else {
               val assignments = setFragments.tail.foldLeft(setFragments.head) { (acc, frag) =>
                 acc ++ fr", " ++ frag
               }
               val updateFragment = fr"UPDATE todos SET " ++ assignments ++ fr" WHERE user_id = $userId AND id = $id"
               updateFragment.update.run.transact(xa).void
             }
        result <- get(userId, id)
      yield result
    }

    override def delete(userId: UUID, id: UUID): F[Boolean] =
      sql"DELETE FROM todos WHERE user_id = $userId AND id = $id".update.run.transact(xa).map(_ > 0)

    private def selectBase(userId: UUID): Fragment =
      fr"SELECT id, user_id, title, description, due_date, completed, created_at, updated_at FROM todos WHERE user_id = $userId"
  }
}
