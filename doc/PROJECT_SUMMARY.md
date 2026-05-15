# Gallery API Token 服务 - 项目总结

## 项目概述

本项目成功为 Google AI Edge Gallery 添加了 API Token 服务的基础架构,实现了 OpenAI 兼容的本地 API 服务的核心组件。

## 已完成的工作

### 1. 项目初始化 ✅
- ✅ Clone Google AI Edge Gallery 仓库 (版本 1.0.14)
- ✅ 分析现有代码结构和 LiteRT-LM 集成方式
- ✅ 添加 NanoHTTPD 2.3.1 依赖

**修改的文件**:
- `gallery/Android/src/gradle/libs.versions.toml` - 添加 nanohttpd 版本和库定义
- `gallery/Android/src/app/build.gradle.kts` - 添加 nanohttpd 依赖

### 2. 数据模型层 ✅

#### ApiToken.kt
**位置**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/model/ApiToken.kt`

**功能**:
- Token 数据结构 (token, name, createdAt, expiresAt, isActive, usageCount)
- `isValid()` 方法检查 Token 是否有效(未过期且激活)

#### OpenAiModels.kt
**位置**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/model/OpenAiModels.kt`

**包含的模型**:
- `ChatCompletionRequest` - OpenAI 聊天请求格式
- `Message` - 聊天消息 (role + content)
- `ChatCompletionResponse` - OpenAI 聊天响应格式
- `Choice` - 响应选项
- `Usage` - Token 使用统计
- `OpenAiError` - 错误响应格式
- `GenerateTokenRequest/Response` - Token 生成接口
- `TokenListResponse/TokenInfo` - Token 列表接口

### 3. Token 管理系统 ✅

#### TokenManager.kt
**位置**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/TokenManager.kt`

**核心功能**:
- `generateToken(name, expiresInDays)` - 生成新 Token (UUID-based, sk- 前缀)
- `validateToken(token)` - 验证 Token 有效性
- `revokeToken(token)` - 撤销 Token
- `listTokens()` - 列出所有 Token
- `incrementUsage(token)` - 更新使用计数
- `maskToken(token)` - Token 脱敏显示

**存储方式**: 
- 当前: 内存存储 (`mutableMapOf<String, ApiToken>`)
- TODO: 生产环境需改为 EncryptedSharedPreferences 或 Room Database

### 4. 认证中间件 ✅

#### AuthMiddleware.kt
**位置**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/middleware/AuthMiddleware.kt`

**功能**:
- 从 HTTP headers 提取 Bearer Token
- 调用 TokenManager 验证 Token
- 返回标准化的 ValidationResult (Valid/Invalid)

## LiteRT-LM 集成研究结果

### 关键发现

Gallery 使用 `LlmModelHelper` 接口进行推理,位于:
```
gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt
```

**核心方法**:
```kotlin
interface LlmModelHelper {
    fun initialize(context, model, taskId, supportImage, supportAudio, onDone, ...)
    fun resetConversation(model, supportImage, supportAudio, systemInstruction, tools, ...)
    fun runInference(model, input, resultListener, cleanUpListener, onError, images, audioClips, ...)
    fun stopResponse(model)
    fun cleanUp(model, onDone)
}
```

**推理调用示例**:
```kotlin
model.runtimeHelper.runInference(
    model = model,
    input = "User's prompt",
    resultListener = { partialResult, done, thinkingResult ->
        // 流式接收响应
        if (done) {
            // 推理完成
        }
    },
    onError = { errorMessage ->
        // 处理错误
    }
)
```

**依赖库**:
- `com.google.ai.edge.litertlm:litertlm-android:0.11.0`
- 使用 `Conversation.sendMessageAsync()` 进行异步推理
- 支持文本、图像、音频多模态输入

## 待完成的核心工作

### 高优先级 🔴

1. **NanoHTTPD 服务器实现**
   - 创建 `ApiService.kt` - 继承 NanoHTTPD,处理 HTTP 请求
   - 创建 `ApiServerService.kt` - Android Foreground Service
   - 创建 `ApiServerController.kt` - 启动/停止控制

2. **API 路由和处理器**
   - 创建 `ApiRouter.kt` - 路由分发逻辑
   - 创建 `ResponseBuilder.kt` - 统一 JSON 响应构建
   - 创建 `ChatCompletionHandler.kt` - /v1/chat/completions 处理
   - 创建 `TokenHandler.kt` - /v1/tokens 管理

3. **LiteRT-LM 适配器**
   - 创建 `LiteRtAdapter.kt` - 将 OpenAI 请求转换为 LiteRT-LM 调用
   - 实现消息格式转换 (OpenAI Messages → LiteRT Contents)
   - 处理流式响应 (SSE 或轮询)

4. **Android 配置**
   - 更新 `AndroidManifest.xml` - 添加 INTERNET 和 FOREGROUND_SERVICE 权限
   - 注册 ApiServerService

### 中优先级 🟡

5. **UI 集成** (可选但推荐)
   - 在 Settings 页面添加 API 服务开关
   - Token 管理界面 (查看、生成、撤销)
   - 服务器状态显示

