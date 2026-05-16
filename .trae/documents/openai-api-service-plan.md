# Gallery API - OpenAI 兼容大模型 API 服务实施计划

## 项目背景

基于 `https://github.com/google-ai-edge/gallery.git` (Google AI Edge Gallery)，为其添加 OpenAI 兼容的本地 HTTP API 服务，使外部应用可通过标准 OpenAI API 格式 (`/v1/chat/completions`) 调用设备上运行的 LiteRT-LM 模型（Gemma、Qwen 等），实现完全离线、数据隐私保护的本地推理服务。

## 现状分析

### 已完成（实际存在于磁盘的代码）
| 文件 | 状态 | 说明 |
|------|------|------|
| `api/model/ApiToken.kt` | ✅ 已完成 | Token 数据模型，含脱敏和过期检查 |
| `api/TokenManager.kt` | ✅ 已完成 | Token 生成/验证/撤销/列表/统计，基于 SharedPreferences |
| `build.gradle.kts` | ✅ 已添加 | NanoHTTPD 2.3.1 依赖 |
| `AndroidManifest.xml` | ✅ 已有 | INTERNET、FOREGROUND_SERVICE 权限 |

### 待实现（文档声称已完成但实际不存在）
| 文件 | 优先级 | 说明 |
|------|--------|------|
| `api/model/OpenAiModels.kt` | P0 | OpenAI 请求/响应数据模型 |
| `api/ApiConfig.kt` | P0 | API 配置管理（端口、认证开关等） |
| `api/ApiService.kt` | P0 | NanoHTTPD 服务器核心 |
| `api/ApiServerService.kt` | P0 | Android Foreground Service |
| `api/ApiServerController.kt` | P1 | 服务器启停控制器 |
| `api/middleware/AuthMiddleware.kt` | P0 | Bearer Token 认证中间件 |
| `api/router/ApiRouter.kt` | P0 | 请求路由分发 |
| `api/handler/ChatCompletionHandler.kt` | P0 | 聊天补全处理器 |
| `api/handler/TokenHandler.kt` | P1 | Token 管理接口处理器 |
| `api/response/ResponseBuilder.kt` | P0 | 统一 JSON 响应构建 |
| `api/inference/LiteRtAdapter.kt` | P0 | LiteRT-LM 推理适配器（核心） |
| `ui/home/ApiServiceControlPanel.kt` | P1 | UI 控制面板 |
| `AndroidManifest.xml` 更新 | P0 | 注册 Service |
| `SettingsDialog.kt` 更新 | P1 | 集成控制面板 |
| `di/AppModule.kt` 更新 | P1 | Hilt DI 绑定 |

---

## 实施阶段

### 阶段 1：数据模型与配置层（P0）

#### 1.1 创建 `OpenAiModels.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/model/OpenAiModels.kt`

定义 OpenAI API 兼容的请求/响应数据类：

```kotlin
// 请求模型
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float? = null,
    val max_tokens: Int? = null,
    val top_p: Float? = null,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,       // "system", "user", "assistant"
    val content: String
)

// 响应模型
data class ChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String? = "stop"
)

data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

// 流式响应模型
data class ChatCompletionChunk(
    val id: String,
    val `object`: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>
)

data class ChunkChoice(
    val index: Int,
    val delta: DeltaMessage,
    val finish_reason: String? = null
)

data class DeltaMessage(
    val role: String? = null,
    val content: String? = null
)

// 错误响应
data class OpenAiError(
    val error: ErrorDetail
)

data class ErrorDetail(
    val message: String,
    val type: String = "invalid_request_error",
    val code: String? = null
)

// 模型列表
data class ModelListResponse(
    val `object`: String = "list",
    val data: List<ModelInfo>
)

data class ModelInfo(
    val id: String,
    val `object`: String = "model",
    val created: Long,
    val owned_by: String = "local"
)

// Token 管理请求/响应
data class GenerateTokenRequest(
    val name: String,
    val expires_in_days: Int? = null
)

data class GenerateTokenResponse(
    val token: String,
    val name: String,
    val created_at: Long,
    val expires_at: Long?
)

data class TokenListResponse(
    val data: List<TokenInfo>
)

data class TokenInfo(
    val name: String,
    val token: String,  // 脱敏后的 token
    val created_at: Long,
    val expires_at: Long?,
    val is_active: Boolean,
    val usage_count: Long
)
```

#### 1.2 创建 `ApiConfig.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/ApiConfig.kt`

管理 API 服务的配置项，使用 SharedPreferences 持久化：

