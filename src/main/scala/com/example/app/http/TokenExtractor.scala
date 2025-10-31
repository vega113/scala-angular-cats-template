package com.example.app.http

import org.http4s.{AuthScheme, Credentials, Request}
import org.http4s.headers.Authorization

object TokenExtractor:
  def bearerToken[F[_]](req: Request[F]): Option[String] =
    req.headers.get[Authorization].collect {
      case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => token
    }
