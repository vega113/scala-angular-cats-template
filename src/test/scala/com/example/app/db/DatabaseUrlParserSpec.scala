package com.example.app.db

import com.example.app.config.DbConfig
import munit.FunSuite

class DatabaseUrlParserSpec extends FunSuite:
  private val baseConfig =
    DbConfig(url = None, user = None, password = None, schema = None, maxPoolSize = 8)

  test("normalize converts postgres URL to JDBC and extracts credentials") {
    val config =
      baseConfig.copy(url = Some("postgres://user:pass@db.example.com:5432/app?sslmode=require"))
    val normalized = DatabaseUrlParser.normalize(config)
    assertEquals(normalized.url, Some("jdbc:postgresql://db.example.com:5432/app?sslmode=require"))
    assertEquals(normalized.user, Some("user"))
    assertEquals(normalized.password, Some("pass"))
  }

  test("normalize preserves existing user/password") {
    val config = baseConfig.copy(
      url = Some("postgres://user:pass@db.example.com/app"),
      user = Some("explicit"),
      password = Some("secret")
    )
    val normalized = DatabaseUrlParser.normalize(config)
    assertEquals(normalized.user, Some("explicit"))
    assertEquals(normalized.password, Some("secret"))
  }

  test("normalize populates schema from query when absent") {
    val config = baseConfig.copy(url = Some("postgres://user:pass@db/app?currentSchema=todo"))
    val normalized = DatabaseUrlParser.normalize(config)
    assertEquals(normalized.schema, Some("todo"))
  }

  test("normalize leaves jdbc url untouched") {
    val jdbc = baseConfig.copy(url = Some("jdbc:postgresql://db/app"))
    assertEquals(DatabaseUrlParser.normalize(jdbc), jdbc)
  }
