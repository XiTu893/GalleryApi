# GalleryApi - 本地大模型 API 服务

![CI Status](https://github.com/XiTu893/GalleryApi/actions/workflows/android-ci.yml/badge.svg) [![Download APK](https://img.shields.io/github/v/release/XiTu893/GalleryApi?label=Download%20APK&color=brightgreen)](https://github.com/XiTu893/GalleryApi/releases/latest)

为 Google AI Edge Gallery 添加本地大模型 API 服务，提供 OpenAI 兼容的 `/v1/chat/completions` 接口，让外部应用通过 HTTP API 调用设备上运行的 LiteRT-LM 模型（如 Gemma、Qwen 等）。

## 📦 下载安装

从 [GitHub Releases](https://github.com/XiTu893/GalleryApi/releases/latest) 下载最新 APK，安装到 Android 设备（API 31+ / Android 12+）即可使用。

> 每次 push 到 master 分支会自动构建并发布最新 APK 到 Release 页面。

## ✨ 核心特性

- ✅ 完全离线运行（无需云端）
- ✅ 数据隐私保护（所有计算在本地）
- ✅ OpenAI API 兼容（无缝切换现有应用）
- ✅ Token 认证管理（安全访问控制）
- ✅ UI 控制面板（直观管理 API 服务）
- ✅ 流式响应（SSE Server-Sent Events）
- ✅ 自动发布 APK 到 GitHub Releases

## 📁 项目结构

```
GalleryApi/
├── .github/
│   └── workflows/
│       └── android-ci.yml              # GitHub Actions CI/CD + 自动发布 Release
├── gallery/
│   └── Android/src/
│       └── app/src/main/java/com/google/ai/edge/gallery/
│           ├── api/                    # API 模块
│           │   ├── model/              # OpenAI 兼容数据模型
│           │   ├── middleware/         # Bearer Token 认证中间件
│           │   ├── handler/            # ChatCompletion / Token 处理器
│           │   ├── router/             # 请求路由
│           │   ├── inference/          # LiteRT 推理适配器 + ModelRegistry
│           │   ├── response/           # 统一 JSON 响应构建器
│           │   ├── ApiService.kt       # NanoHTTPD 服务器
│           │   ├── ApiServerService.kt # Android Foreground Service
│           │   ├── ApiServerController.kt # 服务器生命周期控制
│           │   ├── ApiConfig.kt        # API 配置管理（端口/认证开关）
│           │   └── TokenManager.kt     # Token 生成/验证/撤销
│           └── ui/home/
│               └── ApiServiceControlPanel.kt  # API 服务控制面板
└── doc/                                # 项目文档
```

## ✅ 已完成的功能

### 核心功能

| 模块 | 状态 | 说明 |
|------|------|------|
| 数据模型层 | ✅ 100% | `ApiToken.kt`, `OpenAiModels.kt` |
| Token 管理 | ✅ 100% | `TokenManager.kt` - 生成/验证/撤销/统计 |
| 认证中间件 | ✅ 100% | `AuthMiddleware.kt` - Bearer Token + 可选认证 |
| API 配置管理 | ✅ 100% | `ApiConfig.kt` - 端口/认证开关/SharedPreferences |
| HTTP 服务器 | ✅ 100% | `ApiService.kt` + `ApiServerService.kt` 前台服务 |
| API 路由与处理 | ✅ 100% | `ApiRouter.kt`, `ChatCompletionHandler.kt`, `TokenHandler.kt` |
| LiteRT 推理适配 | ✅ 100% | `LiteRtAdapter.kt` - 接入 LlmModelHelper，支持同步/流式推理 |
| 模型注册表 | ✅ 100% | `ModelRegistry.kt` - 桥接 UI 层与 API 服务的模型共享 |
| UI 控制面板 | ✅ 100% | `ApiServiceControlPanel.kt` - 启停开关/端口显示/认证开关 |
| CI/CD | ✅ 100% | GitHub Actions 自动编译 + 发布 APK 到 Release |
| Hilt DI | ✅ 100% | KSP 注解处理，TokenManager/ApiConfig 依赖注入 |

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/chat/completions` | POST | 聊天补全（支持流式 SSE） |
| `/v1/models` | GET | 列出已加载模型 |
| `/v1/tokens` | POST | 生成 Token |
| `/v1/tokens` | GET | 列出所有 Token |
| `/v1/tokens/{id}` | DELETE | 撤销 Token |
| `/v1/config` | GET | 查询配置 |
| `/v1/config` | PUT | 修改配置（认证开关等） |
| `/health` | GET | 健康检查 |

## 🚀 快速开始

### 安装

1. 从 [Releases](https://github.com/XiTu893/GalleryApi/releases/latest) 下载 APK
2. 安装到 Android 设备（API 31+ / Android 12+）
3. 打开应用，下载一个模型（如 Gemma）
4. 进入 **Settings → API Service**，点击 **Start** 启动服务
5. 记下显示的端口号（默认 8080）

### API 测试

API 服务器运行在 `http://<设备IP>:8080`

```bash
# 健康检查
curl http://127.0.0.1:8080/health

# 查询已加载模型
curl http://127.0.0.1:8080/v1/models

# 聊天补全（非流式）
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'

# 聊天补全（流式 SSE）
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }'

# 生成 Token（认证已启用时）
curl -X POST http://127.0.0.1:8080/v1/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "test-token"}'

# 使用 Token 访问
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

### 与 OpenAI 兼容应用集成

将 API 地址设置为 `http://<设备IP>:8080/v1`，即可与支持 OpenAI API 的应用（如 ChatBox、Open WebUI、LobeChat 等）无缝集成。

## 🏗️ 技术架构

```
Client App (Python/JS/cURL/ChatBox)
    ↓ HTTP Request (Bearer Token)
NanoHTTPD Server (Port 8080)
    ↓ Token Validation (optional)
AuthMiddleware
    ↓ Routing
ApiRouter
    ↓ Handler
ChatCompletionHandler
    ↓ Convert Format + Model Lookup
ModelRegistry → LiteRtAdapter
    ↓ Inference
LlmModelHelper → LiteRT-LM Engine (Gemma/Qwen)
    ↓ Streaming/Sync Response
JSON (OpenAI Format) / SSE Stream
```

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.2.0 | 主要开发语言 |
| NanoHTTPD | 2.3.1 | 嵌入式 HTTP 服务器 |
| Gson | 2.10.1 | JSON 序列化/反序列化 |
| Kotlin Coroutines | - | 异步处理 |
| LiteRT-LM | 0.11.0 | 端侧 LLM 推理引擎 |
| Hilt + KSP | - | 依赖注入 |
| Android Foreground Service | - | 后台服务保活 |
| minSdk | API 31 | Android 12+ |

## 🔐 安全特性

- ✅ Bearer Token 认证（可选开关）
- ✅ Token 过期控制（默认 10 年）
- ✅ Token 撤销机制
- ✅ Token 脱敏显示
- ⏳ 加密存储（EncryptedSharedPreferences）
- ⏳ HTTPS/TLS
- ⏳ 速率限制

## 📊 当前进度

```
总体进度: ████████████████████ 95%

✅ 数据模型层:    ████████████████████ 100%
✅ Token 管理:    ████████████████████ 100%
✅ 认证中间件:    ████████████████████ 100%
✅ API 配置管理:  ████████████████████ 100%
✅ HTTP 服务器:   ████████████████████ 100%
✅ API 路由:      ████████████████████ 100%
✅ LiteRT 推理:   ████████████████████ 100%
✅ UI 控制面板:   ████████████████████ 100%
✅ CI/CD + Release: ██████████████████ 100%
⏳ 安全增强:      ████████████░░░░░░░░  60%
```

## 🎯 下一步计划

### 增强功能

- [ ] 加密存储 - 使用 EncryptedSharedPreferences 存储 Token
- [ ] HTTPS/TLS 支持 - 本地自签名证书
- [ ] 速率限制 - 防止 API 滥用
- [ ] 显示 LAN IP 地址 - 方便局域网访问
- [ ] 多模型并发 - 支持同时加载多个模型
- [ ] Embeddings API - `/v1/embeddings` 端点
- [ ] 自定义系统提示词 - 通过 API 配置 system prompt

### 开发构建

```bash
# 克隆项目
git clone https://github.com/XiTu893/GalleryApi.git
cd GalleryApi

# 构建 Debug APK
cd gallery/Android/src
./gradlew assembleDebug --no-daemon

# 或在 Android Studio 中打开 gallery/Android/src 目录
```

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

如有问题或建议，请提交 Issue 或发送邮件至 28491599@qq.com。

## ☕ 支持项目

如果这个项目对你有帮助，欢迎扫描下方二维码捐赠支持！

<img src="doc/QrReward.jpg" alt="捐赠二维码" width="200" />

---

**最后更新**: 2026-05-16
**版本**: 1.0.0
**状态**: 核心功能已完成，持续优化中
**仓库**: https://github.com/XiTu893/GalleryApi
