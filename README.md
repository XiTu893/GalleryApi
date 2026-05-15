# Gallery API Token Service

为 Google AI Edge Gallery 添加本地 API Token 服务,提供 OpenAI 兼容的 `/v1/chat/completions` 接口。

## 🎯 项目目标

将 Google AI Edge Gallery 改造成支持本地 API 服务的 Android 应用,允许外部应用通过 HTTP API 调用设备上运行的 LiteRT-LM 模型 (如 Gemma、Qwen 等),实现:

- ✅ 完全离线运行 (无需云端)
- ✅ 数据隐私保护 (所有计算在本地)
- ✅ OpenAI API 兼容 (无缝切换现有应用)
- ✅ Token 认证管理 (安全访问控制)

## 📁 项目结构

```
iceMApi/
├── gallery/                          # Google AI Edge Gallery 源码
│   └── Android/src/
│       └── app/src/main/java/com/google/ai/edge/gallery/
│           └── api/                  # 新增 API 模块
│               ├── model/            # 数据模型
│               ├── middleware/       # 认证中间件
│               ├── handler/          # API 处理器 (待实现)
│               ├── router/           # 路由 (待实现)
│               ├── inference/        # LiteRT 适配器 (待实现)
│               └── response/         # 响应构建器 (待完成)
└── doc/                              # 项目文档
    ├── Gallery_API_Token_服务改造方案.md    # 完整设计方案
    ├── IMPLEMENTATION_PROGRESS.md         # 实施进度
    ├── PROJECT_SUMMARY.md                 # 项目总结
    └── QUICK_START.md                     # 快速开始指南
```

## ✅ 已完成的工作

### 1. 基础架构 (100%)
- [x] Clone Gallery 仓库 (v1.0.14)
- [x] 分析 LiteRT-LM 集成方式
- [x] 添加 NanoHTTPD 依赖

### 2. 数据模型 (100%)
- [x] `ApiToken.kt` - Token 数据结构
- [x] `OpenAiModels.kt` - OpenAI 兼容的请求/响应模型

### 3. Token 管理 (100%)
- [x] `TokenManager.kt` - Token 生成、验证、撤销、统计

### 4. 认证中间件 (100%)
- [x] `AuthMiddleware.kt` - Bearer Token 验证

### 5. API 配置管理 (100%) ✨ NEW
- [x] `ApiConfig.kt` - API Key 认证开关配置
- [x] `/v1/config` 端点 - 查询和修改配置
- [x] 可选认证支持 - 允许无 Token 访问聊天接口
- [x] **UI 控制面板** - 设置对话框中显示服务器地址和认证开关

## 🚧 待完成的工作

### 核心功能 (优先级高)
- [ ] NanoHTTPD 服务器 (`ApiService.kt`)
- [ ] Android Foreground Service (`ApiServerService.kt`)
- [ ] API 路由器 (`ApiRouter.kt`)
- [ ] 聊天补全处理器 (`ChatCompletionHandler.kt`)
- [ ] Token 管理处理器 (`TokenHandler.kt`)
- [ ] LiteRT-LM 适配器 (`LiteRtAdapter.kt`)
- [ ] 响应构建器 (`ResponseBuilder.kt`)

### 配置和集成
- [ ] 更新 `AndroidManifest.xml`
- [ ] UI 控制界面 (可选)
- [ ] 加密存储 (生产环境)

## 📖 文档

| 文档 | 说明 |
|------|------|
| [Gallery_API_Token_服务改造方案.md](doc/Gallery_API_Token_服务改造方案.md) | 完整的技术设计方案 (621 行) |
| [IMPLEMENTATION_PROGRESS.md](doc/IMPLEMENTATION_PROGRESS.md) | 实时更新的实施进度跟踪 |
| [PROJECT_SUMMARY.md](doc/PROJECT_SUMMARY.md) | 项目总结和下一步行动 |
| [QUICK_START.md](doc/QUICK_START.md) | API 使用示例和快速开始指南 |
| [API_SERVICE_UI_CONTROL.md](doc/API_SERVICE_UI_CONTROL.md) | ✨ NEW - UI 控制面板功能说明 |

## 🚀 快速开始

### 前置要求

