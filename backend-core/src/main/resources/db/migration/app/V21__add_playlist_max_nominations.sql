ALTER TABLE playlists
    ADD COLUMN max_nominations_per_contributor INT NOT NULL DEFAULT 1;
