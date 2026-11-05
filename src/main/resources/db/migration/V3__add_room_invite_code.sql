-- V3: Add unique invite_code to rooms for private room sharing and unguessable room identifiers
ALTER TABLE rooms ADD COLUMN invite_code VARCHAR(64);

-- Populate existing rooms with random 8-character hex codes
UPDATE rooms SET invite_code = substring(md5(random()::text || id::text) from 1 for 8) WHERE invite_code IS NULL;

ALTER TABLE rooms ALTER COLUMN invite_code SET NOT NULL;
ALTER TABLE rooms ADD CONSTRAINT uq_rooms_invite_code UNIQUE (invite_code);
CREATE INDEX idx_rooms_invite_code ON rooms (invite_code);
