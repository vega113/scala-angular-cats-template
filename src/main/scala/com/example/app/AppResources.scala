package com.example.app

import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.example.app.auth.UserRepository
import com.example.app.config.AppConfig
import com.example.app.db.TransactorBuilder
import com.example.app.security.PasswordHasher
import com.example.app.security.jwt.{JwtService}
import doobie.hikari.HikariTransactor

final case class AppResources(
    transactor: HikariTransactor[IO],
    passwordHasher: PasswordHasher[IO],
    userRepository: UserRepository[IO],
    jwtService: JwtService[IO]
)

object AppResources {
  def make(cfg: AppConfig): Resource[IO, AppResources] =
    for
      xaOpt <- TransactorBuilder.optional(cfg)
      xa    <- Resource.eval(IO.fromOption(xaOpt)(new IllegalStateException("Database not configured")))
      jwt   <- Resource.eval(JwtService[IO](cfg.jwt))
      hasher = PasswordHasher.bcrypt[IO]()
      repo   = UserRepository.doobie[IO](xa)
    yield AppResources(xa, hasher, repo, jwt)
}
