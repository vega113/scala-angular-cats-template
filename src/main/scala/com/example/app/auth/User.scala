package com.example.app.auth

import java.time.Instant
import java.util.UUID

final case class User(
    id: UUID,
    email: String,
    passwordHash: String,
    createdAt: Instant,
    updatedAt: Instant
)
