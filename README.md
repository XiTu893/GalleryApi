# Gallery API Token Service

![CI Status](https://github.com/XiTu893/GalleryApi/actions/workflows/android-ci.yml/badge.svg)

为 Google AI Edge Gallery 添加本地 API Token 服务，提供 OpenAI 兼容的 `/v1/chat/completions` 接口。

## 🎯 项目目标

将 Google AI Edge Gallery 改造成支持本地 API 服务的 Android 应用，允许外部应用通过 HTTP API 调用设备上运行的 LiteRT-LM 模型（如 Gemma、Qwen 等），实现：

- ✅ 完全离线运行（无需云端）
- ✅ 数据隐私保护（所有计算在本地）
- ✅ OpenAI API 兼容（无缝切换现有应用）
- ✅ Token 认证管理（安全访问控制）
- ✅ UI 控制面板（直观管理 API 服务）

## 📁 项目结构

```
GalleryApi/
├── .github/
│   └── workflows/
│       └── android-ci.yml              # GitHub Actions CI 配置
├── gallery/                            # Google AI Edge Gallery (Git Submodule)
│   └── Android/src/
│       └── app/src/main/java/com/google/ai/edge/gallery/
│           ├── api/                    # ✨ 新增 API 模块
│           │   ├── model/              # 数据模型
│           │   ├── middleware/         # 认证中间件
│           │   ├── handler/            # API 处理器
│           │   ├── router/             # 路由
│           │   ├── inference/          # LiteRT 适配器
│           │   ├── response/           # 响应构建器
│           │   ├── ApiService.kt       # NanoHTTPD 服务器
│           │   ├── ApiConfig.kt        # API 配置管理
│           │   └── TokenManager.kt     # Token 管理器
│           └── ui/home/
│               └── ApiServiceControlPanel.kt  # ✨ UI 控制面板
└── doc/                                # 项目文档
    ├── Gallery_API_Token_服务改造方案.md
    ├── IMPLEMENTATION_PROGRESS.md
    ├── PROJECT_SUMMARY.md
    ├── QUICK_START.md
    ├── API_SERVICE_UI_CONTROL.md
    └── COMPLETION_REPORT.md
```

## ✅ 已完成的工作

### 1. 基础架构 (100%)
- [x] Clone Gallery 仓库并设置为 Git Submodule
- [x] 分析 LiteRT-LM 集成方式
- [x] 添加 NanoHTTPD 依赖
- [x] 配置 GitHub Actions CI/CD

### 2. 数据模型 (100%)
- [x] `ApiToken.kt` - Token 数据结构
- [x] `OpenAiModels.kt` - OpenAI 兼容的请求/响应模型
- [x] 默认 Token 过期时间设置为 10 年

### 3. Token 管理 (100%)
- [x] `TokenManager.kt` - Token 生成、验证、撤销、统计

### 4. 认证中间件 (100%)
- [x] `AuthMiddleware.kt` - Bearer Token 验证

### 5. API 配置管理 (100%) ✨
- [x] `ApiConfig.kt` - API Key 认证开关配置
- [x] `/v1/config` 端点 - 查询和修改配置
- [x] 可选认证支持 - 允许无 Token 访问聊天接口
- [x] **UI 控制面板** - 设置对话框中显示服务器地址和认证开关

### 6. HTTP 服务器 (100%) ✨
- [x] `ApiService.kt` - NanoHTTPD 服务器实现
- [x] `ApiServerService.kt` - Android Foreground Service
- [x] `ApiServerController.kt` - 服务器生命周期控制

### 7. API 路由与处理 (100%) ✨
- [x] `ApiRouter.kt` - 请求路由
- [x] `ChatCompletionHandler.kt` - 聊天补全处理器
- [x] `TokenHandler.kt` - Token 管理处理器
- [x] `ResponseBuilder.kt` - 响应构建器

### 8. LiteRT 适配器 (70%) 🚧
- [x] `LiteRtAdapter.kt` - 框架实现（Mock 响应）
- [ ] 集成真实 LiteRT-LM 推理引擎

## 🚧 待完成的工作

### 核心功能 (优先级高)
- [ ] **集成真实 LiteRT-LM 推理引擎**
  - 连接 `LlmModelHelper` 接口
  - 实现消息格式转换
  - 处理流式响应
  
- [ ] UI 启动/停止控制
  - 在 ApiServiceControlPanel 中添加服务器开关
  - 显示服务器运行状态

### 增强功能 (可选)
- [ ] 加密存储 (生产环境)
  - 使用 EncryptedSharedPreferences 存储 Token
  - 或使用 Room + SQLCipher
  
- [ ] HTTPS/TLS 支持
- [ ] 速率限制
- [ ] 显示实际 LAN IP 地址

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
- JDK 17
- 设备至少 6GB RAM (用于运行 LLM)

### 克隆项目

```bash
# 克隆主仓库（包含子模块）
git clone --recursive git@github.com:XiTu893/GalleryApi.git
cd GalleryApi

# 如果已经克隆但忘记 --recursive
git submodule update --init --recursive
```

### 构建和运行

```bash
# 1. 进入 Android 项目目录
cd gallery/Android/src

# 2. 同步 Gradle 依赖
./gradlew build

# 3. 在 Android Studio 中打开项目并运行
# 或者使用命令行安装到设备
./gradlew installDebug
```

### GitHub Actions 自动编译

每次推送到 `master` 分支时，GitHub Actions 会自动编译 APK：

1. 访问 https://github.com/XiTu893/GalleryApi/actions
2. 查看最近的 workflow 运行状态
3. 下载生成的 APK 文件（保留 30 天）

详见 [.github/README_CI.md](.github/README_CI.md)

### API 测试

API 服务器运行在 `http://127.0.0.1:8080`

```bash
# 查询当前配置
curl http://127.0.0.1:8080/v1/config

# 禁用认证（允许无 Token 访问）
curl -X PUT http://127.0.0.1:8080/v1/config \
  -H "Content-Type: application/json" \
  -d '{"auth_enabled": false}'

# 生成 Token（默认 10 年过期）
curl -X POST http://127.0.0.1:8080/v1/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "test-token"}'

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
总体进度: ███████████████████░ 85%

✅ 数据模型层:    ████████████████████ 100%
✅ Token 管理:    ████████████████████ 100%
✅ 认证中间件:    ████████████████████ 100%
✅ API 配置管理:  ████████████████████ 100%
✅ HTTP 服务器:   ████████████████████ 100%
✅ API 路由:      ████████████████████ 100%
🚧 LiteRT 适配:   ██████████████░░░░░░  70% (Mock 实现)
🚧 UI 控制:       ██████████████░░░░░░  70% (缺少启停开关)
```

## 🎯 下一步行动

### 立即可执行 (预计 1-2 小时)

1. **集成真实 LiteRT-LM 推理引擎**
   - 在 `LiteRtAdapter.kt` 中接入 `LlmModelHelper`
   - 实现 OpenAI 消息到 LiteRT Contents 的转换
   - 处理流式响应回调
   - 测试端到端推理流程

2. **添加 UI 启停控制**
   - 在 `ApiServiceControlPanel` 中添加启动/停止开关
   - 显示服务器运行状态
   - 集成 `ApiServerController`

详细计划请查看 [PROJECT_SUMMARY.md](doc/PROJECT_SUMMARY.md) 和 [COMPLETION_REPORT.md](doc/COMPLETION_REPORT.md)

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request!

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

### 开发注意事项

- 保持代码风格与 Gallery 项目一致
- 添加必要的注释和文档
- 确保 CI 编译通过
- 更新相关文档

## 📄 许可证

Apache License 2.0 (与 Google AI Edge Gallery 保持一致)

## 🙏 致谢

- **Google AI Edge Team** - Gallery 和 LiteRT-LM
- **NanoHTTPD** - 轻量级 HTTP 服务器
- **OpenAI** - API 格式参考

## 📞 联系方式

如有问题或建议，请提交 Issue。

## ☕ 支持项目

如果这个项目对你有帮助，欢迎扫描下方二维码捐赠支持！

<img src="doc/QrReward.jpg" alt="捐赠二维码" width="200" />

---

**最后更新**: 2026-05-16  
**版本**: 0.2.0-alpha  
**状态**: 核心功能完成，等待 LiteRT-LM 集成  
**仓库**: https://github.com/XiTu893/GalleryApi
