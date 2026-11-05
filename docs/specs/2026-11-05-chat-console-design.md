# Agent Gateway 对话台设计

**日期：** 2026-11-05  
**状态：** 已批准，进入实现  
**仓库：** 独立前端 `agent-gateway-web`（与 `agent-gateway` 平级）

## 目标

提供第一版**对话台**：贴 API Key 即可对话；空态为 Perplexity 式居中大输入；首条消息后切 ChatGPT/DeepSeek 式消息流；支持工具事件、确认卡、取消。

## 非目标（v1）

- 管理控制台（租户 / MCP / 策略）
- 附件、语音、多模型切换
- 服务端会话列表 API（先用 localStorage）

## 布局

### 空态

```
┌──────────┬─────────────────────────────┐
│ + 新建   │                             │
│ 会话列表 │     Agent Gateway           │
│          │   ┌───────────────────┐     │
│          │   │  大输入盒 + 发送  │     │
│          │   └───────────────────┘     │
│  API Key │   [芯片] [芯片] [芯片]      │
└──────────┴─────────────────────────────┘
```

### 会话态

```
┌──────────┬─────────────────────────────┐
│ 侧栏同左 │  消息流（用户 / 助手 / 工具 / 确认） │
│          │─────────────────────────────│
│          │  底栏粘底输入 + 停止          │
└──────────┴─────────────────────────────┘
```

## 视觉

| Token | 值 | 用途 |
|---|---|---|
| `--bg` | `#F4F6F5` | 页面底 |
| `--surface` | `#FFFFFF` | 卡片 / 输入 |
| `--ink` | `#14201C` | 主文字 |
| `--muted` | `#5C6B66` | 次要文字 |
| `--accent` | `#0F766E` | 发送 / 批准 |
| `--danger` | `#B42318` | 拒绝描边 |
| `--line` | `#D8E0DC` | 分割线 |

字体：展示用 **Sora**，正文 **Source Sans 3**，代码 **IBM Plex Mono**。

## SSE 契约

- `POST /v1/chat` body: `{ message, conversationId? }`
- 事件：`meta`（taskId/conversationId）、`thinking`、`tool`、`confirm`（JSON：taskId/calls[].confirmId）、`answer`、`error`、`done`
- `POST /v1/confirms/{confirmId}/approve|deny` → 续跑 SSE
- `POST /v1/runs/{taskId}/cancel`

鉴权：`Authorization: Bearer <apiKey>`。

## 技术

- React 18 + TypeScript + Vite
- 无路由框架亦可（单页）
- Vite proxy → `http://127.0.0.1:8080`；gateway 另开 CORS 便于直连
