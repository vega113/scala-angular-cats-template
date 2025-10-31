package com.example.app.db

import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.example.app.config.{AngularConfig, AppConfig, DbConfig, HttpConfig, JwtConfig, LoggingConfig, TodoConfig}
import doobie.implicits._
import munit.CatsEffectSuite
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer

class PostgresIntegrationSuite extends CatsEffectSuite:
  private val dockerAvailable: Boolean = DockerSupport.isAvailable

  test("MigrationRunner migrates successfully against container") {
    assume(dockerAvailable, DockerSupport.skipMessage)
    withPostgres { container =>
      val cfg = configFromContainer(container)
      MigrationRunner.migrate(cfg)
    }
  }

  test("TransactorBuilder executes a simple query") {
    assume(dockerAvailable, DockerSupport.skipMessage)
    withPostgres { container =>
      val cfg = configFromContainer(container)
      for
        _ <- MigrationRunner.migrate(cfg)
        result <- TransactorBuilder.optional(cfg).use {
          case Some(xa) =>
            val checkTables = for
              selectOne <- sql"select 1".query[Int].unique
              usersTable <- sql"select to_regclass('app.users')".query[Option[String]].unique
              todosTable <- sql"select to_regclass('app.todos')".query[Option[String]].unique
            yield {
              assertEquals(selectOne, 1)
              assertEquals(usersTable, Some("app.users"))
              assertEquals(todosTable, Some("app.todos"))
            }
            checkTables.transact(xa)
          case None => fail("expected transactor")
        }
      yield result
    }
  }

  test("UserRepository can create and fetch users") {
    assume(dockerAvailable, DockerSupport.skipMessage)
    withPostgres { container =>
      val cfg = configFromContainer(container)
      val email = "integration@example.com"
      val passwordHash = "$2a$10$Z.uq3G6eeiN4sqw2H8t2XePrwL7hIwrKyYVJZZzKzbwQ6vurIWBiK" // precomputed hash

      for
        _ <- MigrationRunner.migrate(cfg)
        result <- TransactorBuilder.optional(cfg).use {
          case Some(xa) =>
            val repo = com.example.app.auth.UserRepository.doobie[IO](xa)
            for
              created <- repo.create(email, passwordHash)
              fetched <- repo.findByEmail(email)
            yield {
              assertEquals(fetched.map(_.id), Some(created.id))
              assertEquals(fetched.map(_.email), Some(email))
            }
          case None => fail("expected transactor")
        }
      yield result
    }
  }

  test("AuthService signup/login flow") {
    assume(dockerAvailable, DockerSupport.skipMessage)
    withPostgres { container =>
      val cfg = configFromContainer(container)
      val repoConfig = cfg

      MigrationRunner.migrate(repoConfig) *> TransactorBuilder
        .optional(repoConfig)
        .use {
          case Some(xa) =>
            for
              jwt <- com.example.app.security.jwt.JwtService[IO](repoConfig.jwt.copy(secret = Some("it-secret")))
              repo = com.example.app.auth.UserRepository.doobie[IO](xa)
              hasher = com.example.app.security.PasswordHasher.bcrypt[IO]()
              service = com.example.app.auth.AuthService[IO](repo, hasher, jwt)
              signup <- service.signup("pguser@example.com", "secret")
              login  <- service.login("pguser@example.com", "secret")
            yield assertEquals(login.user.email, signup.user.email)
          case None => IO.raiseError(new RuntimeException("missing transactor"))
        }
    }
  }

  private def withPostgres[A](use: PostgreSQLContainer[?] => IO[A]): IO[A] =
    postgresResource.use(use)

  private def postgresResource: Resource[IO, PostgreSQLContainer[?]] =
    Resource.make {
      IO.blocking {
        val container = new PostgreSQLContainer("postgres:16-alpine")
        container.start()
        container
      }
    }(container => IO.blocking(container.stop()))

  private def configFromContainer(container: PostgreSQLContainer[?]): AppConfig =
    AppConfig(
      http = HttpConfig(port = 0),
      angular = AngularConfig(mode = "dev", port = 4200),
      db = DbConfig(
        url = Some(container.getJdbcUrl),
        user = Some(container.getUsername),
        password = Some(container.getPassword),
        schema = None,
        maxPoolSize = 4
      ),
      jwt = JwtConfig(secret = None, ttl = 3600),
      logging = LoggingConfig(level = "INFO"),
      todo = TodoConfig(defaultPageSize = 20, maxPageSize = 100)
    )

object DockerSupport:
  def isAvailable: Boolean =
    scala.util.Try(DockerClientFactory.instance().isDockerAvailable).getOrElse(false)

  val skipMessage: String = "Docker is required for TestContainers integration tests"
