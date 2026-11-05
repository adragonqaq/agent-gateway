-- =============================================================================
-- Agent Gateway 数据库 Schema
-- 约定：
--   1. 所有业务表强制带 tenant_id（或复合主键含 tenant_id），实现逻辑多租户隔离
--   2. 每张表必须有 COMMENT ON TABLE；每个字段必须有 COMMENT ON COLUMN（仓库规范强制）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 租户主表：SaaS 租户档案与运行配额
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_tenant (
    id                   VARCHAR(64) PRIMARY KEY,
    name                 VARCHAR(128) NOT NULL,
    status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    model_tier           VARCHAR(64) NOT NULL DEFAULT 'standard',
    credential_binding   VARCHAR(64) NOT NULL DEFAULT 'platform',
    max_concurrent       INT NOT NULL DEFAULT 5,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE gw_tenant IS '租户主表：SaaS 租户档案与运行配额';
COMMENT ON COLUMN gw_tenant.id IS '租户主键，如 ten_xxx';
COMMENT ON COLUMN gw_tenant.name IS '租户展示名';
COMMENT ON COLUMN gw_tenant.status IS '租户状态：ACTIVE / SUSPENDED';
COMMENT ON COLUMN gw_tenant.model_tier IS '模型档位（路由/计费）';
COMMENT ON COLUMN gw_tenant.credential_binding IS '凭证绑定方式：platform / byok 等';
COMMENT ON COLUMN gw_tenant.max_concurrent IS '该租户最大并发任务数';
COMMENT ON COLUMN gw_tenant.created_at IS '创建时间';

-- -----------------------------------------------------------------------------
-- API Key：明文仅签发时返回一次，库中只存哈希与前缀
-- -----------------------------------------------------------------------------
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
COMMENT ON TABLE gw_api_key IS 'API Key：仅存哈希与前缀，明文只在签发响应中出现一次';
COMMENT ON COLUMN gw_api_key.id IS 'Key 记录 ID，如 key_xxx';
COMMENT ON COLUMN gw_api_key.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN gw_api_key.key_hash IS 'SHA-256(明文 Key)，用于鉴权比对';
COMMENT ON COLUMN gw_api_key.key_prefix IS '明文前缀，便于日志脱敏展示';
COMMENT ON COLUMN gw_api_key.role IS '角色：ADMIN / RUNTIME';
COMMENT ON COLUMN gw_api_key.status IS '状态：ACTIVE / REVOKED';
COMMENT ON COLUMN gw_api_key.created_at IS '创建时间';

-- -----------------------------------------------------------------------------
-- MCP Server 注册：每租户可挂多个远端 MCP；Header 密文存储
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_mcp_server (
    id              VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    transport_url   VARCHAR(512) NOT NULL,
    headers_cipher  TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_gw_mcp_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);
-- 兼容旧库：曾用 id 全局主键，多租户无法登记相同 server id（如 demo-mcp）
ALTER TABLE gw_mcp_server DROP CONSTRAINT IF EXISTS gw_mcp_server_pkey;
ALTER TABLE gw_mcp_server ADD PRIMARY KEY (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_gw_mcp_tenant ON gw_mcp_server(tenant_id);
COMMENT ON TABLE gw_mcp_server IS 'MCP Server 注册表：租户级远端工具服务连接配置';
COMMENT ON COLUMN gw_mcp_server.id IS 'Server ID，如 mcp_xxx / demo-mcp';
COMMENT ON COLUMN gw_mcp_server.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN gw_mcp_server.name IS '展示名，同时作为 AgentScope MCP 客户端名';
COMMENT ON COLUMN gw_mcp_server.transport_url IS '传输地址（含 /mcp 或 /sse）';
COMMENT ON COLUMN gw_mcp_server.headers_cipher IS 'AES-GCM 加密后的请求头 JSON；可空';
COMMENT ON COLUMN gw_mcp_server.status IS '状态：ACTIVE / DISABLED';
COMMENT ON COLUMN gw_mcp_server.updated_at IS '最后更新时间';

-- -----------------------------------------------------------------------------
-- 工具策略：按工具名 ALLOW/DENY，以及是否要求人工确认
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_tool_policy (
    tenant_id       VARCHAR(64) NOT NULL,
    tool_name       VARCHAR(128) NOT NULL,
    effect          VARCHAR(16) NOT NULL,
    require_confirm BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, tool_name),
    CONSTRAINT fk_gw_policy_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);
COMMENT ON TABLE gw_tool_policy IS '工具策略：按工具名控制 ALLOW/DENY 与是否人工确认';
COMMENT ON COLUMN gw_tool_policy.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN gw_tool_policy.tool_name IS '工具名，与 MCP listTools 名称一致';
COMMENT ON COLUMN gw_tool_policy.effect IS '策略效果：ALLOW / DENY';
COMMENT ON COLUMN gw_tool_policy.require_confirm IS '调用前是否需要人工确认';

-- -----------------------------------------------------------------------------
-- 会话元数据（可选标题）；主键含 tenant_id 防跨租户碰撞
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_conversation (
    id              VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    title           VARCHAR(256),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_gw_conv_tenant FOREIGN KEY (tenant_id) REFERENCES gw_tenant(id)
);
COMMENT ON TABLE gw_conversation IS '会话元数据：按租户隔离的对话档案';
COMMENT ON COLUMN gw_conversation.id IS '会话 ID，如 conv_xxx';
COMMENT ON COLUMN gw_conversation.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN gw_conversation.title IS '会话标题；可空';
COMMENT ON COLUMN gw_conversation.created_at IS '创建时间';

-- -----------------------------------------------------------------------------
-- Agent 紧凑状态快照：多轮对话恢复用
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_agent_state (
    tenant_id       VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    payload         TEXT NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, conversation_id)
);
COMMENT ON TABLE gw_agent_state IS 'Agent 业务侧状态快照：多轮对话恢复用';
COMMENT ON COLUMN gw_agent_state.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN gw_agent_state.conversation_id IS '会话 ID';
COMMENT ON COLUMN gw_agent_state.payload IS 'JSON 快照正文';
COMMENT ON COLUMN gw_agent_state.updated_at IS '最后更新时间';