```kotlin
class ApiConfig(private val context: Context) {
    companion object {
        const val PREFS_NAME = "api_config"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_AUTH_ENABLED = "auth_enabled"
        const val DEFAULT_PORT = 8080
        const val MIN_PORT = 8080
        const val MAX_PORT = 8099
    }

    var serverPort: Int  // 带范围校验
    var isAuthEnabled: Boolean  // 认证开关
    fun getPortRange(): Pair<Int, Int>
}
```

---

### 阶段 2：HTTP 服务器与 Android Service（P0）

#### 2.1 创建 `ApiService.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/ApiService.kt`

继承 NanoHTTPD，实现核心 HTTP 服务器：

```kotlin
class ApiService(
    private val context: Context,
    port: Int = ApiConfig.DEFAULT_PORT
) : NanoHTTPD(port) {

    private val tokenManager: TokenManager
    private val apiConfig: ApiConfig
    private val router: ApiRouter

    override fun serve(session: IHTTPSession): Response {
        // 1. CORS 预检处理
        // 2. 解析请求体（POST/PUT）
        // 3. 委托 ApiRouter 路由分发
        // 4. 返回 Response
    }

    fun getActualPort(): Int
}
```

关键设计点：
- 构造时注入 Context，内部创建 TokenManager、ApiConfig、ApiRouter
- `serve()` 方法统一处理 CORS、请求体解析、异常捕获
- 支持端口回退（通过构造参数传入不同端口）

#### 2.2 创建 `ApiServerService.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/ApiServerService.kt`

Android Foreground Service，确保 API 服务在后台持续运行：

```kotlin
class ApiServerService : Service() {
    companion object {
        const val ACTION_PORT_UPDATED = "com.google.ai.edge.gallery.api.PORT_UPDATED"
        const val EXTRA_PORT = "extra_port"
        const val NOTIFICATION_CHANNEL_ID = "api_service_channel"
        const val NOTIFICATION_ID = 1001
    }

    private var apiService: ApiService? = null

    override fun onCreate() {
        // 1. 创建通知渠道
        // 2. 启动前台通知
        // 3. 读取配置端口
        // 4. 启动服务器（带端口回退）
        // 5. 广播实际端口
    }

    override fun onDestroy() {
        apiService?.stop()
        apiService = null
    }

    private fun startServerWithFallback(startPort: Int): Int {
        // 从 startPort 开始尝试，直到 MAX_PORT
        // 成功绑定返回实际端口
        // 全部失败抛出 RuntimeException
    }

    private fun broadcastPort(port: Int) {
        // 发送 LocalBroadcast 通知 UI 实际端口
    }
}
```

#### 2.3 更新 `AndroidManifest.xml`

在 `<application>` 标签内添加：

```xml
<service
    android:name=".api.ApiServerService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

---

### 阶段 3：路由与中间件（P0）

#### 3.1 创建 `AuthMiddleware.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/middleware/AuthMiddleware.kt`

```kotlin
object AuthMiddleware {
    sealed class AuthResult {
        data class Valid(val token: String) : AuthResult()
        data class Invalid(val message: String) : AuthResult()
    }

    fun validate(
        headers: Map<String, String>,
        tokenManager: TokenManager,
        authEnabled: Boolean
    ): AuthResult {
        if (!authEnabled) return AuthResult.Valid("anonymous")
        // 从 Authorization header 提取 Bearer Token
        // 调用 tokenManager.validateToken()
    }
}
```

#### 3.2 创建 `ApiRouter.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/router/ApiRouter.kt`

```kotlin
class ApiRouter(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val apiConfig: ApiConfig
) {
    private val chatHandler: ChatCompletionHandler
    private val tokenHandler: TokenHandler

    fun route(
        uri: String,
        method: Method,
        headers: Map<String, String>,
        body: String?
    ): Response {
        return when {
            // 健康检查（无需认证）
            uri == "/health" && method == Method.GET ->
                ResponseBuilder.success(mapOf("status" to "ok"))

            // 模型列表
            uri == "/v1/models" && method == Method.GET ->
                handleModelsList(headers)

            // 聊天补全
            uri == "/v1/chat/completions" && method == Method.POST ->
                chatHandler.handle(headers, body)

            // Token 管理
            uri == "/v1/tokens" && method == Method.POST ->
                tokenHandler.handleGenerate(headers, body)
            uri == "/v1/tokens" && method == Method.GET ->
                tokenHandler.handleList(headers)
            uri.startsWith("/v1/tokens/") && method == Method.DELETE ->
                tokenHandler.handleDelete(headers, uri)

            else -> ResponseBuilder.notFound()
        }
    }
}
```

#### 3.3 创建 `ResponseBuilder.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/response/ResponseBuilder.kt`

```kotlin
object ResponseBuilder {
    private val gson = Gson()

