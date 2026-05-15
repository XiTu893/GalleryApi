# Gallery API Token 服务改造方案

## 项目概述

将 Google AI Edge Gallery 改造成支持本地 API Token 服务的 Android 应用,提供完全兼容 OpenAI 格式的 API 接口,同时保留原有 App 功能。

## 技术架构

### 核心组件
- **NanoHTTPD**: 轻量级嵌入式 HTTP 服务器,运行在 Android Service 中
- **LiteRT-LM**: Google 端侧 LLM 推理引擎,负责模型推理
- **Token 管理器**: 管理 API Token 的生成、验证和过期
- **OpenAI 适配器**: 将 OpenAI 格式请求转换为 LiteRT-LM 调用

### 架构层次
```
外部应用/客户端
    ↓ (HTTP Request with Bearer Token)
NanoHTTPD Server (Android Service)
    ↓ (Token Validation)
API Router & Handler
    ↓ (Request Parsing)
OpenAI Adapter
    ↓ (Prompt Engineering)
LiteRT-LM Inference Engine
    ↓ (Model Response)
Response Formatter
    ↓ (JSON Response)
Client
```

## 实施步骤

### 第一阶段:项目初始化和依赖配置

#### 1.1 Clone Gallery 源码
```bash
git clone https://github.com/google-ai-edge/gallery.git
cd gallery
```

#### 1.2 分析现有项目结构
需要重点关注的目录:
- `app/src/main/java/com/google/ai/edge/gallery/` - 主应用代码
- 查找 LiteRT-LM 集成位置(通常在 inference 或 model 相关包)
- 查看现有的模型管理和推理调用逻辑

#### 1.3 添加 NanoHTTPD 依赖
在 `app/build.gradle.kts` 或 `build.gradle` 中添加:
```gradle
dependencies {
    // NanoHTTPD for embedded HTTP server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // JSON parsing (如果项目中还没有)
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines for async operations (如果项目中还没有)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 第二阶段:API Token 管理系统

#### 2.1 创建 Token 数据模型
文件: `app/src/main/java/com/google/ai/edge/gallery/api/model/ApiToken.kt`

```kotlin
data class ApiToken(
    val token: String,
    val name: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val isActive: Boolean = true,
    val usageCount: Long = 0
)
```

#### 2.2 创建 Token 管理器
文件: `app/src/main/java/com/google/ai/edge/gallery/api/TokenManager.kt`

核心功能:
- 生成随机 Token (UUID 或自定义格式)
- Token 存储 (使用 Room Database 或 SharedPreferences)
- Token 验证 (检查有效性、过期时间)
- Token 撤销和刷新
- 使用统计追踪

关键方法:
```kotlin
interface TokenManager {
    suspend fun generateToken(name: String, expiresInDays: Int?): ApiToken
    suspend fun validateToken(token: String): Boolean
    suspend fun revokeToken(token: String): Boolean
    suspend fun listTokens(): List<ApiToken>
    suspend fun incrementUsage(token: String)
}
```

#### 2.3 创建 Token 管理 UI (可选但推荐)
在 Gallery App 设置页面添加:
- Token 列表展示
- 新建 Token 按钮
- Token 详情 (复制、撤销、查看使用情况)

### 第三阶段:NanoHTTPD 服务器集成

#### 3.1 创建 API Service
文件: `app/src/main/java/com/google/ai/edge/gallery/api/ApiService.kt`

继承 `NanoHTTPD`,实现核心服务器逻辑:

```kotlin
class ApiService(private val context: Context) : NanoHTTPD(PORT) {
    companion object {
        const val PORT = 8080
    }
    
    override fun serve(session: IHTTPSession): Response {
        // 1. 路由匹配
        // 2. Token 验证
        // 3. 请求处理
        // 4. 返回响应
    }
}
```

#### 3.2 创建 Foreground Service
文件: `app/src/main/java/com/google/ai/edge/gallery/api/ApiServerService.kt`

确保服务器在后台持续运行:
```kotlin
class ApiServerService : Service() {
    private var apiService: ApiService? = null
    
    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        apiService = ApiService(applicationContext).apply {
            start()
        }
    }
    
    override fun onDestroy() {
        apiService?.stop()
        super.onDestroy()
    }
}
```

#### 3.3 添加权限和 Service 声明
在 `AndroidManifest.xml` 中添加:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<service
    android:name=".api.ApiServerService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### 第四阶段:OpenAI 兼容 API 实现

#### 4.1 定义 OpenAI 请求/响应模型
文件: `app/src/main/java/com/google/ai/edge/gallery/api/model/OpenAiModels.kt`

**请求模型:**
```kotlin
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float? = 0.7f,
    val max_tokens: Int? = null,
    val top_p: Float? = 1.0f,
    val stream: Boolean = false
)

