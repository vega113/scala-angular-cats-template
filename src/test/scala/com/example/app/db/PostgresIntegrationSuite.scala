package com.example.app.db

import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.example.app.config.{AngularConfig, AppConfig, DbConfig, HttpConfig, JwtConfig, LoggingConfig}
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
      TransactorBuilder.optional(cfg).use {
        case Some(xa) =>
          sql"select 1".query[Int].unique.transact(xa).map(n => assertEquals(n, 1))
        case None => fail("expected transactor")
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
      logging = LoggingConfig(level = "INFO")
    )

object DockerSupport:
  def isAvailable: Boolean =
    scala.util.Try(DockerClientFactory.instance().isDockerAvailable).getOrElse(false)

  val skipMessage: String = "Docker is required for TestContainers integration tests"
