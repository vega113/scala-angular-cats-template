package com.example.app.http.middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import com.example.app.auth.AuthService
import com.example.app.security.jwt.JwtPayload
import org.http4s.{AuthScheme, Credentials, Request}
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware

object BearerAuthMiddleware {
  final case class AuthUser(userId: java.util.UUID, email: String)

  def apply(authService: AuthService[IO]): AuthMiddleware[IO, AuthUser] = {
    type OptionTIO[A] = OptionT[IO, A]

    val authenticate: Kleisli[OptionTIO, Request[IO], AuthUser] = Kleisli { req =>
      OptionT {
        extractToken(req).flatMap {
          case Some(token) =>
            authService.authenticate(token).map(_.map(p => AuthUser(p.userId, p.email)))
          case None => IO.pure(None)
        }
      }
    }

    AuthMiddleware(authenticate)
  }

  private def extractToken(req: Request[IO]): IO[Option[String]] =
    IO.pure {
      req.headers.get[Authorization].collect {
        case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => token
      }
    }
}
