# Gallery API Token Service - Quick Start Guide

## 概述

本项目为 Google AI Edge Gallery 添加了本地 API Token 服务,提供完全兼容 OpenAI 格式的 `/v1/chat/completions` 接口,允许外部应用通过 HTTP API 调用设备上运行的 LiteRT-LM 模型。

## 架构

```
┌─────────────┐
│ Client App  │ (Python, JS, cURL, etc.)
└──────┬──────┘
       │ HTTP Request (Bearer Token)
       ▼
┌──────────────────┐
│  NanoHTTPD       │ Port 8080
│  Server          │
└──────┬───────────┘
       │ Token Validation
       ▼
┌──────────────────┐
│  AuthMiddleware  │
└──────┬───────────┘
       │ Validated Request
       ▼
┌──────────────────┐
│  ApiRouter       │
└──────┬───────────┘
       │ Route to Handler
       ▼
┌──────────────────┐
│  ChatCompletion  │
│  Handler         │
└──────┬───────────┘
       │ Convert to LiteRT Format
       ▼
┌──────────────────┐
│  LiteRtAdapter   │
└──────┬───────────┘
       │ Inference
       ▼
┌──────────────────┐
│  LiteRT-LM       │ Gemma/Qwen/etc.
│  Engine          │
└──────┬───────────┘
       │ Response
       ▼
┌──────────────────┐
│  JSON Response   │ (OpenAI Format)
└──────────────────┘
```

## 已实现的功能

### ✅ 核心组件

1. **Token 管理系统**
   - Token 生成 (UUID-based, `sk-` prefix)
   - Token 验证 (active + expiry check)
   - Token 撤销
   - 使用统计追踪

2. **认证中间件**
   - Bearer Token 提取和验证
   - 标准化错误响应

3. **数据模型**
   - OpenAI 兼容的请求/响应格式
   - Token 管理相关模型

### 🚧 待实现的核心功能

1. **NanoHTTPD 服务器**
2. **API 路由和处理器**
3. **LiteRT-LM 推理适配器**
4. **Android Service 集成**

## API 端点 (计划)

### 0. 配置管理

**Endpoint**: `GET /v1/config`

**Description**: Query current API server configuration.

**Response**:
```json
{
  "auth_enabled": true,
  "server_port": 8080
}
```

---

**Endpoint**: `PUT /v1/config`

**Description**: Update API server configuration.

**Request**:
```json
{
  "auth_enabled": false
}
```

**Response**:
```json
{
  "message": "Configuration updated"
}
```

---

### 1. 生成 Token

**Endpoint**: `POST /v1/tokens`

**Request**:
```json
{
  "name": "my-app-token",
  "expires_in_days": 30
}
```

**Response**:
```json
{
  "token": "sk-a1b2c3d4e5f6...",
  "name": "my-app-token",
  "created_at": 1715750400000,
  "expires_at": 1718342400000,
  "is_active": true
}
```

### 2. 列出 Token

**Endpoint**: `GET /v1/tokens`

**Headers**: `Authorization: Bearer <admin-token>`

**Response**:
```json
{
  "tokens": [
    {
      "name": "my-app-token",
      "created_at": 1715750400000,
      "expires_at": 1718342400000,
      "is_active": true,
      "usage_count": 42,
      "masked": "sk-a1b2...3d4e"
    }
  ]
}
```

### 3. 聊天补全 (OpenAI 兼容)

**Endpoint**: `POST /v1/chat/completions`

**Headers**: `Authorization: Bearer <your-token>`

**Request**:
```json
{
  "model": "gemma-4-e2b-it",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello!"}
  ],
  "temperature": 0.7,
  "max_tokens": 512,
  "top_p": 0.95
}
```

**Response**:
```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1715750400,
  "model": "gemma-4-e2b-it",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello! How can I help you today?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 15,
    "completion_tokens": 9,
    "total_tokens": 24
  }
}
```

## 使用示例

### API 认证配置

默认情况下，API 需要有效的 Token 才能访问。您可以通过以下方式更改：

#### 方法 1: 通过 API

```bash
# 查询当前配置
curl http://127.0.0.1:8080/v1/config

# 禁用认证（允许无 Token 访问）
curl -X PUT http://127.0.0.1:8080/v1/config \
  -H "Content-Type: application/json" \
  -d '{"auth_enabled": false}'

# 启用认证
curl -X PUT http://127.0.0.1:8080/v1/config \
  -H "Content-Type: application/json" \
  -d '{"auth_enabled": true}'
```

#### 方法 2: 通过 App 设置