    fun success(data: Any, status: Status = Status.OK): Response
    fun error(message: String, code: Int, type: String = "invalid_request_error"): Response
    fun unauthorized(message: String = "Invalid or missing API key"): Response
    fun notFound(message: String = "Not found"): Response
    fun badRequest(message: String): Response
    fun serverError(message: String): Response
    fun streamChunk(data: String): Response  // SSE 格式
}
```

---

### 阶段 4：LiteRT-LM 推理适配器（P0 - 核心）

#### 4.1 创建 `LiteRtAdapter.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/inference/LiteRtAdapter.kt`

这是整个项目最核心的组件，负责将 OpenAI 格式请求转换为 LiteRT-LM 推理调用：

```kotlin
class LiteRtAdapter(private val context: Context) {
    companion object {
        private const val TAG = "LiteRtAdapter"
    }

    // 获取当前已加载的模型
    fun getLoadedModels(): List<Model>

    // 同步推理（非流式）
    fun complete(request: ChatCompletionRequest): ChatCompletionResponse

    // 流式推理（SSE）
    fun completeStream(
        request: ChatCompletionRequest,
        onChunk: (ChatCompletionChunk) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    )

    // 将 OpenAI messages 转换为 LiteRT 输入
    private fun buildPrompt(messages: List<ChatMessage>): String

    // 查找匹配的已加载模型
    private fun findModel(modelName: String): Model?

    // Token 估算（LiteRT 不提供精确 token 计数）
    private fun estimateTokens(text: String): Int
}
```

**关键设计决策**：

1. **模型发现**：通过 `ModelManagerViewModel` 或 Application 级别获取已加载的模型列表，匹配 `request.model` 字段
2. **推理调用**：复用 `LlmModelHelper.runInference()` 接口，通过 `model.runtimeHelper` 获取正确的运行时实现
3. **消息格式转换**：将 OpenAI 的 `messages` 数组拼接为 LiteRT 可理解的文本格式
4. **同步包装**：`runInference` 是异步回调模式，需要使用 `CountDownLatch` 或 `suspendCancellableCoroutine` 包装为同步调用
5. **流式支持**：利用 `ResultListener` 的 `partialResult` 回调实现 SSE 流式输出

**推理调用流程**：
```
ChatCompletionRequest
    → findModel(request.model)
    → buildPrompt(request.messages)
    → model.runtimeHelper.runInference(model, prompt, resultListener, ...)
    → 收集完整响应（或流式 chunk）
    → 构建 ChatCompletionResponse
```

**同步推理实现策略**（关键）：
```kotlin
fun complete(request: ChatCompletionRequest): ChatCompletionResponse {
    val model = findModel(request.model) ?: throw ModelNotFoundException()
    val prompt = buildPrompt(request.messages)

    val result = StringBuilder()
    val latch = CountDownLatch(1)
    var error: String? = null

    model.runtimeHelper.runInference(
        model = model,
        input = prompt,
        resultListener = { partialResult, done, _ ->
            if (done) {
                latch.countDown()
            } else {
                result.append(partialResult)
            }
        },
        cleanUpListener = { latch.countDown() },
        onError = { errorMessage ->
            error = errorMessage
            latch.countDown()
        }
    )

    latch.await(request.max_tokens?.toLong()?.times(100) ?: 300_000, TimeUnit.MILLISECONDS)

    // 构建 OpenAI 格式响应
    return ChatCompletionResponse(...)
}
```

**模型名称映射**：
- `request.model` 值如 `"gemma-3n-e2b-it"` → 匹配 `Model.name` 或 `Model.displayName`
- 支持模糊匹配（忽略大小写、简写）
- 如果模型未加载，返回错误提示

---

### 阶段 5：API 处理器（P0）

#### 5.1 创建 `ChatCompletionHandler.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/handler/ChatCompletionHandler.kt`

```kotlin
class ChatCompletionHandler(
    private val liteRtAdapter: LiteRtAdapter,
    private val tokenManager: TokenManager,
    private val apiConfig: ApiConfig
) {
    fun handle(headers: Map<String, String>, body: String?): Response {
        // 1. 认证检查
        // 2. 解析请求体（Gson -> ChatCompletionRequest）
        // 3. 请求校验（messages 非空、model 存在）
        // 4. 判断 stream 参数
        //    - stream=false: 调用 liteRtAdapter.complete()，返回完整响应
        //    - stream=true: 调用 liteRtAdapter.completeStream()，返回 SSE 响应
        // 5. 更新 Token 使用统计
        // 6. 返回 Response
    }

    private fun handleNonStream(request: ChatCompletionRequest): Response
    private fun handleStream(request: ChatCompletionRequest): Response
}
```

#### 5.2 创建 `TokenHandler.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/handler/TokenHandler.kt`

```kotlin
class TokenHandler(
    private val tokenManager: TokenManager,
    private val apiConfig: ApiConfig
) {
    fun handleGenerate(headers: Map<String, String>, body: String?): Response {
        // POST /v1/tokens - 生成新 Token（始终需要认证）
    }

    fun handleList(headers: Map<String, String>): Response {
        // GET /v1/tokens - 列出所有 Token
    }

    fun handleDelete(headers: Map<String, String>, uri: String): Response {
        // DELETE /v1/tokens/{token} - 撤销 Token
    }
}
```

---

### 阶段 6：服务器控制器（P1）

#### 6.1 创建 `ApiServerController.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/ApiServerController.kt`

```kotlin
object ApiServerController {
    private var isRunning: Boolean = false
    private var actualPort: Int = 0

