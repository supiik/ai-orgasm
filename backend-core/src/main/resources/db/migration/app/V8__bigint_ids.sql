-- Revert from VARCHAR(25) to BIGINT. IDs are now application-generated sortable longs
-- (42-bit ms timestamp + 22-bit random). Existing VARCHAR data is incompatible so tables
-- are truncated first. Foreign-key relations use BIGINT directly.
TRUNCATE TABLE contributors;
TRUNCATE TABLE songs;
TRUNCATE TABLE playlists;

ALTER TABLE playlists  MODIFY COLUMN id BIGINT NOT NULL;
ALTER TABLE songs      MODIFY COLUMN id BIGINT NOT NULL;
ALTER TABLE contributors MODIFY COLUMN id BIGINT NOT NULL;
