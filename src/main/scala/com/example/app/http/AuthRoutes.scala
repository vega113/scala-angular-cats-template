package com.example.app.http

import cats.effect.IO
import com.example.app.auth.{AccountActivationService, AuthError, AuthResult, AuthService, PasswordResetService, User}
import com.example.app.security.jwt.JwtPayload
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.{EntityDecoder, HttpRoutes, Request, Response, Status}

import java.util.UUID

final class AuthRoutes(
  authService: AuthService[IO],
  passwordResetService: PasswordResetService[IO],
  activationService: AccountActivationService[IO]
) {
  import AuthRoutes.{*, given}

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "signup" =>
      signup(req)
    case req @ POST -> Root / "login" =>
      login(req)
    case req @ GET -> Root / "me" =>
      me(req)
    case req @ POST -> Root / "password-reset" / "request" =>
      requestPasswordReset(req)
    case req @ POST -> Root / "password-reset" / "confirm" =>
      confirmPasswordReset(req)
    case req @ POST -> Root / "activation" / "confirm" =>
      confirmActivation(req)
  }

  private def signup(req: Request[IO]): IO[Response[IO]] =
    req.as[SignupRequest].flatMap { body =>
      authService.signup(body.email, body.password).attempt.flatMap {
        case Right(_) =>
          ApiResponse.success(
            Status.Accepted,
            Json.obj(
              "status" -> Json.fromString("activation_required"),
              "message" -> Json.fromString("Check your email to activate your account.")
            )
          )
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

  private def requestPasswordReset(req: Request[IO]): IO[Response[IO]] =
    req.as[PasswordResetRequest].flatMap { body =>
      passwordResetService.request(body.email).attempt.flatMap {
        case Right(_) =>
          ApiResponse.success(Status.Accepted, Json.obj("status" -> Json.fromString("ok")))
        case Left(_) =>
          ApiResponse.error(
            ApiError.internal("password_reset_failed", "Unable to process password reset request")
          )
      }
    }

  private def confirmPasswordReset(req: Request[IO]): IO[Response[IO]] =
    req.as[PasswordResetConfirmRequest].flatMap { body =>
      passwordResetService.confirm(body.token, body.password).attempt.flatMap {
        case Right(_) => ApiResponse.noContent
        case Left(err: PasswordResetService.Error.PasswordTooWeak) =>
          ApiResponse.error(
            ApiError.unprocessableEntity("password_too_weak", err.getMessage)
          )
        case Left(PasswordResetService.Error.InvalidToken) =>
          ApiResponse.error(ApiError.notFound("password_reset_invalid", "Invalid or expired token"))
        case Left(PasswordResetService.Error.TokenExpired) =>
          ApiResponse.error(
            ApiError.unprocessableEntity("password_reset_expired", "Password reset token has expired")
          )
        case Left(_) =>
          ApiResponse.error(
            ApiError.internal("password_reset_failed", "Unable to reset password at this time")
          )
      }
    }

  private def confirmActivation(req: Request[IO]): IO[Response[IO]] =
    req.as[ActivationConfirmRequest].flatMap { body =>
      activationService.activate(body.token).attempt.flatMap {
        case Right(user) =>
          authService.issueToken(user).flatMap(result => ApiResponse.success(result.asJson))
        case Left(AccountActivationService.Error.InvalidToken) =>
          ApiResponse.error(ApiError.notFound("activation_invalid", "Activation link is invalid"))
        case Left(AccountActivationService.Error.TokenExpired) =>
          ApiResponse.error(
            ApiError.unprocessableEntity("activation_expired", "Activation link has expired")
          )
        case Left(_) =>
          ApiResponse.error(
            ApiError.internal("activation_failed", "Unable to activate account at this time")
          )
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
    case AuthError.AccountNotActivated =>
      ApiResponse.error(
        ApiError.forbidden("account_not_activated", "Activate your account before signing in")
      )
}

object AuthRoutes {
  private final case class SignupRequest(email: String, password: String)
  private final case class LoginRequest(email: String, password: String)
  private final case class PasswordResetRequest(email: String)
  private final case class PasswordResetConfirmRequest(token: String, password: String)
  private final case class ActivationConfirmRequest(token: String)
  private final case class UserResponse(id: UUID, email: String)
  private final case class AuthPayload(token: String, user: UserResponse)

  private given Decoder[SignupRequest] = deriveDecoder
  private given Decoder[LoginRequest] = deriveDecoder
  private given Decoder[PasswordResetRequest] = deriveDecoder
  private given Decoder[PasswordResetConfirmRequest] = deriveDecoder
  private given Decoder[ActivationConfirmRequest] = deriveDecoder
  private given EntityDecoder[IO, SignupRequest] = jsonOf[IO, SignupRequest]
  private given EntityDecoder[IO, LoginRequest] = jsonOf[IO, LoginRequest]
  private given EntityDecoder[IO, PasswordResetRequest] = jsonOf[IO, PasswordResetRequest]
  private given EntityDecoder[IO, PasswordResetConfirmRequest] = jsonOf[IO, PasswordResetConfirmRequest]
  private given EntityDecoder[IO, ActivationConfirmRequest] = jsonOf[IO, ActivationConfirmRequest]

  private given Encoder[UserResponse] = deriveEncoder
  private given Encoder[AuthPayload] = deriveEncoder

  given Encoder[AuthResult] = Encoder.instance { result =>
    AuthPayload(result.token, UserResponse.from(result.user)).asJson
  }

  private object UserResponse {
    def from(user: User): UserResponse = UserResponse(user.id, user.email)
  }
}
