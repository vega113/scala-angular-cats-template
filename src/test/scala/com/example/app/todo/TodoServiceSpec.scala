package com.example.app.todo

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._
import munit.CatsEffectSuite
import com.example.app.todo.FieldPatch

import java.time.Instant
import java.util.UUID

class TodoServiceSpec extends CatsEffectSuite:
  private def inMemoryRepo: IO[(TodoRepository[IO], Ref[IO, Map[UUID, Todo]])] =
    Ref.of[IO, Map[UUID, Todo]](Map.empty).map { ref => (new InMemoryRepo(ref), ref) }

  private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

  test("create trims and stores todo") {
    inMemoryRepo.flatMap { case (repo, ref) =>
      val service = TodoService[IO](repo)
      for
        todo <- service.create(userId, TodoCreate("  Title  ", None, None))
        stored <- ref.get
      yield {
        assertEquals(todo.title, "Title")
        assert(stored.values.exists(_.title == "Title"))
      }
    }
  }

  test("update returns not found when todo missing") {
    inMemoryRepo.flatMap { case (repo, _) =>
      val service = TodoService[IO](repo)
      service.update(userId, UUID.randomUUID(), TodoUpdate(None, FieldPatch.Unchanged, FieldPatch.Unchanged, None)).attempt.map { res =>
        assertEquals(res.left.map(_.getClass), Left(classOf[TodoError.NotFound.type]))
      }
    }
  }

  private final class InMemoryRepo(ref: Ref[IO, Map[UUID, Todo]]) extends TodoRepository[IO]:
    override def create(userId: UUID, create: TodoCreate): IO[Todo] =
      for
        id <- IO(UUID.randomUUID())
        now <- IO.realTimeInstant
        todo = Todo(id, userId, create.title, create.description, create.dueDate, false, now, now)
        _ <- ref.update(_ + (id -> todo))
      yield todo

    override def get(userId: UUID, id: UUID): IO[Option[Todo]] =
      ref.get.map(_.get(id).filter(_.userId == userId))

    override def list(userId: UUID, completed: Option[Boolean], limit: Int, offset: Int): IO[List[Todo]] =
      ref.get.map(_.values.filter(_.userId == userId).toList)

    override def update(userId: UUID, id: UUID, update: TodoUpdate): IO[Option[Todo]] =
      ref.modify { todos =>
        todos.get(id).filter(_.userId == userId) match
          case Some(todo) =>
            val updated = todo.copy(
              title = update.title.getOrElse(todo.title),
              description = update.description match
                case FieldPatch.Unchanged => todo.description
                case FieldPatch.Set(value) => Some(value)
                case FieldPatch.Clear => None,
              dueDate = update.dueDate match
                case FieldPatch.Unchanged => todo.dueDate
                case FieldPatch.Set(value) => Some(value)
                case FieldPatch.Clear => None,
              completed = update.completed.getOrElse(todo.completed),
              updatedAt = Instant.parse("2024-01-01T00:00:00Z")
            )
            (todos.updated(id, updated), Some(updated))
          case None => (todos, None)
      }

    override def delete(userId: UUID, id: UUID): IO[Boolean] =
      ref.modify { todos =>
        if (todos.get(id).exists(_.userId == userId))
          (todos - id, true)
        else
          (todos, false)
      }