    fun startServer(context: Context) {
        // 启动 ApiServerService
    }

    fun stopServer(context: Context) {
        // 停止 ApiServerService
    }

    fun isServerRunning(): Boolean = isRunning
    fun getActualPort(): Int = actualPort

    fun updateState(running: Boolean, port: Int) {
        isRunning = running
        actualPort = port
    }
}
```

---

### 阶段 7：UI 控制面板（P1）

#### 7.1 创建 `ApiServiceControlPanel.kt`
**路径**: `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/ApiServiceControlPanel.kt`

Jetpack Compose 组件，嵌入到 SettingsDialog 中：

```kotlin
@Composable
fun ApiServiceControlPanel(modifier: Modifier = Modifier) {
    Card {
        Column {
            // 1. 标题 "API Service"
            // 2. 服务状态指示（Running/Stopped）
            // 3. 启动/停止按钮
            // 4. 端口配置输入框（仅停止时可编辑）
            // 5. 服务器地址显示 + 复制按钮
            // 6. 端口回退提示（实际端口 ≠ 配置端口时）
            // 7. 认证开关 "Require API Key"
            // 8. 认证禁用警告
        }
    }
}
```

#### 7.2 更新 `SettingsDialog.kt`

在设置对话框中添加 `ApiServiceControlPanel`：
- 位置：HuggingFace Token 之后，Third-party libraries 之前
- 创建 `ApiConfig` 实例并传入

---

### 阶段 8：Hilt DI 集成（P1）

#### 8.1 更新 `di/AppModule.kt`

添加 API 模块相关的依赖绑定：

```kotlin
@Provides @Singleton
fun provideTokenManager(@ApplicationContext context: Context): TokenManager

@Provides @Singleton
fun provideApiConfig(@ApplicationContext context: Context): ApiConfig