在 Gallery App 的设置页面中，找到 "API Key Authentication" 开关进行切换。

#### 无认证模式使用示例

当认证禁用后，可以直接调用 API 而无需 Token：

```bash
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

**注意**: 无认证模式仅建议在受信任的本地网络中使用。

---

### Python

```python
import requests

# Configuration
BASE_URL = "http://127.0.0.1:8080"
API_TOKEN = "sk-your-token-here"

headers = {
    "Authorization": f"Bearer {API_TOKEN}",
    "Content-Type": "application/json"
}

# Generate a new token
response = requests.post(
    f"{BASE_URL}/v1/tokens",
    json={"name": "python-app", "expires_in_days": 30}
)
print("New Token:", response.json()["token"])

# Chat completion
response = requests.post(
    f"{BASE_URL}/v1/chat/completions",
    headers=headers,
    json={
        "model": "gemma-4-e2b-it",
        "messages": [
            {"role": "user", "content": "Explain quantum computing in simple terms"}
        ],
        "temperature": 0.7
    }
)

result = response.json()
print("Assistant:", result["choices"][0]["message"]["content"])
print("Tokens used:", result["usage"]["total_tokens"])
```

### JavaScript (Node.js)

```javascript
const fetch = require('node-fetch');

const BASE_URL = 'http://127.0.0.1:8080';
const API_TOKEN = 'sk-your-token-here';

async function chatCompletion(prompt) {
  const response = await fetch(`${BASE_URL}/v1/chat/completions`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${API_TOKEN}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      model: 'gemma-4-e2b-it',
      messages: [
        { role: 'user', content: prompt }
      ],
      temperature: 0.7
    })
  });

  const data = await response.json();
  return data.choices[0].message.content;
}

// Usage
chatCompletion('What is the capital of France?')
  .then(answer => console.log('Answer:', answer));
```

### cURL

```bash
# Generate token
curl -X POST http://127.0.0.1:8080/v1/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "test-token", "expires_in_days": 30}'

# Chat completion
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-your-token-here" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [
      {"role": "user", "content": "Write a haiku about AI"}
    ],
    "temperature": 0.8
  }'
```

## 开发状态

### 已完成 ✅
- [x] 项目初始化和依赖配置
- [x] Token 数据模型
- [x] OpenAI 兼容的请求/响应模型
- [x] TokenManager 实现
- [x] AuthMiddleware 实现

### 进行中 🚧
- [ ] NanoHTTPD 服务器集成
- [ ] API 路由和处理器
- [ ] LiteRT-LM 推理适配器
- [ ] Android Service 配置

### 计划中 📋
- [ ] UI 控制界面
- [ ] 流式响应支持 (SSE)
- [ ] Token 加密存储
- [ ] 速率限制
- [ ] 完整测试套件

## 技术栈

- **语言**: Kotlin
- **HTTP 服务器**: NanoHTTPD 2.3.1
- **JSON 解析**: Gson 2.10.1
- **异步处理**: Kotlin Coroutines
- **LLM 引擎**: LiteRT-LM 0.11.0
- **最小 SDK**: Android 12 (API 31)

## 安全考虑

1. **Token 存储**: 当前使用内存存储,生产环境应使用 EncryptedSharedPreferences 或 Room + SQLCipher
2. **网络访问**: 默认仅监听 localhost,如需远程访问需明确配置并启用防火墙规则
3. **HTTPS**: 当前使用 HTTP,生产环境建议添加 TLS 支持
4. **速率限制**: 待实现,防止滥用
5. **认证开关**: 
   - 默认启用认证 (`auth_enabled = true`)，确保安全性
   - Token 管理端点 (`/v1/tokens`) 始终要求认证，即使聊天接口不需要
   - 禁用认证后，任何人都可以访问 `/v1/chat/completions`，仅限受信任的本地网络使用
   - 配置变更会记录到日志中，便于审计

## 故障排除

### 服务器无法启动
- 检查端口 8080 是否被占用
- 确认 INTERNET 和 FOREGROUND_SERVICE 权限已授予

### Token 验证失败
- 确认 Authorization header 格式正确: `Bearer <token>`
- 检查 Token 是否过期或被撤销

### 推理超时
- LiteRT-LM 模型加载可能需要时间,确保模型已下载
- 检查设备内存是否充足 (至少 6GB RAM)

## 贡献指南

欢迎提交 Issue 和 Pull Request!

## 许可证

Apache License 2.0 (与 Google AI Edge Gallery 保持一致)

---

**注意**: 这是一个正在进行的项目,API 可能会在最终版本前发生变化。
