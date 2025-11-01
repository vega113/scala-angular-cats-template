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
  case object AccountNotActivated extends AuthError("account_not_activated")
}

final case class AuthResult(user: User, token: String)

trait AuthService[F[_]] {
  def signup(email: String, password: String): F[User]
  def login(email: String, password: String): F[AuthResult]
  def issueToken(user: User): F[AuthResult]
  def currentUser(userId: UUID): F[Option[User]]
  def authenticate(token: String): F[Option[JwtPayload]]
}

object AuthService {
  def apply[F[_]: Sync](
    repo: UserRepository[F],
    hasher: PasswordHasher[F],
    jwt: JwtService[F],
    activationService: AccountActivationService[F]
  ): AuthService[F] =
    new AuthService[F] {
      private val F = Sync[F]

      override def signup(email: String, password: String): F[User] =
        val normalized = normalizeEmail(email)
        for
          existing <- repo.findByEmail(normalized)
          _ <- existing match
            case Some(_) => F.raiseError(AuthError.EmailAlreadyExists)
            case None => F.unit
          hash <- hasher.hash(password)
          user <- repo.create(normalized, hash)
          _ <- activationService.issueToken(user)
        yield user

      override def login(email: String, password: String): F[AuthResult] =
        val normalized = normalizeEmail(email)
        for
          maybeUser <- repo.findByEmail(normalized)
          result <- maybeUser match
            case Some(user) =>
              hasher.verify(password, user.passwordHash).flatMap { valid =>
                if !valid then F.raiseError[AuthResult](AuthError.InvalidCredentials)
                else if !user.activated then F.raiseError[AuthResult](AuthError.AccountNotActivated)
                else issueToken(user)
              }
            case None =>
              PasswordHasher.constantTimeFailure[F](password) *> F.raiseError[AuthResult](
                AuthError.InvalidCredentials
              )
        yield result

      override def issueToken(user: User): F[AuthResult] =
        jwt.generate(JwtPayload(user.id, user.email)).map(AuthResult(user, _))

      override def currentUser(userId: UUID): F[Option[User]] =
        repo.findById(userId)

      override def authenticate(token: String): F[Option[JwtPayload]] =
        jwt.verify(token)

      private def normalizeEmail(value: String): String =
        value.trim.toLowerCase
    }
}
