package com.example.app.http

import cats.effect.IO
import com.example.app.auth.{AuthError, AuthResult, AuthService, User}
import com.example.app.security.jwt.JwtPayload
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
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
    case req @ GET -> Root / "me" =>
      me(req)
  }

  private def signup(req: Request[IO]): IO[Response[IO]] =
    req.as[SignupRequest].flatMap { body =>
      authService.signup(body.email, body.password).attempt.flatMap {
        case Right(result) => ApiResponse.success(Status.Created, result.asJson)
        case Left(err: AuthError) => authErrorResponse(err)
        case Left(_) =>
          ApiResponse.error(ApiError.internal("signup_failed", "Unable to complete signup"))
      }
    }

  private def login(req: Request[IO]): IO[Response[IO]] =
    req.as[LoginRequest].flatMap { body =>
      authService.login(body.email, body.password).attempt.flatMap {
        case Right(result) => ApiResponse.success(result.asJson)
        case Left(err: AuthError) => authErrorResponse(err)
        case Left(_) =>
          ApiResponse.error(ApiError.internal("login_failed", "Unable to authenticate"))
      }
    }

  private def me(req: Request[IO]): IO[Response[IO]] =
    extractToken(req).flatMap {
      case Some(token) =>
        authService.authenticate(token).flatMap {
          case Some(JwtPayload(userId, _)) =>
            authService.currentUser(userId).flatMap {
              case Some(user) => ApiResponse.success(UserResponse.from(user).asJson)
              case None => ApiResponse.error(ApiError.notFound("user_not_found", "User not found"))
            }
          case None =>
            ApiResponse.error(ApiError.unauthorized("invalid_token", "Invalid or expired token"))
        }
      case None =>
        ApiResponse.error(ApiError.unauthorized("missing_token", "Authorization token missing"))
    }

  private def extractToken(req: Request[IO]): IO[Option[String]] =
    IO.pure(TokenExtractor.bearerToken(req))

  private def authErrorResponse(err: AuthError) = err match
    case AuthError.EmailAlreadyExists =>
      ApiResponse.error(ApiError.conflict("email_exists", "Email already registered"))
    case AuthError.InvalidCredentials =>
      ApiResponse.error(ApiError.unauthorized("invalid_credentials", "Invalid credentials"))
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

  private object UserResponse {
    def from(user: User): UserResponse = UserResponse(user.id, user.email)
  }
}