-- -----------------------------------------------------------------------------
-- AgentScope 框架状态：按 tenant + conversation + state_key 持久化
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_agentscope_state (
    tenant_id       VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    state_key       VARCHAR(128) NOT NULL,
    payload         TEXT NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, conversation_id, state_key)
);
CREATE INDEX IF NOT EXISTS idx_gw_as_state_tenant ON gw_agentscope_state(tenant_id);
COMMENT ON TABLE gw_agentscope_state IS 'AgentScope 框架状态：支撑 pending tool recovery';
COMMENT ON COLUMN gw_agentscope_state.tenant_id IS '即 AgentScope userId（租户）';
COMMENT ON COLUMN gw_agentscope_state.conversation_id IS '即 AgentScope sessionId';
COMMENT ON COLUMN gw_agentscope_state.state_key IS '框架状态键，如 agent_state';
COMMENT ON COLUMN gw_agentscope_state.payload IS '框架自有 JSON，业务侧不解析';
COMMENT ON COLUMN gw_agentscope_state.updated_at IS '最后更新时间';

-- -----------------------------------------------------------------------------
-- 人工确认单：危险工具挂起后由客户端 approve/deny
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_confirm (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    tool_name       VARCHAR(128) NOT NULL,
    args_json       TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_confirm_tenant_task ON gw_confirm(tenant_id, task_id);
COMMENT ON TABLE gw_confirm IS '人工确认单：写工具挂起后等待 approve/deny';
COMMENT ON COLUMN gw_confirm.id IS '确认单 ID，如 cfm_xxx';
COMMENT ON COLUMN gw_confirm.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN gw_confirm.task_id IS '发起确认的聊天任务 ID';
COMMENT ON COLUMN gw_confirm.conversation_id IS '会话 ID；续跑时定位 Agent 状态';
COMMENT ON COLUMN gw_confirm.tool_name IS '待确认工具名';
COMMENT ON COLUMN gw_confirm.args_json IS '工具参数 JSON（展示与审计）';
COMMENT ON COLUMN gw_confirm.status IS '状态：PENDING / CONFIRMED / DENIED / EXPIRED';
COMMENT ON COLUMN gw_confirm.expires_at IS '过期时间（惰性读取 + 定时扫描）';
COMMENT ON COLUMN gw_confirm.created_at IS '创建时间';

-- -----------------------------------------------------------------------------
-- 审计事件：谁在何时做了什么
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_audit_event (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    ref_id          VARCHAR(64),
    detail_json     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_audit_tenant ON gw_audit_event(tenant_id);
COMMENT ON TABLE gw_audit_event IS '审计事件：确认、取消、对话完成等操作留痕';
COMMENT ON COLUMN gw_audit_event.id IS '事件 ID';
COMMENT ON COLUMN gw_audit_event.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN gw_audit_event.event_type IS '事件类型，如 CONFIRM_APPROVE / CHAT_COMPLETE';
COMMENT ON COLUMN gw_audit_event.ref_id IS '关联对象 ID（taskId / confirmId）';
COMMENT ON COLUMN gw_audit_event.detail_json IS '补充细节 JSON';
COMMENT ON COLUMN gw_audit_event.created_at IS '创建时间';

-- -----------------------------------------------------------------------------
-- 用量事件：按租户累计 runs / tokens 等指标
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_usage_event (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64),
    metric          VARCHAR(64) NOT NULL,
    amount          BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_usage_tenant ON gw_usage_event(tenant_id);
COMMENT ON TABLE gw_usage_event IS '用量事件：按租户累计 runs / tokens 等指标';
COMMENT ON COLUMN gw_usage_event.id IS '事件 ID';
COMMENT ON COLUMN gw_usage_event.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN gw_usage_event.task_id IS '可选关联任务 ID';
COMMENT ON COLUMN gw_usage_event.metric IS '指标名，如 runs';
COMMENT ON COLUMN gw_usage_event.amount IS '增量数值';
COMMENT ON COLUMN gw_usage_event.created_at IS '创建时间';
