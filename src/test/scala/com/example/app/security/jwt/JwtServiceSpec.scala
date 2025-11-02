package com.example.app.security.jwt

import cats.effect.IO
import com.example.app.config.JwtConfig
import scala.concurrent.duration.*
import munit.CatsEffectSuite
import java.util.UUID

class JwtServiceSpec extends CatsEffectSuite:
  private val config = JwtConfig(secret = Some("test-secret"), ttl = 3600.seconds)

  test("generate and verify round-trip") {
    for
      service <- JwtService[IO](config)
      payload = JwtPayload(UUID.randomUUID(), "user@example.com")
      token <- service.generate(payload)
      decoded <- service.verify(token)
    yield assertEquals(decoded, Some(payload))
  }

  test("verify returns None for invalid token") {
    for
      service <- JwtService[IO](config)
      decoded <- service.verify("not-a-token")
    yield assertEquals(decoded, None)
  }

  test("fails when secret missing") {
    interceptMessageIO[IllegalStateException]("JWT secret is not configured") {
      JwtService[IO](JwtConfig(secret = None, ttl = 3600.seconds))
    }.void
  }
