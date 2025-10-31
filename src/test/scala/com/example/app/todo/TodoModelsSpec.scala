package com.example.app.todo

import cats.syntax.either._
import com.example.app.todo.TodoModels._
import io.circe.parser.decode
import io.circe.syntax._
import munit.FunSuite

import java.time.Instant
import java.util.UUID

class TodoModelsSpec extends FunSuite {
  private val todo = Todo(
    id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    userId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    title = "Test",
    description = Some("desc"),
    dueDate = Some(Instant.parse("2025-10-31T00:00:00Z")),
    completed = false,
    createdAt = Instant.parse("2025-10-31T00:00:00Z"),
    updatedAt = Instant.parse("2025-10-31T00:00:00Z")
  )

  test("Todo encodes and decodes to JSON") {
    val json = todo.asJson
    val decoded = decode[Todo](json.noSpaces)
    assertEquals(decoded.toOption, Some(todo))
  }

  test("TodoCreate decoding requires title") {
    val json = """{"title": "New", "description": null}"""
    val decoded = decode[TodoCreate](json)
    assertEquals(decoded.toOption.map(_.title), Some("New"))
  }

  test("TodoUpdate decodes null to clear patch") {
    val json = """{"description": null}"""
    val decoded = decode[TodoUpdate](json)
    val expected = TodoUpdate(None, FieldPatch.Clear, FieldPatch.Unchanged, None)
    assertEquals(decoded, Right(expected))
  }

  test("TodoUpdate encodes FieldPatch.Clear as null") {
    val update = TodoUpdate(None, FieldPatch.Clear, FieldPatch.Unchanged, None)
    val json = update.asJson.noSpaces
    assertEquals(json, """{"description":null}""")
  }

  test("TodoUpdate leaves missing fields unchanged") {
    val json = "{}"
    val decoded = decode[TodoUpdate](json)
    val expected = TodoUpdate(None, FieldPatch.Unchanged, FieldPatch.Unchanged, None)
    assertEquals(decoded, Right(expected))
  }

  test("TodoUpdate encode omits unchanged fields") {
    val update = TodoUpdate(None, FieldPatch.Unchanged, FieldPatch.Unchanged, None)
    assertEquals(update.asJson.noSpaces, "{}")
  }
}
