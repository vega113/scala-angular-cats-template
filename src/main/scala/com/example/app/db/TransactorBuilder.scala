package com.example.app.db

import cats.effect.{IO, Resource}
import com.example.app.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import org.typelevel.log4cats.slf4j.Slf4jLogger

object TransactorBuilder:
  private val DriverClass = "org.postgresql.Driver"

  def optional(cfg: AppConfig): Resource[IO, Option[HikariTransactor[IO]]] =
    Resource.eval(Slf4jLogger.create[IO]).flatMap { logger =>
      cfg.db.url match
        case Some(url) =>
          for
            hikariCfg <- Resource.eval(IO(hikariConfig(cfg, url)))
            transactor <- HikariTransactor.fromHikariConfig[IO](hikariCfg)
            _ <- Resource.eval(logger.info(s"Hikari transactor initialized for ${redact(url)}"))
          yield Some(transactor)
        case None =>
          Resource.eval(logger.warn("DATABASE_URL not set; skipping transactor creation")).map(_ => None)
    }

  private def hikariConfig(cfg: AppConfig, url: String): HikariConfig =
    val hc = new HikariConfig()
    hc.setJdbcUrl(url)
    cfg.db.user.foreach(hc.setUsername)
    cfg.db.password.foreach(hc.setPassword)
    cfg.db.schema.foreach(hc.setSchema)
    hc.setMaximumPoolSize(cfg.db.maxPoolSize)
    hc.setDriverClassName(DriverClass)
    hc

  private val CredentialsPattern = "://[^@]+@"

  private def redact(url: String): String =
    url.replaceAll(CredentialsPattern, "://***@")
