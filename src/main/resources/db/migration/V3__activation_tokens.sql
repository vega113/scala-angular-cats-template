ALTER TABLE users
  ADD COLUMN activated BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET activated = TRUE;

CREATE TABLE activation_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_activation_tokens_user ON activation_tokens(user_id) WHERE consumed_at IS NULL;
CREATE INDEX idx_activation_tokens_token_hash ON activation_tokens(token_hash);