@Provides @Singleton
fun provideLiteRtAdapter(@ApplicationContext context: Context): LiteRtAdapter
```

---

### 阶段 9：流式响应（SSE）支持（P2）

#### 9.1 SSE 实现方案

NanoHTTPD 支持 `ChunkedResponse`，可用于实现 Server-Sent Events：

```kotlin
private fun handleStream(request: ChatCompletionRequest): Response {
    return newChunkedResponse(
        Status.OK,
        "text/event-stream",
        PipedInputStream().also { input ->
            CoroutineScope(Dispatchers.IO).launch {
                PipedOutputStream(input).use { output ->
                    liteRtAdapter.completeStream(
                        request,
                        onChunk = { chunk ->
                            output.write("data: ${gson.toJson(chunk)}\n\n".toByteArray())
                            output.flush()
                        },
                        onDone = {
                            output.write("data: [DONE]\n\n".toByteArray())
                            output.flush()
                        },
                        onError = { throwable ->
                            // 写入错误事件
                        }
                    )
                }
            }
        }
    )
}
```

---

### 阶段 10：增强功能（P3）

- **Token 加密存储**：迁移到 EncryptedSharedPreferences
- **速率限制**：基于 Token 的请求频率限制
- **CORS 支持**：允许浏览器跨域访问
- **LAN IP 显示**：自动检测并显示局域网 IP
- **更多 OpenAI 端点**：`/v1/completions`、`/v1/embeddings`
- **HTTPS 支持**：自签名证书

---

## 文件创建/修改清单

### 新建文件（12 个）

| # | 文件路径 | 阶段 | 行数估计 |
|---|---------|------|---------|
| 1 | `api/model/OpenAiModels.kt` | 1 | ~150 |
| 2 | `api/ApiConfig.kt` | 1 | ~60 |
| 3 | `api/ApiService.kt` | 2 | ~120 |
| 4 | `api/ApiServerService.kt` | 2 | ~150 |
| 5 | `api/middleware/AuthMiddleware.kt` | 3 | ~50 |
| 6 | `api/router/ApiRouter.kt` | 3 | ~80 |
| 7 | `api/response/ResponseBuilder.kt` | 3 | ~80 |
| 8 | `api/inference/LiteRtAdapter.kt` | 4 | ~200 |
| 9 | `api/handler/ChatCompletionHandler.kt` | 5 | ~150 |
| 10 | `api/handler/TokenHandler.kt` | 5 | ~100 |
| 11 | `api/ApiServerController.kt` | 6 | ~60 |
| 12 | `ui/home/ApiServiceControlPanel.kt` | 7 | ~200 |

### 修改文件（3 个）

| # | 文件路径 | 阶段 | 修改内容 |
|---|---------|------|---------|
| 1 | `AndroidManifest.xml` | 2 | 添加 ApiServerService 声明 |
| 2 | `ui/home/SettingsDialog.kt` | 7 | 添加 ApiServiceControlPanel |
| 3 | `di/AppModule.kt` | 8 | 添加 API 模块依赖绑定 |

**总计**：新建约 1400 行代码，修改约 50 行

---

## API 端点规范

| 端点 | 方法 | 认证 | 功能 |
|------|------|------|------|
| `/health` | GET | 无 | 健康检查 |
| `/v1/models` | GET | 可选 | 列出可用模型 |
| `/v1/chat/completions` | POST | 可选 | OpenAI 兼容聊天补全 |
| `/v1/tokens` | POST | 必须 | 生成 API Token |
| `/v1/tokens` | GET | 必须 | 列出所有 Token |
| `/v1/tokens/{token}` | DELETE | 必须 | 撤销 Token |

---

## 技术风险与应对

| 风险 | 影响 | 应对策略 |
|------|------|---------|
| LiteRT-LM 推理是异步回调，API 需同步返回 | 高 | 使用 CountDownLatch 或 suspendCancellableCoroutine 包装 |
| 模型可能未加载或正在初始化 | 中 | 返回 503 Service Unavailable + 友好错误信息 |
| NanoHTTPD 单线程处理可能阻塞 | 中 | 使用 CoroutineScope(Dispatchers.IO) 处理耗时操作 |
| Android 后台 Service 被系统杀死 | 中 | Foreground Service + 通知保活 |
| 并发推理请求冲突 | 低 | 单模型串行推理队列（LiteRT 不支持并行） |

---

## 实施顺序（推荐执行顺序）

1. **OpenAiModels.kt** → 数据模型是所有组件的基础
2. **ApiConfig.kt** → 配置管理，后续组件依赖
3. **ResponseBuilder.kt** → 统一响应格式
4. **AuthMiddleware.kt** → 认证逻辑
5. **LiteRtAdapter.kt** → 核心推理适配器
6. **TokenHandler.kt** → Token 管理接口
7. **ChatCompletionHandler.kt** → 聊天补全接口
8. **ApiRouter.kt** → 路由分发
9. **ApiService.kt** → HTTP 服务器
10. **ApiServerService.kt** → Android Service
11. **AndroidManifest.xml** → 注册 Service
12. **ApiServerController.kt** → 控制器
13. **ApiServiceControlPanel.kt** → UI 面板
14. **SettingsDialog.kt** → UI 集成
15. **AppModule.kt** → DI 集成

---

## 验证方案

### 基本功能测试
```bash
# 1. 健康检查
curl http://127.0.0.1:8080/health

# 2. 生成 Token
curl -X POST http://127.0.0.1:8080/v1/tokens \
  -H "Content-Type: application/json" \
  -d '{"name":"test","expires_in_days":30}'

# 3. 列出模型
curl http://127.0.0.1:8080/v1/models

# 4. 聊天补全（非流式）
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-xxx" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma-3n-e2b-it","messages":[{"role":"user","content":"Hello!"}]}'

# 5. 聊天补全（流式）
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-xxx" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma-3n-e2b-it","messages":[{"role":"user","content":"Hello!"}],"stream":true}'
```

### OpenAI SDK 兼容性测试
```python
from openai import OpenAI

client = OpenAI(
    base_url="http://127.0.0.1:8080/v1",
    api_key="sk-xxx"  # 或任意值（认证禁用时）
)

response = client.chat.completions.create(
    model="gemma-3n-e2b-it",
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```
