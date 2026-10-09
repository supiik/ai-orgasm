ALTER TABLE playlists
    ADD COLUMN spotify_playlist_id VARCHAR(255) NULL,
    ADD COLUMN spotify_playlist_url VARCHAR(2048) NULL;
