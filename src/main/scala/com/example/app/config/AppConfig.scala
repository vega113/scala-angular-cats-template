package com.example.app.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

final case class HttpConfig(port: Int) derives ConfigReader
final case class AngularConfig(mode: String, port: Int) derives ConfigReader
final case class DbConfig(url: Option[String], maxPoolSize: Int) derives ConfigReader
final case class JwtConfig(secret: Option[String], ttl: Int) derives ConfigReader
final case class LoggingConfig(level: String) derives ConfigReader
final case class AppConfig(http: HttpConfig, angular: AngularConfig, db: DbConfig, jwt: JwtConfig, logging: LoggingConfig) derives ConfigReader
