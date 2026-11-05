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

CREATE TABLE IF NOT EXISTS gw_mcp_server (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    transport_url   VARCHAR(512) NOT NULL,
    headers_cipher  TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gw_mcp_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);
CREATE INDEX IF NOT EXISTS idx_gw_mcp_tenant ON gw_mcp_server(tenant_id);

CREATE TABLE IF NOT EXISTS gw_tool_policy (
    tenant_id       VARCHAR(64) NOT NULL,
    tool_name       VARCHAR(128) NOT NULL,
    effect          VARCHAR(16) NOT NULL,
    require_confirm BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, tool_name),
    CONSTRAINT fk_gw_policy_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);

CREATE TABLE IF NOT EXISTS gw_conversation (
    id              VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    title           VARCHAR(256),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_gw_conv_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);

CREATE TABLE IF NOT EXISTS gw_agent_state (
    tenant_id       VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    payload         TEXT NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, conversation_id)
);

CREATE TABLE IF NOT EXISTS gw_confirm (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL,
    tool_name       VARCHAR(128) NOT NULL,
    args_json       TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_confirm_tenant_task ON gw_confirm(tenant_id, task_id);

CREATE TABLE IF NOT EXISTS gw_audit_event (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    ref_id          VARCHAR(64),
    detail_json     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_audit_tenant ON gw_audit_event(tenant_id);

CREATE TABLE IF NOT EXISTS gw_usage_event (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64),
    metric          VARCHAR(64) NOT NULL,
    amount          BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_usage_tenant ON gw_usage_event(tenant_id);
