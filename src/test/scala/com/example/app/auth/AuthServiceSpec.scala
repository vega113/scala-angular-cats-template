package com.example.app.auth

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._
import com.example.app.security.PasswordHasher
import com.example.app.security.jwt.{JwtPayload, JwtService}
import munit.CatsEffectSuite

import java.time.Instant
import java.util.UUID

class AuthServiceSpec extends CatsEffectSuite:
  private val passwordHasher = PasswordHasher.bcrypt[IO]()
  private val staticToken = "static-token"

  private val jwtService = new JwtService[IO]:
    override def generate(payload: JwtPayload): IO[String] = IO.pure(staticToken)
    override def verify(token: String): IO[Option[JwtPayload]] =
      if token == staticToken then IO.pure(Some(JwtPayload(UUID.randomUUID(), "user@example.com")))
      else IO.pure(None)

  private def inMemoryRepo: IO[(UserRepository[IO], Ref[IO, Map[UUID, User]])] =
    Ref.of[IO, Map[UUID, User]](Map.empty).map { ref =>
      (new InMemoryRepo(ref), ref)
    }

  test("signup stores normalized email and returns token") {
    inMemoryRepo.flatMap { case (repo, ref) =>
      val service = AuthService[IO](repo, passwordHasher, jwtService)
      for
        result <- service.signup("User@Example.com ", "secret")
        stored <- ref.get
      yield {
        assertEquals(result.user.email, "user@example.com")
        assertEquals(result.token, staticToken)
        assert(stored.values.exists(_.email == "user@example.com"))
      }
    }
  }

  test("signup rejects duplicate email") {
    inMemoryRepo.flatMap { case (repo, _) =>
      val service = AuthService[IO](repo, passwordHasher, jwtService)
      val program = for
        _ <- service.signup("duplicate@example.com", "secret")
        _ <- service.signup("duplicate@example.com", "secret")
      yield ()

      program.attempt.map(res => assertEquals(res.left.map(_.getClass), Left(classOf[AuthError.EmailAlreadyExists.type])))
    }
  }

  test("login rejects unknown email") {
    inMemoryRepo.flatMap { case (repo, _) =>
      val service = AuthService[IO](repo, passwordHasher, jwtService)
      service.login("missing@example.com", "secret").attempt.map { res =>
        assertEquals(res.left.map(_.getClass), Left(classOf[AuthError.InvalidCredentials.type]))
      }
    }
  }

  test("login succeeds with correct credentials") {
    inMemoryRepo.flatMap { case (repo, _) =>
      val service = AuthService[IO](repo, passwordHasher, jwtService)
      for
        signup <- service.signup("login@example.com", "secret")
        login  <- service.login("login@example.com", "secret")
      yield assertEquals(signup.user.email, login.user.email)
    }
  }

  private final class InMemoryRepo(ref: Ref[IO, Map[UUID, User]]) extends UserRepository[IO]:
    override def create(email: String, passwordHash: String): IO[User] =
      for
        id  <- IO(UUID.randomUUID())
        now <- IO.realTimeInstant
        user = User(id, email, passwordHash, now, now)
        _   <- ref.update(_ + (id -> user))
      yield user

    override def findByEmail(email: String): IO[Option[User]] =
      ref.get.map(_.values.find(_.email == email))

    override def findById(id: UUID): IO[Option[User]] =
      ref.get.map(_.get(id))
