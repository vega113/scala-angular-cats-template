package com.example.app.security

import cats.effect.IO
import munit.CatsEffectSuite

class PasswordHasherSpec extends CatsEffectSuite:
  private val hasher = PasswordHasher.bcrypt[IO]()

  test("hash and verify succeeds") {
    val plain = "s3cr3t"
    for
      hash <- hasher.hash(plain)
      ok   <- hasher.verify(plain, hash)
    yield assert(ok)
  }

  test("verify fails with different password") {
    val plain = "s3cr3t"
    for
      hash <- hasher.hash(plain)
      ok   <- hasher.verify("other", hash)
    yield assert(!ok)
  }
