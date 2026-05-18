-- Replace auto-increment BIGINT primary keys with TSID VARCHAR(25) columns.
-- IDs are now assigned by the application (@PrePersist) before INSERT.
ALTER TABLE playlists
    MODIFY COLUMN id VARCHAR(25) NOT NULL;

ALTER TABLE songs
    MODIFY COLUMN id VARCHAR(25) NOT NULL;

ALTER TABLE contributors
    MODIFY COLUMN id VARCHAR(25) NOT NULL;
