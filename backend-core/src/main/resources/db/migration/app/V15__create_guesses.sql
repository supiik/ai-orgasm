CREATE TABLE guesses (
    id                     BIGINT    NOT NULL,
    tenant_id              BIGINT    NOT NULL,
    playlist_id            BIGINT    NOT NULL,
    nomination_id          BIGINT    NOT NULL,
    guesser_contributor_id BIGINT    NOT NULL,
    guessed_contributor_id BIGINT    NOT NULL,
    version                BIGINT    NOT NULL DEFAULT 0,
    created_at             TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP NOT NULL,
    deleted_at             TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_guess_nomination_guesser (nomination_id, guesser_contributor_id)
);
