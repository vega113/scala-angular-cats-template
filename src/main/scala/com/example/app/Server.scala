package com.example.app

import cats.effect._
import com.comcast.ip4s._
import org.http4s.server.Server as Http4sServer
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s.server.middleware._
import org.http4s.Uri
import com.example.app.config.*
import com.example.app.http.Routes
import com.example.app.http.middleware.{RequestIdMiddleware, LoggingMiddleware, ErrorHandler}

object Server:
  def resource(cfg: AppConfig): Resource[IO, Http4sServer] =
    for
      logger <- Resource.eval(Slf4jLogger.create[IO])
      baseApp = new Routes().httpApp
      corsApp = {
        if (cfg.angular.mode == "dev")
          CORS.policy
            .withAllowCredentials(false)
            .withAllowOriginAll(baseApp)
        else baseApp
      }
      app     = ErrorHandler(LoggingMiddleware(RequestIdMiddleware(corsApp)))
      _      <- Resource.eval(logger.info(s"Starting HTTP server on port ${cfg.http.port}"))
      srv    <- EmberServerBuilder.default[IO]
                  .withHost(ipv4"0.0.0.0")
                  .withPort(Port.fromInt(cfg.http.port).getOrElse(port"8080"))
                  .withHttpApp(app)
                  .build
    yield srv
