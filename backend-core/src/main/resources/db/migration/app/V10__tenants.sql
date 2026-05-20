CREATE TABLE tenants (
    id   BIGINT       NOT NULL,
    slug VARCHAR(50)  NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenant_slug (slug)
) ENGINE=InnoDB;

INSERT INTO tenants (id, slug, name) VALUES (1, 'default', 'Default Tenant');
