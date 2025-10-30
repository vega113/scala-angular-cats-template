package com.example.app.security.jwt

import cats.effect.Sync
import cats.syntax.all._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.syntax._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import com.example.app.config.JwtConfig
import java.util.UUID

final case class JwtPayload(userId: UUID, email: String)

trait JwtService[F[_]] {
  def generate(payload: JwtPayload): F[String]
  def verify(token: String): F[Option[JwtPayload]]
}

object JwtService {
  def apply[F[_]: Sync](config: JwtConfig): F[JwtService[F]] =
    config.secret match
      case Some(secret) =>
        val algo = JwtAlgorithm.HS256
        for {
          logger <- Slf4jLogger.create[F]
        } yield new JwtService[F] {
          private val ttlSeconds = config.ttl.toLong

          override def generate(payload: JwtPayload): F[String] =
            for
              now <- Sync[F].realTimeInstant
              nowSec = now.getEpochSecond
              claim = JwtClaim(
                subject = Some(payload.userId.toString),
                content = payload.asJson.noSpaces,
                issuedAt = Some(nowSec),
                expiration = Some(nowSec + ttlSeconds)
              )
            yield JwtCirce.encode(claim, secret, algo)

          override def verify(token: String): F[Option[JwtPayload]] =
            Sync[F].delay(JwtCirce.decode(token, secret, Seq(algo))).flatMap {
              case scala.util.Success(decoded) =>
                parsePayload(decoded).pure[F]
              case scala.util.Failure(err) =>
                logger.warn(err)("Failed to decode JWT token").as(None)
            }

          private def parsePayload(claim: JwtClaim): Option[JwtPayload] =
            for
              subject <- claim.subject
              uuid    <- scala.util.Try(UUID.fromString(subject)).toOption
              payload <- io.circe.parser.decode[JwtPayload](claim.content).toOption
              _       <- Option.when(payload.userId == uuid)(())
            yield payload
        }
      case None => Sync[F].raiseError(new IllegalStateException("JWT secret is not configured"))

  private given Encoder[JwtPayload] = new Encoder[JwtPayload] {
    override def apply(a: JwtPayload): Json =
      Json.obj(
        "userId" -> a.userId.toString.asJson,
        "email"  -> a.email.asJson
      )
  }

  private given Decoder[JwtPayload] = Decoder.instance { cursor =>
    for
      userStr <- cursor.downField("userId").as[String]
      user <- Either
        .catchNonFatal(UUID.fromString(userStr))
        .leftMap(_ => DecodingFailure("invalid uuid", cursor.downField("userId").history))
      email <- cursor.downField("email").as[String]
    yield JwtPayload(user, email)
  }
}
