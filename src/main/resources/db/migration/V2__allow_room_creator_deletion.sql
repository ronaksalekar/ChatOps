-- The room creator may be deleted; retain the room and clear its creator reference.
ALTER TABLE rooms ALTER COLUMN created_by DROP NOT NULL;
