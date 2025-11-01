package com.example.app.http.middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import com.example.app.auth.AuthService
import com.example.app.http.TokenExtractor
import org.http4s.Request
import org.http4s.server.AuthMiddleware

object BearerAuthMiddleware {
  final case class AuthUser(userId: java.util.UUID, email: String)

  def apply(authService: AuthService[IO]): AuthMiddleware[IO, AuthUser] = {
    type OptionTIO[A] = OptionT[IO, A]

    val authenticate: Kleisli[OptionTIO, Request[IO], AuthUser] = Kleisli { req =>
      OptionT.liftF(IO.pure(TokenExtractor.bearerToken(req))).flatMapF {
        case Some(token) =>
          authService.authenticate(token).map(_.map(p => AuthUser(p.userId, p.email)))
        case None => IO.pure(None)
      }
    }

    AuthMiddleware(authenticate)
  }
}
