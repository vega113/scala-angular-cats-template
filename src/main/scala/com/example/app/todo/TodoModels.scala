package com.example.app.todo

import io.circe.{Codec, Decoder, Encoder, Json, JsonObject}
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.time.Instant
import java.util.UUID

final case class Todo(
  id: UUID,
  userId: UUID,
  title: String,
  description: Option[String],
  dueDate: Option[Instant],
  completed: Boolean,
  createdAt: Instant,
  updatedAt: Instant
)

final case class TodoCreate(
  title: String,
  description: Option[String],
  dueDate: Option[Instant]
)

final case class TodoUpdate(
  title: Option[String],
  description: FieldPatch[String],
  dueDate: FieldPatch[Instant],
  completed: Option[Boolean]
)

object TodoModels {
  implicit val todoCodec: Codec[Todo] = deriveCodec
  implicit val todoCreateDecoder: Decoder[TodoCreate] = deriveDecoder
  implicit val todoCreateEncoder: Encoder[TodoCreate] = deriveEncoder
  implicit val todoUpdateDecoder: Decoder[TodoUpdate] = Decoder.instance { cursor =>
    def decodePatch[A](field: String)(using Decoder[A]): Decoder.Result[FieldPatch[A]] =
      val down = cursor.downField(field)
      if !down.succeeded then Right(FieldPatch.Unchanged)
      else if down.focus.exists(_.isNull) then Right(FieldPatch.Clear)
      else down.as[A].map(FieldPatch.Set(_))

    for
      title <- cursor.get[Option[String]]("title")
      description <- decodePatch[String]("description")
      dueDate <- decodePatch[Instant]("dueDate")
      completed <- cursor.get[Option[Boolean]]("completed")
    yield TodoUpdate(title, description, dueDate, completed)
  }

  implicit val todoUpdateEncoder: Encoder.AsObject[TodoUpdate] = Encoder.AsObject.instance {
    update =>
      val base = List(
        update.title.map(t => "title" -> t.asJson),
        update.completed.map(c => "completed" -> c.asJson)
      ).flatten

      val desc = update.description match
        case FieldPatch.Unchanged => Nil
        case FieldPatch.Set(value) => List("description" -> value.asJson)
        case FieldPatch.Clear => List("description" -> io.circe.Json.Null)

      val due = update.dueDate match
        case FieldPatch.Unchanged => Nil
        case FieldPatch.Set(value) => List("dueDate" -> value.asJson)
        case FieldPatch.Clear => List("dueDate" -> io.circe.Json.Null)

      JsonObject.fromIterable(base ++ desc ++ due)
  }
}
