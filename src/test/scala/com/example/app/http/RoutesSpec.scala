package com.example.app.http

import munit.CatsEffectSuite
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

class RoutesSpec extends CatsEffectSuite:
  private val app = new Routes().httpApp

  test("GET /health returns 200") {
    val req = Request[IO](method = Method.GET, uri = uri"/health")
    app.run(req).map { res => assertEquals(res.status, Status.Ok) }
  }
