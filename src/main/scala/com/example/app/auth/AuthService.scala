package com.example.app.auth

import cats.effect.Sync
import cats.syntax.all._
import com.example.app.security.PasswordHasher
import com.example.app.security.jwt.{JwtPayload, JwtService}
import java.util.UUID

sealed abstract class AuthError(message: String) extends RuntimeException(message)
object AuthError {
  case object EmailAlreadyExists extends AuthError("email already exists")
  case object InvalidCredentials extends AuthError("invalid credentials")
}

final case class AuthResult(user: User, token: String)

trait AuthService[F[_]] {
  def signup(email: String, password: String): F[AuthResult]
  def login(email: String, password: String): F[AuthResult]
  def currentUser(userId: UUID): F[Option[User]]
  def authenticate(token: String): F[Option[JwtPayload]]
}

object AuthService {
  def apply[F[_]: Sync](repo: UserRepository[F], hasher: PasswordHasher[F], jwt: JwtService[F]): AuthService[F] =
    new AuthService[F] {
      private val F = Sync[F]

      override def signup(email: String, password: String): F[AuthResult] =
        val normalized = normalizeEmail(email)
        for
          existing <- repo.findByEmail(normalized)
          _        <- existing match
                        case Some(_) => F.raiseError(AuthError.EmailAlreadyExists)
                        case None    => F.unit
          hash     <- hasher.hash(password)
          user     <- repo.create(normalized, hash)
          token    <- jwt.generate(JwtPayload(user.id, user.email))
        yield AuthResult(user, token)

      override def login(email: String, password: String): F[AuthResult] =
        val normalized = normalizeEmail(email)
        for
          maybeUser <- repo.findByEmail(normalized)
          user      <- maybeUser match
                          case Some(value) => F.pure(value)
                          case None        => F.raiseError(AuthError.InvalidCredentials)
          valid     <- hasher.verify(password, user.passwordHash)
          _         <- if valid then F.unit else F.raiseError(AuthError.InvalidCredentials)
          token     <- jwt.generate(JwtPayload(user.id, user.email))
        yield AuthResult(user, token)

      override def currentUser(userId: UUID): F[Option[User]] =
        repo.findById(userId)

      override def authenticate(token: String): F[Option[JwtPayload]] =
        jwt.verify(token)

      private def normalizeEmail(value: String): String =
        value.trim.toLowerCase
    }
}
