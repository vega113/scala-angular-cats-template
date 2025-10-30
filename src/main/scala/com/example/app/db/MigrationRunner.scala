package com.example.app.db

import cats.effect.IO
import com.example.app.config.AppConfig
import org.flywaydb.core.Flyway
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.jdk.CollectionConverters._

object MigrationRunner:
  def migrate(cfg: AppConfig): IO[Unit] =
    for
      logger <- Slf4jLogger.create[IO]
      _ <- cfg.db.url match
        case Some(url) =>
          val defaultSchema = cfg.db.schema.getOrElse("public")
          val migrateIO = IO.blocking {
            val builder = Flyway.configure()
              .dataSource(url, cfg.db.user.orNull, cfg.db.password.orNull)
              .locations("classpath:db/migration")
              .baselineOnMigrate(true)
              .defaultSchema(defaultSchema)
              .schemas(defaultSchema)
              .placeholders(Map("app_schema" -> defaultSchema).asJava)
            builder.load().migrate()
          }

          migrateIO.attempt.flatMap {
            case Right(result) =>
              logger.info(s"Flyway migrations executed: ${result.migrationsExecuted}")
            case Left(err) =>
              logger.error(err)("Flyway migration failed") *> IO.raiseError(err)
          }
        case None =>
          logger.warn("DATABASE_URL not provided; skipping Flyway migrations")
    yield ()
