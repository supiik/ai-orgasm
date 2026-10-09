CREATE TABLE spotify_connections (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL,
    contributor_id  BIGINT       NOT NULL,
    spotify_user_id VARCHAR(255) NOT NULL,
    access_token    TEXT         NOT NULL,
    refresh_token   TEXT         NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    version         INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_spotify_connection_contributor (contributor_id),
    CONSTRAINT fk_spotify_connection_contributor FOREIGN KEY (contributor_id) REFERENCES contributors(id)
) ENGINE=InnoDB;