data class Message(
    val role: String, // "system", "user", "assistant"
    val content: String
)
```

**响应模型:**
```kotlin
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
    val message: Message,
    val finish_reason: String
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)
```

#### 4.2 创建 API 路由器
文件: `app/src/main/java/com/google/ai/edge/gallery/api/router/ApiRouter.kt`

处理不同端点:
```kotlin
class ApiRouter(private val context: Context) {
    fun handleRequest(uri: String, method: Method, headers: Map<String, String>, body: String?): Response {
        return when {
            uri == "/v1/chat/completions" && method == Method.POST -> handleChatCompletions(headers, body)
            uri == "/v1/tokens" && method == Method.POST -> handleGenerateToken(headers, body)
            uri == "/v1/tokens" && method == Method.GET -> handleListTokens(headers)
            else -> createErrorResponse(404, "Not Found")
        }
    }
}
```

#### 4.3 实现 Token 验证中间件
文件: `app/src/main/java/com/google/ai/edge/gallery/api/middleware/AuthMiddleware.kt`

从 `Authorization: Bearer <token>` header 中提取并验证 Token:
```kotlin
object AuthMiddleware {
    fun validateAuthorization(headers: Map<String, String>): ValidationResult {
        val authHeader = headers["authorization"] ?: return ValidationResult.Invalid("Missing authorization header")
        
        if (!authHeader.startsWith("Bearer ")) {
            return ValidationResult.Invalid("Invalid authorization format")
        }
        
        val token = authHeader.substringAfter("Bearer ")
        return if (TokenManager.validateToken(token)) {
            ValidationResult.Valid(token)
        } else {
            ValidationResult.Invalid("Invalid or expired token")
        }
    }
}
```

### 第五阶段:LiteRT-LM 推理集成

#### 5.1 研究现有 Gallery 推理逻辑
需要找到:
- LiteRT-LM 初始化代码
- 模型加载逻辑
- 推理调用方法 (通常是同步或异步的 generate/predict 方法)
- 现有的 prompt 处理方式

关键搜索路径:
- 查找包含 "LiteRT"、"LlmEngine"、"Inference" 的文件
- 查看 Gallery 中 AI Chat 功能的实现

#### 5.2 创建推理适配器
文件: `app/src/main/java/com/google/ai/edge/gallery/api/inference/LiteRtAdapter.kt`

将 OpenAI 请求转换为 LiteRT-LM 调用:

```kotlin
class LiteRtAdapter(private val context: Context) {
    
    // 可能需要注入或获取现有的 LiteRT-LM 实例
    private val llmEngine: LlmEngine? = null // 需要从 Gallery 现有代码中获取
    
    suspend fun generateCompletion(request: ChatCompletionRequest): ChatCompletionResponse {
        // 1. 构建完整 prompt (拼接 system + user messages)
        val prompt = buildPrompt(request.messages)
        
        // 2. 配置推理参数
        val config = InferenceConfig(
            temperature = request.temperature ?: 0.7f,
            maxTokens = request.max_tokens,
            topP = request.top_p ?: 1.0f
        )
        
        // 3. 调用 LiteRT-LM 推理
        val result = llmEngine?.generate(prompt, config)
        
        // 4. 构建 OpenAI 格式响应
        return ChatCompletionResponse(
            id = generateId(),
            created = System.currentTimeMillis() / 1000,
            model = request.model,
            choices = listOf(
                Choice(
                    index = 0,
                    message = Message(role = "assistant", content = result?.text ?: ""),
                    finish_reason = "stop"
                )
            ),
            usage = Usage(
                prompt_tokens = countTokens(prompt),
                completion_tokens = countTokens(result?.text ?: ""),
                total_tokens = countTokens(prompt) + countTokens(result?.text ?: "")
            )
        )
    }
    
    private fun buildPrompt(messages: List<Message>): String {
        // 根据消息角色构建适合 LiteRT-LM 的 prompt 格式
        return messages.joinToString("\n") { "${it.role}: ${it.content}" }
    }
}
```

#### 5.3 处理流式响应 (如果需求需要)
如果需要支持 `stream: true`,需要实现 Server-Sent Events (SSE):

文件: `app/src/main/java/com/google/ai/edge/gallery/api/streaming/SseHandler.kt`

```kotlin
fun handleStreamingCompletion(request: ChatCompletionRequest): Response {
    // 使用 chunked response
    // 每次生成一个 token 就发送一个 SSE event
    // 格式: data: {"choices": [{"delta": {"content": "某字符"}}]}\n\n
}
```

### 第六阶段:请求处理和错误处理

#### 6.1 创建统一的响应构建器
文件: `app/src/main/java/com/google/ai/edge/gallery/api/response/ResponseBuilder.kt`

```kotlin
object ResponseBuilder {
    fun success(data: Any, statusCode: Int = 200): Response {
        return newFixedLengthResponse(
            Status.OK,
            "application/json",
            Gson().toJson(data)
        )
    }
    
