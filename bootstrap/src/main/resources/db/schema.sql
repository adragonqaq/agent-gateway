CREATE TABLE IF NOT EXISTS gw_tenant (
    id                   VARCHAR(64) PRIMARY KEY,
    name                 VARCHAR(128) NOT NULL,
    status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    model_tier           VARCHAR(64) NOT NULL DEFAULT 'standard',
    credential_binding   VARCHAR(64) NOT NULL DEFAULT 'platform',
    max_concurrent       INT NOT NULL DEFAULT 5,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gw_api_key (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    key_hash        VARCHAR(128) NOT NULL UNIQUE,
    key_prefix      VARCHAR(16) NOT NULL,
    role            VARCHAR(32) NOT NULL DEFAULT 'RUNTIME',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gw_api_key_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);
CREATE INDEX IF NOT EXISTS idx_gw_api_key_tenant ON gw_api_key(tenant_id);
