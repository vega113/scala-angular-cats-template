package com.example.app.db

import cats.effect.{IO, Resource}
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.example.app.config.{
  AngularConfig,
  AppConfig,
  DbConfig,
  HttpConfig,
  JwtConfig,
  LoggingConfig,
  EmailConfig,
  PasswordResetConfig,
  TodoConfig,
  TracingConfig
}
import com.example.app.todo.{FieldPatch, TodoCreate, TodoRepository, TodoUpdate}
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

class TodoRepositoryPostgresSpec extends CatsEffectSuite {
  private val dockerAvailable = DockerSupport.isAvailable
  private val container = ResourceSuiteLocalFixture("postgres", postgresResource)
  override def munitFixtures = if dockerAvailable then List(container) else Nil

  test("TodoRepository CRUD flow") {
    assume(dockerAvailable, DockerSupport.skipMessage)
    val cfg = configFromContainer(container())

    val program = for
      _ <- MigrationRunner.migrate(cfg)
      _ <- TransactorBuilder.optional(cfg).use {
        case Some(xa) =>
          val repo = TodoRepository.doobie[IO](xa)
          val userId = UUID.randomUUID()
          for
            created <- repo.create(userId, TodoCreate("Task", Some("desc"), None))
            _ <- repo.update(
              userId,
              created.id,
              TodoUpdate(
                title = Some("Updated"),
                description = FieldPatch.Clear,
                dueDate = FieldPatch.Unchanged,
                completed = Some(true)
              )
            )
            fetched <- repo.get(userId, created.id)
            list <- repo.list(userId, Some(true), limit = 10, offset = 0)
            deleted <- repo.delete(userId, created.id)
          yield {
            assertEquals(fetched.map(_.title), Some("Updated"))
            assertEquals(list.map(_.id), List(created.id))
            assert(deleted)
          }
        case None => IO.raiseError(new RuntimeException("missing transactor"))
      }
    yield ()

    program
  }

  private def postgresResource: Resource[IO, PostgreSQLContainer] =
    Resource.make {
      IO {
        val container = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
        container.start()
        container
      }
    }(c => IO(c.stop()))

  private def configFromContainer(container: PostgreSQLContainer): AppConfig =
    AppConfig(
      http = HttpConfig(port = 0),
      angular = AngularConfig(mode = "dev", port = 4200),
      db = DbConfig(
        url = Some(container.jdbcUrl),
        user = Some(container.username),
        password = Some(container.password),
        schema = None,
        maxPoolSize = 4
      ),
      jwt = JwtConfig(secret = Some("integration"), ttl = 3600),
      logging = LoggingConfig(level = "INFO"),
      email = EmailConfig(
        provider = "logging",
        fromAddress = Some("no-reply@example.com"),
        apiKey = None,
        resetSubject = "Reset your password"
      ),
      tracing = TracingConfig(enabled = false),
      todo = TodoConfig(defaultPageSize = 20, maxPageSize = 100),
      passwordReset = PasswordResetConfig(
        resetUrlBase = "http://localhost:4200/password-reset/confirm",
        tokenTtl = 1.hour
      )
    )
}
