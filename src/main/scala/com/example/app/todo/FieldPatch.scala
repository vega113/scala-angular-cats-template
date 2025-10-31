package com.example.app.todo

import io.circe.{ACursor, Decoder, Encoder, Json}

sealed trait FieldPatch[+A]
object FieldPatch {
  case object Unchanged extends FieldPatch[Nothing]
  final case class Set[A](value: A) extends FieldPatch[A]
  case object Clear extends FieldPatch[Nothing]

  def unchanged[A]: FieldPatch[A] = Unchanged

  def fromOptionOption[A](opt: Option[Option[A]]): FieldPatch[A] =
    opt match
      case None => Unchanged
      case Some(Some(value)) => Set(value)
      case Some(None) => Clear

  private def decodePatch[A](cursor: ACursor)(using Decoder[A]): Decoder.Result[FieldPatch[A]] =
    cursor.success match
      case None => Right(Unchanged)
      case Some(c) =>
        c.value match
          case Json.Null => Right(Clear)
          case _         => c.as[A].map(Set(_))

  given [A](using encoder: Encoder[A]): Encoder[FieldPatch[A]] = Encoder.instance {
    case Unchanged => Json.Null
    case Set(value) => encoder(value)
    case Clear => Json.Null
  }

  given [A](using Decoder[A]): Decoder[FieldPatch[A]] = Decoder.instance { cursor =>
    decodePatch(cursor)
  }
}
