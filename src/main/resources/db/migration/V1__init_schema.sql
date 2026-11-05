-- ============================================================
-- V1__init_schema.sql
-- ChatOps initial database schema
-- ============================================================

-- ===== USERS =====
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    avatar_url    VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email    ON users (email);
CREATE INDEX idx_users_username ON users (username);

-- ===== ROOMS =====
CREATE TABLE rooms (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_by  BIGINT       NOT NULL REFERENCES users (id) ON DELETE SET NULL,
    is_private  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rooms_created_by ON rooms (created_by);

-- ===== ROOM MEMBERS =====
CREATE TYPE room_role AS ENUM ('MEMBER', 'ADMIN');

CREATE TABLE room_members (
    room_id   BIGINT    NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    user_id   BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      room_role NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (room_id, user_id)
);

CREATE INDEX idx_room_members_user_id ON room_members (user_id);

-- ===== MESSAGES =====
-- cursor_id is a monotonically increasing BIGSERIAL used for efficient cursor pagination
CREATE TABLE messages (
    id                BIGSERIAL PRIMARY KEY,
    cursor_id         BIGSERIAL NOT NULL UNIQUE,          -- pagination cursor
    room_id           BIGINT    REFERENCES rooms (id) ON DELETE CASCADE,
    dm_recipient_id   BIGINT    REFERENCES users (id) ON DELETE CASCADE,  -- NULL for room messages
    sender_id         BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content           TEXT      NOT NULL,
    message_type      VARCHAR(20) NOT NULL DEFAULT 'TEXT', -- TEXT | JOIN | LEAVE | SYSTEM
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_message_target CHECK (
        (room_id IS NOT NULL AND dm_recipient_id IS NULL) OR
        (room_id IS NULL AND dm_recipient_id IS NOT NULL)
    )
);

CREATE INDEX idx_messages_room_cursor   ON messages (room_id, cursor_id DESC);
CREATE INDEX idx_messages_dm_cursor     ON messages (dm_recipient_id, cursor_id DESC)
    WHERE dm_recipient_id IS NOT NULL;
CREATE INDEX idx_messages_sender        ON messages (sender_id);

-- ===== REFRESH TOKENS =====
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- ===== AUTO-UPDATE updated_at =====
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_rooms_updated_at
    BEFORE UPDATE ON rooms
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
