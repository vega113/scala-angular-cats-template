package com.example.app.http

import cats.effect.IO
import com.example.app.auth.{AuthError, AuthResult, AuthService, User}
import com.example.app.security.jwt.JwtPayload
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.{EntityDecoder, HttpRoutes, Request, Response, Status}

import java.util.UUID

final class AuthRoutes(authService: AuthService[IO]) {
  import AuthRoutes.{*, given}

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "signup" =>
      signup(req)
    case req @ POST -> Root / "login" =>
      login(req)
    case req @ GET  -> Root / "me"    =>
      me(req)
  }

  private def signup(req: Request[IO]): IO[Response[IO]] =
    req.as[SignupRequest].flatMap { body =>
      authService.signup(body.email, body.password).attempt.flatMap {
        case Right(result)         => jsonResponse(Status.Created, result.asJson)
        case Left(err: AuthError)  => authErrorResponse(err)
        case Left(other)           => jsonResponse(Status.InternalServerError, errorBody("signup_failed", other.getMessage))
      }
    }

  private def login(req: Request[IO]): IO[Response[IO]] =
    req.as[LoginRequest].flatMap { body =>
      authService.login(body.email, body.password).attempt.flatMap {
        case Right(result)         => jsonResponse(Status.Ok, result.asJson)
        case Left(err: AuthError)  => authErrorResponse(err)
        case Left(other)           => jsonResponse(Status.InternalServerError, errorBody("login_failed", other.getMessage))
      }
    }

  private def me(req: Request[IO]): IO[Response[IO]] =
    extractToken(req).flatMap {
      case Some(token) =>
        authService.authenticate(token).flatMap {
          case Some(JwtPayload(userId, _)) =>
            authService.currentUser(userId).flatMap {
              case Some(user) => jsonResponse(Status.Ok, UserResponse.from(user).asJson)
              case None       => jsonResponse(Status.NotFound, errorBody("user_not_found", "User not found"))
            }
          case None => jsonResponse(Status.Unauthorized, errorBody("invalid_token", "Invalid or expired token"))
        }
      case None => jsonResponse(Status.Unauthorized, errorBody("missing_token", "Authorization token missing"))
    }

  private def extractToken(req: Request[IO]): IO[Option[String]] =
    IO.pure(TokenExtractor.bearerToken(req))

  private def authErrorResponse(err: AuthError): IO[Response[IO]] = err match
    case AuthError.EmailAlreadyExists => jsonResponse(Status.Conflict, errorBody("email_exists", "Email already registered"))
    case AuthError.InvalidCredentials => jsonResponse(Status.Unauthorized, errorBody("invalid_credentials", "Invalid credentials"))

  private def jsonResponse(status: Status, json: Json): IO[Response[IO]] =
    IO.pure(Response[IO](status).withEntity(json))
}

object AuthRoutes {
  private final case class SignupRequest(email: String, password: String)
  private final case class LoginRequest(email: String, password: String)
  private final case class UserResponse(id: UUID, email: String)
  private final case class AuthPayload(token: String, user: UserResponse)

  private given Decoder[SignupRequest] = deriveDecoder
  private given Decoder[LoginRequest] = deriveDecoder
  private given EntityDecoder[IO, SignupRequest] = jsonOf[IO, SignupRequest]
  private given EntityDecoder[IO, LoginRequest] = jsonOf[IO, LoginRequest]

  private given Encoder[UserResponse] = deriveEncoder
  private given Encoder[AuthPayload] = deriveEncoder

  given Encoder[AuthResult] = Encoder.instance { result =>
    AuthPayload(result.token, UserResponse.from(result.user)).asJson
  }

  private[http] def errorBody(code: String, message: String): Json =
    Map("error" -> Map("code" -> code.asJson, "message" -> message.asJson).asJson).asJson

  private object UserResponse {
    def from(user: User): UserResponse = UserResponse(user.id, user.email)
  }
}
