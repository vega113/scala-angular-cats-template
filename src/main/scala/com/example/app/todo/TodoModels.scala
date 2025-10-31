package com.example.app.todo

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto._
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
    description: Option[Option[String]],
    dueDate: Option[Option[Instant]],
    completed: Option[Boolean]
)

object TodoModels {
  implicit val todoCodec: Codec[Todo] = deriveCodec
  implicit val todoCreateDecoder: Decoder[TodoCreate] = deriveDecoder
  implicit val todoCreateEncoder: Encoder[TodoCreate] = deriveEncoder
  implicit val todoUpdateDecoder: Decoder[TodoUpdate] = deriveDecoder
  implicit val todoUpdateEncoder: Encoder[TodoUpdate] = deriveEncoder
}
