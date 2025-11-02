package com.example.app.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*
import scala.concurrent.duration.*

final case class HttpConfig(
  port: Int,
  requestHeaderTimeout: FiniteDuration = 15.seconds,
  idleTimeout: FiniteDuration = 60.seconds,
  shutdownTimeout: FiniteDuration = 10.seconds
) derives ConfigReader
final case class AngularConfig(mode: String, port: Int) derives ConfigReader
final case class DbConfig(
  url: Option[String],
  user: Option[String],
  password: Option[String],
  schema: Option[String],
  maxPoolSize: Int,
  minimumIdle: Int = 0,
  connectionTimeout: FiniteDuration = 30.seconds
) derives ConfigReader
final case class JwtConfig(secret: Option[String], ttl: FiniteDuration) derives ConfigReader
final case class LoggingConfig(level: String) derives ConfigReader
final case class EmailConfig(
  provider: String = "logging",
  fromAddress: Option[String] = None,
  apiKey: Option[String] = None,
  resetSubject: String = "Reset your password",
  activationSubject: String = "Activate your account",
  activationUrlBase: String = "http://localhost:4200/auth/activate"
) derives ConfigReader
final case class TracingConfig(enabled: Boolean) derives ConfigReader
final case class TodoConfig(defaultPageSize: Int, maxPageSize: Int) derives ConfigReader
final case class PasswordResetConfig(
  resetUrlBase: String,
  tokenTtl: FiniteDuration = 1.hour
) derives ConfigReader
final case class ActivationConfig(tokenTtl: FiniteDuration = 24.hours) derives ConfigReader
final case class AppConfig(
  http: HttpConfig,
  angular: AngularConfig,
  db: DbConfig,
  jwt: JwtConfig,
  logging: LoggingConfig,
  email: EmailConfig,
  tracing: TracingConfig,
  todo: TodoConfig,
  passwordReset: PasswordResetConfig,
  activation: ActivationConfig
) derives ConfigReader
