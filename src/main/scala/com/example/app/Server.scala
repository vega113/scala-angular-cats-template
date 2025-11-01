package com.example.app

import cats.effect._
import cats.syntax.semigroupk._
import com.comcast.ip4s._
import org.http4s.{HttpRoutes, MediaType, Method, Request, StaticFile}
import org.http4s.dsl.io._
import org.http4s.server.Server as Http4sServer
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware._
import org.http4s.server.staticcontent.resourceServiceBuilder
import org.http4s.headers.Accept
import com.example.app.config.*
import com.example.app.auth.{AccountActivationService, AuthService, PasswordResetService}
import com.example.app.email.EmailService
import com.example.app.http.{AuthRoutes, Routes, TodoRoutes}
import com.example.app.http.middleware.{
  ErrorHandler,
  LoggingMiddleware,
  RequestIdMiddleware,
  BearerAuthMiddleware
}
import com.example.app.todo.TodoService
import com.example.app.tracing.{Tracing, TracingMiddleware}
import doobie.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Server:
  def resource(cfg: AppConfig, resources: AppResources): Resource[IO, Http4sServer] =
    for
      logger <- Resource.eval(Slf4jLogger.create[IO])
      readinessCheck = sql"select 1".query[Int].unique.transact(resources.transactor).void
      given Logger[IO] = logger
      emailService = EmailService.fromConfig[IO](cfg.email)
      activationService = AccountActivationService[IO](
        AccountActivationService.Dependencies(
          resources.userRepository,
          resources.activationTokenRepository,
          emailService,
          cfg.email,
          AccountActivationService.Config(cfg.activation.tokenTtl)
        )
      )
      authService = AuthService[IO](
        resources.userRepository,
        resources.passwordHasher,
        resources.jwtService,
        activationService
      )
      passwordResetService = PasswordResetService[IO](
        PasswordResetService.Dependencies(
          resources.userRepository,
          resources.passwordResetTokenRepository,
          resources.passwordHasher,
          emailService,
          cfg.email,
          cfg.passwordReset
        )
      )
      todoService = TodoService[IO](resources.todoRepository)
      authMiddleware = BearerAuthMiddleware(authService)
      authRoutes = new AuthRoutes(authService, passwordResetService, activationService)
      todoRoutes = new TodoRoutes(todoService, cfg.todo)
      routes = new Routes(authRoutes, todoRoutes, authMiddleware, readinessCheck)
      entryPoint <- Tracing.entryPoint(cfg.tracing)
      staticRoutes =
        if cfg.angular.mode == "dev" then HttpRoutes.empty[IO]
        else resourceServiceBuilder[IO]("static").toRoutes
      spaFallback =
        if cfg.angular.mode == "dev" then HttpRoutes.empty[IO]
        else
          HttpRoutes.of[IO] {
            case req @ GET -> _ if shouldServeSpa(req) =>
              StaticFile
                .fromResource("static/index.html", Some(req))
                .getOrElseF(NotFound())
          }
      combinedApp = (routes.routes <+> staticRoutes <+> spaFallback).orNotFound
      corsApp =
        if (cfg.angular.mode == "dev")
          CORS.policy
            .withAllowCredentials(false)
            .withAllowOriginAll(combinedApp)
        else combinedApp
      requestTracked = RequestIdMiddleware(corsApp)
      tracedApp = TracingMiddleware(entryPoint, cfg.tracing.enabled)(requestTracked)
      loggedApp = LoggingMiddleware(tracedApp)
      app = ErrorHandler(loggedApp)
      _ <- Resource.eval(logger.info(s"Starting HTTP server on port ${cfg.http.port}"))
      srv <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(cfg.http.port).getOrElse(port"8080"))
        .withRequestHeaderReceiveTimeout(cfg.http.requestHeaderTimeout)
        .withIdleTimeout(cfg.http.idleTimeout)
        .withShutdownTimeout(cfg.http.shutdownTimeout)
        .withHttpApp(app)
        .build
    yield srv

  private def shouldServeSpa(req: Request[IO]): Boolean =
    req.method == Method.GET &&
      !req.uri.path.segments.headOption.exists(_.decoded().equalsIgnoreCase("api")) &&
      acceptsHtmlOrMissing(req)

  private def acceptsHtmlOrMissing(req: Request[IO]): Boolean =
    req.headers
      .get[Accept]
      .map(_.values.exists(_.mediaRange.satisfiedBy(MediaType.text.html)))
      .getOrElse(true)
