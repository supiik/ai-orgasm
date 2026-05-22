CREATE TABLE IF NOT EXISTS tenants
(
    id         BIGINT      NOT NULL,
    slug       VARCHAR(50) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenants_slug (slug)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO tenants (id, slug, name) VALUES (1, 'default', 'Default');
