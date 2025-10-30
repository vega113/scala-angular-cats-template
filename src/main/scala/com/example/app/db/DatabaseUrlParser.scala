package com.example.app.db

import com.example.app.config.DbConfig
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object DatabaseUrlParser:
  def normalize(config: DbConfig): DbConfig =
    config.url match
      case Some(url) =>
        parse(url) match
          case Some(parsed) =>
            config.copy(
              url = Some(parsed.jdbcUrl),
              user = config.user.orElse(parsed.user),
              password = config.password.orElse(parsed.password),
              schema = config.schema.orElse(parsed.schema)
            )
          case None => config
      case None => config

  private case class Parsed(jdbcUrl: String, user: Option[String], password: Option[String], schema: Option[String])

  private def parse(url: String): Option[Parsed] =
    val lower = url.toLowerCase
    if lower.startsWith("postgres://") || lower.startsWith("postgresql://") then
      val uri = URI.create(url)
      val (userOpt, passOpt) = decodeUserInfo(uri.getUserInfo)
      val host = Option(uri.getHost).getOrElse("localhost")
      val port = if uri.getPort > 0 then s":${uri.getPort}" else ""
      val path = Option(uri.getPath).getOrElse("")
      val rawQuery = Option(uri.getQuery).filter(_.nonEmpty)
      val params = rawQuery.map(queryParams).getOrElse(Map.empty)
      val queryString = rawQuery.map(q => s"?$q").getOrElse("")
      val jdbcUrl = s"jdbc:postgresql://$host$port$path$queryString"
      val schema = params.get("currentSchema").orElse(params.get("current_schema"))
      Some(Parsed(jdbcUrl, userOpt, passOpt, schema))
    else None

  private def decodeUserInfo(userInfo: String): (Option[String], Option[String]) =
    Option(userInfo) match
      case Some(info) =>
        val parts = info.split(":", 2)
        val user = Option(parts.headOption.getOrElse("")).filter(_.nonEmpty).map(decode)
        val pass = if parts.length > 1 then Option(parts(1)).filter(_.nonEmpty).map(decode) else None
        (user, pass)
      case None => (None, None)

  private def queryParams(query: String): Map[String, String] =
    Option(query)
      .map(_.split('&').toList.flatMap { kv =>
        kv.split("=", 2) match
          case Array(k, v) => Some(k -> decode(v))
          case Array(k)    => Some(k -> "")
          case _           => None
      }.toMap)
      .getOrElse(Map.empty)

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
