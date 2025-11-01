package com.example.app.todo

import cats.effect.Sync
import cats.syntax.all._
import java.util.UUID

sealed abstract class TodoError(message: String) extends RuntimeException(message)
object TodoError {
  case object NotFound extends TodoError("todo_not_found")
}

trait TodoService[F[_]] {
  def create(userId: UUID, input: TodoCreate): F[Todo]
  def list(userId: UUID, completed: Option[Boolean], limit: Int, offset: Int): F[List[Todo]]
  def get(userId: UUID, id: UUID): F[Option[Todo]]
  def update(userId: UUID, id: UUID, update: TodoUpdate): F[Todo]
  def delete(userId: UUID, id: UUID): F[Unit]
}

object TodoService {
  def apply[F[_]: Sync](repo: TodoRepository[F]): TodoService[F] =
    new TodoService[F] {
      private val F = Sync[F]

      override def create(userId: UUID, input: TodoCreate): F[Todo] =
        if (input.title.trim.isEmpty) F.raiseError(new IllegalArgumentException("title_required"))
        else repo.create(userId, input.copy(title = input.title.trim))

      override def list(
        userId: UUID,
        completed: Option[Boolean],
        limit: Int,
        offset: Int
      ): F[List[Todo]] =
        repo.list(userId, completed, limit = math.max(limit, 1), offset = math.max(offset, 0))

      override def get(userId: UUID, id: UUID): F[Option[Todo]] =
        repo.get(userId, id)

      override def update(userId: UUID, id: UUID, update: TodoUpdate): F[Todo] =
        val sanitized = update.copy(title = update.title.map(_.trim).filter(_.nonEmpty))
        repo.update(userId, id, sanitized).flatMap {
          case Some(todo) => F.pure(todo)
          case None => F.raiseError(TodoError.NotFound)
        }

      override def delete(userId: UUID, id: UUID): F[Unit] =
        repo.delete(userId, id).flatMap {
          case true => F.unit
          case false => F.raiseError(TodoError.NotFound)
        }
    }
}