    fun error(code: Int, message: String): Response {
        return newFixedLengthResponse(
            Status.valueOf(code),
            "application/json",
            Gson().toJson(mapOf("error" to mapOf("message" to message, "code" to code)))
        )
    }
}
```

#### 6.2 实现完整的 chat/completions handler
文件: `app/src/main/java/com/google/ai/edge/gallery/api/handler/ChatCompletionHandler.kt`

```kotlin
class ChatCompletionHandler(
    private val tokenManager: TokenManager,
    private val liteRtAdapter: LiteRtAdapter
) {
    suspend fun handle(headers: Map<String, String>, body: String?): Response {
        // 1. 验证 Token
        val authResult = AuthMiddleware.validateAuthorization(headers)
        if (!authResult.isValid) {
            return ResponseBuilder.error(401, authResult.message)
        }
        
        // 2. 解析请求体
        val request = try {
            Gson().fromJson(body, ChatCompletionRequest::class.java)
        } catch (e: Exception) {
            return ResponseBuilder.error(400, "Invalid JSON body")
        }
        
        // 3. 验证请求参数
        val validationError = validateRequest(request)
        if (validationError != null) {
            return ResponseBuilder.error(400, validationError)
        }
        
        // 4. 执行推理
        return try {
            val response = liteRtAdapter.generateCompletion(request)
            
            // 5. 更新 Token 使用统计
            tokenManager.incrementUsage(authResult.token)
            
            ResponseBuilder.success(response)
        } catch (e: Exception) {
            ResponseBuilder.error(500, "Inference failed: ${e.message}")
        }
    }
    
    private fun validateRequest(request: ChatCompletionRequest): String? {
        if (request.messages.isEmpty()) {
            return "messages array cannot be empty"
        }
        // 更多验证...
        return null
    }
}
```

### 第七阶段:服务器生命周期管理

#### 7.1 创建服务器控制器
文件: `app/src/main/java/com/google/ai/edge/gallery/api/ApiServerController.kt`

管理服务器的启动、停止和状态:

```kotlin
class ApiServerController(private val context: Context) {
    var isRunning: Boolean = false
        private set
    
    fun startServer() {
        if (isRunning) return
        
        val intent = Intent(context, ApiServerService::class.java)
        context.startForegroundService(intent)
        isRunning = true
        
        // 发送通知告知用户服务器已启动
        showNotification("API Server Started on port 8080")
    }
    
    fun stopServer() {
        if (!isRunning) return
        
        val intent = Intent(context, ApiServerService::class.java)
        context.stopService(intent)
        isRunning = false
        
        showNotification("API Server Stopped")
    }
}
```

#### 7.2 添加 UI 控制入口
在 Gallery App 的设置或开发者选项页面添加:
- "启用 API 服务" 开关
- 显示当前服务器状态 (运行/停止)
- 显示服务器地址 (如 http://127.0.0.1:8080)
- 快速访问 Token 管理

### 第八阶段:测试和调试

#### 8.1 单元测试
为以下组件编写测试:
- `TokenManager` - Token 生成、验证、过期逻辑
- `AuthMiddleware` - 认证流程
- `LiteRtAdapter` - Prompt 构建和响应格式化
- `ApiRouter` - 路由逻辑

#### 8.2 集成测试
使用 curl 或 Postman 测试 API:

```bash
# 生成 Token
curl -X POST http://127.0.0.1:8080/v1/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "test-token", "expires_in_days": 30}'

# 调用聊天接口
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [
      {"role": "user", "content": "Hello, how are you?"}
    ],
    "temperature": 0.7
  }'
