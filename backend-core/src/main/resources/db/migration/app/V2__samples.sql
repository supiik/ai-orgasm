CREATE TABLE IF NOT EXISTS samples
(
    id           BIGINT          NOT NULL,
    tenant_id    BIGINT          NOT NULL,
    name         VARCHAR(255)    NOT NULL,
    description  TEXT,
    email        VARCHAR(255),
    quantity     INT             NOT NULL DEFAULT 0,
    large_number BIGINT          NOT NULL DEFAULT 0,
    rating       DOUBLE          NOT NULL DEFAULT 0,
    price        DECIMAL(19, 4),
    active       BOOLEAN         NOT NULL DEFAULT TRUE,
    birth_date   DATE,
    scheduled_at DATETIME(6),
    status       VARCHAR(50)     NOT NULL DEFAULT 'DRAFT',
    notes        TEXT,
    version      BIGINT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at   TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_samples_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
