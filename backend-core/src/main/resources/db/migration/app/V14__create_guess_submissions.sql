CREATE TABLE guess_submissions (
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL,
    playlist_id BIGINT       NOT NULL,
    contributor_id BIGINT    NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    deleted_at  TIMESTAMP    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_guess_sub_playlist_contributor (playlist_id, contributor_id)
);
