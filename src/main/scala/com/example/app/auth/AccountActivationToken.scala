package com.example.app.auth

import java.time.Instant
import java.util.UUID

final case class AccountActivationToken(
  id: UUID,
  userId: UUID,
  tokenHash: String,
  expiresAt: Instant,
  consumedAt: Option[Instant],
  createdAt: Instant
)
