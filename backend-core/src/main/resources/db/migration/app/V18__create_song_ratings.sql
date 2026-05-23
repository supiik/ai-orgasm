CREATE TABLE song_rating (
    id               BIGINT    NOT NULL,
    tenant_id        BIGINT    NOT NULL,
    playlist_id      BIGINT    NOT NULL,
    contributor_id   BIGINT    NOT NULL,
    nomination_id    BIGINT    NOT NULL,
    points           INT       NOT NULL,
    version          BIGINT    NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    deleted_at       TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_song_rating (playlist_id, contributor_id, nomination_id)
);
