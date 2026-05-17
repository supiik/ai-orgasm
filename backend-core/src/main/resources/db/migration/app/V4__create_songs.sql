CREATE TABLE songs
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    artist       VARCHAR(255) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    album        VARCHAR(255),
    release_year INT,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at   TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
