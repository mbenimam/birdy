-- Birdy relational schema (MySQL 8.4+). Idempotent: safe to run on every start.
-- Posts, comments, likes and chat messages live in MongoDB.

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(30)  NOT NULL,
  email         VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  birthday      DATE         NOT NULL,
  created_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Only the SHA-256 digest (hex) of a session token is stored, never the token itself.
CREATE TABLE IF NOT EXISTS user_sessions (
  token_digest CHAR(64)    CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id      BIGINT      NOT NULL,
  created_at   DATETIME(3) NOT NULL,
  expires_at   DATETIME(3) NOT NULL,
  PRIMARY KEY (token_digest),
  KEY ix_user_sessions_user (user_id),
  KEY ix_user_sessions_expires (expires_at),
  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS follows (
  follower_id BIGINT      NOT NULL,
  followed_id BIGINT      NOT NULL,
  created_at  DATETIME(3) NOT NULL,
  PRIMARY KEY (follower_id, followed_id),
  KEY ix_follows_followed (followed_id),
  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_follows_followed FOREIGN KEY (followed_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
