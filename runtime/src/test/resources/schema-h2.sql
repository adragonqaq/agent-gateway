-- =============================================================================
-- Agent Gateway 数据库 Schema
-- 约定：所有业务表强制带 tenant_id（或复合主键含 tenant_id），实现逻辑多租户隔离。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 租户主表：SaaS 租户档案与运行配额
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_tenant (
    id                   VARCHAR(64) PRIMARY KEY,              -- 租户主键，如 ten_xxx
    name                 VARCHAR(128) NOT NULL,                -- 租户展示名
    status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / SUSPENDED
    model_tier           VARCHAR(64) NOT NULL DEFAULT 'standard', -- 模型档位（路由/计费）
    credential_binding   VARCHAR(64) NOT NULL DEFAULT 'platform', -- 凭证绑定：platform / byok 等
    max_concurrent       INT NOT NULL DEFAULT 5,               -- 该租户最大并发任务数
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP -- 创建时间
);

-- -----------------------------------------------------------------------------
-- API Key：明文仅签发时返回一次，库中只存哈希与前缀
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_api_key (
    id              VARCHAR(64) PRIMARY KEY,                 -- Key 记录 ID，如 key_xxx
    tenant_id       VARCHAR(64) NOT NULL,                    -- 所属租户
    key_hash        VARCHAR(128) NOT NULL UNIQUE,             -- SHA-256(明文 Key)
    key_prefix      VARCHAR(16) NOT NULL,                    -- 明文前缀，便于日志脱敏展示
    role            VARCHAR(32) NOT NULL DEFAULT 'RUNTIME',  -- ADMIN / RUNTIME
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / REVOKED
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gw_api_key_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);
CREATE INDEX IF NOT EXISTS idx_gw_api_key_tenant ON gw_api_key(tenant_id);

-- -----------------------------------------------------------------------------
-- MCP Server 注册：每租户可挂多个远端 MCP；Header 密文存储
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_mcp_server (
    id              VARCHAR(64) PRIMARY KEY,                 -- Server ID，如 mcp_xxx
    tenant_id       VARCHAR(64) NOT NULL,                    -- 所属租户
    name            VARCHAR(128) NOT NULL,                   -- 展示名 / AgentScope 客户端名
    transport_url   VARCHAR(512) NOT NULL,                   -- SSE 或 streamable HTTP 地址
    headers_cipher  TEXT,                                    -- AES-GCM 加密后的 Header JSON
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / DISABLED
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gw_mcp_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);
CREATE INDEX IF NOT EXISTS idx_gw_mcp_tenant ON gw_mcp_server(tenant_id);

-- -----------------------------------------------------------------------------
-- 工具策略：按工具名 ALLOW/DENY，以及是否要求人工确认
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_tool_policy (
    tenant_id       VARCHAR(64) NOT NULL,                    -- 所属租户
    tool_name       VARCHAR(128) NOT NULL,                   -- 与 MCP 发现名一致
    effect          VARCHAR(16) NOT NULL,                    -- ALLOW / DENY
    require_confirm BOOLEAN NOT NULL DEFAULT TRUE,           -- 调用前是否需确认
    PRIMARY KEY (tenant_id, tool_name),
    CONSTRAINT fk_gw_policy_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);

-- -----------------------------------------------------------------------------
-- 会话元数据（可选标题）；主键含 tenant_id 防跨租户碰撞
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_conversation (
    id              VARCHAR(64) NOT NULL,                    -- 会话 ID，如 conv_xxx
    tenant_id       VARCHAR(64) NOT NULL,                    -- 所属租户
    title           VARCHAR(256),                            -- 会话标题（可空）
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_gw_conv_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);

-- -----------------------------------------------------------------------------
-- Agent 紧凑状态快照：多轮对话恢复用
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_agent_state (
    tenant_id       VARCHAR(64) NOT NULL,                    -- 所属租户
    conversation_id VARCHAR(64) NOT NULL,                    -- 会话 ID
    payload         TEXT NOT NULL,                           -- JSON 快照正文
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, conversation_id)
);

-- -----------------------------------------------------------------------------
-- 人工确认单：危险工具挂起后由客户端 approve/deny
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_confirm (
    id              VARCHAR(64) PRIMARY KEY,                 -- 确认单 ID，如 cfm_xxx
    tenant_id       VARCHAR(64) NOT NULL,                    -- 所属租户
    task_id         VARCHAR(64) NOT NULL,                    -- 发起确认的聊天任务 ID
    conversation_id VARCHAR(64),                             -- 会话 ID，续跑时定位 Agent 状态
    tool_name       VARCHAR(128) NOT NULL,                   -- 待确认工具名
    args_json       TEXT NOT NULL,                           -- 工具参数 JSON（展示/审计）
    status          VARCHAR(32) NOT NULL,                    -- PENDING / CONFIRMED / DENIED / EXPIRED
    expires_at      TIMESTAMP NOT NULL,                      -- 过期时间（惰性过期）
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_confirm_tenant_task ON gw_confirm(tenant_id, task_id);

-- -----------------------------------------------------------------------------
-- 审计事件：谁在何时做了什么（确认、取消、对话完成等）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_audit_event (
    id              VARCHAR(64) PRIMARY KEY,                 -- 事件 ID
    tenant_id       VARCHAR(64) NOT NULL,                    -- 所属租户
    event_type      VARCHAR(64) NOT NULL,                    -- 如 CONFIRM_APPROVE / CHAT_COMPLETE
    ref_id          VARCHAR(64),                             -- 关联对象 ID（taskId / confirmId）
    detail_json     TEXT,                                    -- 补充细节 JSON
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_audit_tenant ON gw_audit_event(tenant_id);

-- -----------------------------------------------------------------------------
-- 用量事件：按租户累计 runs / tokens 等指标
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_usage_event (
    id              VARCHAR(64) PRIMARY KEY,                 -- 事件 ID
    tenant_id       VARCHAR(64) NOT NULL,                    -- 所属租户
    task_id         VARCHAR(64),                             -- 可选关联任务
    metric          VARCHAR(64) NOT NULL,                    -- 指标名，如 runs
    amount          BIGINT NOT NULL,                         -- 增量
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_usage_tenant ON gw_usage_event(tenant_id);