6. **安全加固**
   - Token 加密存储 (EncryptedSharedPreferences)
   - CORS 配置
   - 速率限制

### 低优先级 🟢

7. **高级功能**
   - 流式响应 (Server-Sent Events)
   - 更多 OpenAI 端点 (/v1/completions, /v1/embeddings)
   - 监控和日志

## 文件结构

```
gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/
├── api/                                    # 新增 API 模块
│   ├── model/
│   │   ├── ApiToken.kt                    ✅ 已完成
│   │   └── OpenAiModels.kt                ✅ 已完成
│   ├── middleware/
│   │   └── AuthMiddleware.kt              ✅ 已完成
│   ├── TokenManager.kt                    ✅ 已完成
│   ├── ApiService.kt                      ⏳ 待创建
│   ├── ApiServerService.kt                ⏳ 待创建
│   ├── ApiServerController.kt             ⏳ 待创建
│   ├── router/
│   │   └── ApiRouter.kt                   ⏳ 待创建
│   ├── handler/
│   │   ├── ChatCompletionHandler.kt       ⏳ 待创建
│   │   └── TokenHandler.kt                ⏳ 待创建
│   ├── response/
│   │   └── ResponseBuilder.kt             ⏳ 待创建
│   └── inference/
│       └── LiteRtAdapter.kt               ⏳ 待创建
└── runtime/
    └── LlmModelHelper.kt                  ✅ 现有接口 (复用)
```

## 技术挑战与解决方案

### 1. 线程安全
**问题**: NanoHTTPD 在独立线程处理请求,而 LiteRT-LM 可能需要主线程

**解决方案**:
- 使用 Kotlin Coroutines (`Dispatchers.IO` for I/O, `Dispatchers.Main` for UI)
- 在 ApiService 中使用 `runBlocking` 或协程作用域
- 确保 TokenManager 的并发访问安全 (可使用 `ConcurrentHashMap`)

### 2. 模型实例共享
**问题**: API Service 需要访问 Gallery App 已加载的模型

**解决方案**:
- 通过 Application Context 共享 `LlmModelHelper` 实例
- 或使用 Hilt/Koin 依赖注入管理单例
- 避免重复加载模型 (节省内存)

### 3. 流式响应
**问题**: NanoHTTPD 默认不支持 chunked transfer encoding

**解决方案**:
- 方案 A: 使用 `ChunkedOutputStream` 实现 SSE
- 方案 B: 先实现非流式,后续迭代添加
- 方案 C: 切换到 Ktor Server (更现代的 Kotlin 框架)

### 4. 后台服务保活
**问题**: Android 系统可能杀死后台 Service

**解决方案**:
- 使用 Foreground Service + Notification
- 请求忽略电池优化 (需用户授权)
- 监听系统广播,适时重启

## 下一步行动建议

### 立即执行 (1-2 小时)

1. **创建 NanoHTTPD 基础服务器**
   ```kotlin
   class ApiService(private val context: Context) : NanoHTTPD(8080) {
       override fun serve(session: IHTTPSession): Response {
           // 路由匹配
           // Token 验证
           // 调用 Handler
           // 返回响应
       }
   }
   ```

2. **实现简单的 Token 端点测试**
   - `/v1/tokens` POST - 生成 Token
   - `/v1/tokens` GET - 列出 Token
   - 使用 curl 测试

3. **创建 LiteRtAdapter 原型**
   - 封装 `LlmModelHelper.runInference()` 调用
   - 实现基本的 prompt 构建

### 短期目标 (本周内)

4. **完成 /v1/chat/completions 端点**
   - 解析 OpenAI 请求
   - 调用 LiteRT-LM
   - 返回 OpenAI 格式响应

5. **集成到 Gallery App**
   - 添加启动/停止按钮
   - 显示服务器状态
   - Token 管理 UI

6. **端到端测试**
   - Python/JS 客户端测试
   - 性能测试 (延迟、吞吐量)
   - 内存监控

## 参考资源

- **NanoHTTPD**: https://github.com/NanoHttpd/nanohttpd
- **LiteRT-LM Docs**: https://github.com/google-ai-edge/LiteRT-LM
- **OpenAI API**: https://platform.openai.com/docs/api-reference
- **Gallery Source**: https://github.com/google-ai-edge/gallery

## 文档

项目文档位于 `doc/` 目录:
- `Gallery_API_Token_服务改造方案.md` - 完整设计方案
- `IMPLEMENTATION_PROGRESS.md` - 实施进度跟踪
- `QUICK_START.md` - 快速开始指南和使用示例

## 总结

✅ **已完成 30% 的核心架构**
- 数据模型层完整
- Token 管理系统就绪
- 认证中间件可用
- LiteRT-LM 集成路径清晰

🚧 **下一步重点**
- NanoHTTPD 服务器实现
- API 路由和处理器
- LiteRT-LM 适配器开发

预计再需 **2-3 小时** 可完成 MVP (最小可行产品),实现基本的 Token 管理和聊天补全功能。

---

**创建时间**: 2026-05-15  
**当前版本**: 0.1.0-alpha  
**状态**: 开发中