- Android Studio Hedgehog 或更高版本
- Android SDK 31+ (Android 12)
- 设备至少 6GB RAM (用于运行 LLM)

### 构建和运行

```bash
# 1. 克隆项目 (已完成)
cd iceMApi/gallery/Android/src

# 2. 同步 Gradle 依赖
./gradlew build

# 3. 在 Android Studio 中打开项目并运行
```

### API 测试 (待服务器实现后)

```bash
# 查询配置
curl http://127.0.0.1:8080/v1/config

# 禁用认证（允许无 Token 访问）
curl -X PUT http://127.0.0.1:8080/v1/config \
  -H "Content-Type: application/json" \
  -d '{"auth_enabled": false}'

# 生成 Token
curl -X POST http://127.0.0.1:8080/v1/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "test", "expires_in_days": 30}'

# 聊天补全（需要 Token，如果认证已启用）
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'

# 聊天补全（无需 Token，如果认证已禁用）
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

更多示例请查看 [QUICK_START.md](doc/QUICK_START.md)

## 🏗️ 技术架构

```
Client App (Python/JS/cURL)
    ↓ HTTP Request (Bearer Token)
NanoHTTPD Server (Port 8080)
    ↓ Token Validation
AuthMiddleware
    ↓ Routing
ApiRouter
    ↓ Handler
ChatCompletionHandler
    ↓ Convert Format
LiteRtAdapter
    ↓ Inference
LiteRT-LM Engine (Gemma/Qwen)
    ↓ Response
JSON (OpenAI Format)
```

## 🛠️ 技术栈

- **语言**: Kotlin
- **HTTP 服务器**: NanoHTTPD 2.3.1
- **JSON 解析**: Gson 2.10.1
- **异步处理**: Kotlin Coroutines
- **LLM 引擎**: LiteRT-LM 0.11.0
- **依赖注入**: Hilt (Gallery 已有)
- **最小 SDK**: Android 12 (API 31)

## 🔐 安全特性

- ✅ Bearer Token 认证
- ✅ Token 过期控制
- ✅ Token 撤销机制
- ✅ Token 脱敏显示
- ✅ **可选认证开关** - 允许禁用 API Key 要求（类似 Ollama）
- ⏳ 加密存储 (待实现)
- ⏳ HTTPS/TLS (待实现)
- ⏳ 速率限制 (待实现)

## 📊 当前进度

```
总体进度: ██████████████░░░░░░ 35%

✅ 数据模型层:    ████████████████████ 100%
✅ Token 管理:    ████████████████████ 100%
✅ 认证中间件:    ████████████████████ 100%
✅ API 配置管理:  ████████████████████ 100% ✨ NEW
🚧 HTTP 服务器:   ░░░░░░░░░░░░░░░░░░░░   0%
🚧 API 路由:      ░░░░░░░░░░░░░░░░░░░░   0%
🚧 LiteRT 适配:   ░░░░░░░░░░░░░░░░░░░░   0%
🚧 配置和测试:    ░░░░░░░░░░░░░░░░░░░░   0%
```

## 🎯 下一步行动

### 立即可执行 (预计 2-3 小时)

1. **创建 NanoHTTPD 服务器**
   - 实现 `ApiService.kt`
   - 实现 `ApiServerService.kt`
   - 基本路由和响应

2. **实现 Token 端点**
   - `/v1/tokens` POST - 生成 Token
   - `/v1/tokens` GET - 列出 Token
   - curl 测试验证

3. **创建 LiteRT 适配器原型**
   - 封装 `LlmModelHelper` 调用
   - 简单的 prompt 转换

详细计划请查看 [PROJECT_SUMMARY.md](doc/PROJECT_SUMMARY.md)

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request!

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

## 📄 许可证

Apache License 2.0 (与 Google AI Edge Gallery 保持一致)

## 🙏 致谢

- **Google AI Edge Team** - Gallery 和 LiteRT-LM
- **NanoHTTPD** - 轻量级 HTTP 服务器
- **OpenAI** - API 格式参考

## 📞 联系方式

如有问题或建议,请提交 Issue。

---

**最后更新**: 2026-05-15  
**版本**: 0.1.0-alpha  
**状态**: 开发中
