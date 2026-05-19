ALTER TABLE playlists
    ADD COLUMN lead_contributor_id BIGINT NULL,
    ADD COLUMN deadline             TIMESTAMP NULL,
    ADD CONSTRAINT fk_playlist_lead_contributor
        FOREIGN KEY (lead_contributor_id) REFERENCES contributors(id);

CREATE TABLE nominations (
    id              BIGINT      NOT NULL,
    playlist_id     BIGINT      NOT NULL,
    song_id         BIGINT      NOT NULL,
    nominated_by_id BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version         INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_nomination_playlist_song (playlist_id, song_id),
    CONSTRAINT fk_nomination_playlist    FOREIGN KEY (playlist_id)     REFERENCES playlists(id),
    CONSTRAINT fk_nomination_song        FOREIGN KEY (song_id)         REFERENCES songs(id),
    CONSTRAINT fk_nomination_contributor FOREIGN KEY (nominated_by_id) REFERENCES contributors(id)
) ENGINE=InnoDB;