```

#### 8.3 性能测试
- 并发请求处理能力
- Token 验证延迟
- 推理响应时间
- 内存占用监控

### 第九阶段:安全性和优化

#### 9.1 安全加固
- Token 加密存储 (使用 Android Keystore)
- HTTPS 支持 (可选,使用自签名证书)
- 速率限制 (防止滥用)
- IP 白名单 (如果需要)
- 请求日志记录 (用于审计)

#### 9.2 性能优化
- 使用线程池处理并发请求
- 推理请求队列管理
- 缓存常用响应
- 懒加载模型 (按需加载,节省内存)

#### 9.3 错误恢复
- 服务器崩溃自动重启
- 模型加载失败降级处理
- Token 数据库损坏恢复

### 第十阶段:文档和使用指南

#### 10.1 API 文档
创建 `API_DOCUMENTATION.md`,包含:
- 认证方式说明
- 所有可用端点列表
- 请求/响应示例
- 错误码说明
- 速率限制说明

#### 10.2 集成示例
提供常见语言的调用示例:
- Python (requests 库)
- JavaScript (fetch API)
- cURL 命令
- Android Kotlin 示例

#### 10.3 故障排除指南
常见问题和解决方案:
- 服务器无法启动
- Token 验证失败
- 推理超时
- 内存不足错误

## 关键技术挑战与解决方案

### 挑战 1: LiteRT-LM 实例共享
**问题**: Gallery App 可能已有 LiteRT-LM 实例,API Service 需要复用而非重新创建

**解决方案**:
- 使用 Dependency Injection (Hilt/Koin) 管理单例
- 或者通过 Application Context 共享引擎实例
- 确保线程安全 (推理可能是阻塞操作)

### 挑战 2: 后台服务保活
**问题**: Android 系统可能杀死后台 Service

**解决方案**:
- 使用 Foreground Service + Notification
- 请求忽略电池优化 (需要用户授权)
- 监听系统广播,在适当时机重启服务

### 挑战 3: 流式响应实现
**问题**: NanoHTTPD 默认不支持 chunked transfer encoding

**解决方案**:
- 使用 `ChunkedResponse` 类
- 或者切换到支持 SSE 的框架 (如 Ktor)
- 或者先实现非流式,后续迭代添加

### 挑战 4: 多模型支持
**问题**: Gallery 支持多个模型,API 需要指定使用哪个

**解决方案**:
- 在请求中通过 `model` 字段指定
- 维护模型名称到 LiteRT-LM 配置的映射
- 动态加载/切换模型 (注意内存管理)

## 文件结构概览

```
app/src/main/java/com/google/ai/edge/gallery/
├── api/
│   ├── ApiService.kt                    # NanoHTTPD 服务器
│   ├── ApiServerService.kt              # Foreground Service
│   ├── ApiServerController.kt           # 服务器控制器
│   ├── TokenManager.kt                  # Token 管理
│   ├── middleware/
│   │   └── AuthMiddleware.kt            # 认证中间件
│   ├── router/
│   │   └── ApiRouter.kt                 # 路由分发
│   ├── handler/
│   │   ├── ChatCompletionHandler.kt     # 聊天接口处理
│   │   └── TokenHandler.kt              # Token 管理接口
│   ├── model/
│   │   ├── ApiToken.kt                  # Token 数据模型
│   │   └── OpenAiModels.kt              # OpenAI 请求/响应模型
│   ├── inference/
│   │   └── LiteRtAdapter.kt             # LiteRT-LM 适配器
│   ├── response/
│   │   └── ResponseBuilder.kt           # 响应构建工具
│   └── streaming/
│       └── SseHandler.kt                # SSE 流式处理 (可选)
├── data/
│   └── local/
│       └── TokenDao.kt                  # Room DAO for tokens
└── ui/
    └── settings/
        └── ApiSettingsFragment.kt       # API 设置 UI (可选)
```

## 验收标准

1. ✅ 成功启动本地 HTTP 服务器在端口 8080
2. ✅ Token 生成、验证、撤销功能正常工作
3. ✅ `/v1/chat/completions` 接口完全兼容 OpenAI 格式
4. ✅ 能够正确调用 LiteRT-LM 进行推理
5. ✅ 支持至少一个 Gallery 中的模型 (如 Gemma-4-E2B-it)
6. ✅ 错误处理完善,返回标准错误格式
7. ✅ 服务器作为 Foreground Service 稳定运行
8. ✅ 通过 curl/Postman 测试所有接口
9. ✅ 内存和性能在合理范围内
10. ✅ 提供清晰的 API 文档和使用示例

## 后续扩展方向

1. **流式响应**: 实现 `stream: true` 支持
2. **更多 OpenAI 端点**: `/v1/completions`, `/v1/embeddings`
3. **WebUI**: 内置简单的 Web 界面用于测试和管理
4. **远程访问**: 支持局域网内其他设备访问 (需要配置防火墙)
5. **插件系统**: 允许扩展自定义 API 端点
6. **监控面板**: 实时显示 API 使用统计和性能指标

## 注意事项

1. **许可证合规**: 确保改造后的代码遵守 Gallery 的开源许可证
2. **隐私保护**: API Token 和用户数据必须加密存储
3. **资源管理**: 监控内存使用,避免 OOM
4. **用户体验**: API 服务不应影响 Gallery App 的正常使用
5. **安全性**: 默认仅监听 localhost,如需远程访问需明确警告用户
