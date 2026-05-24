CREATE TABLE IF NOT EXISTS organization
(
    id   BIGINT       NOT NULL,
    slug VARCHAR(50)  NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_organization_slug (slug)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO organization (id, slug, name)
VALUES (1, 'default', 'Default Organization');
