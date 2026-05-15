# Gallery API Token Service - Implementation Progress

## 已完成的工作

### ✅ 第一阶段:项目初始化
- [x] Clone Google AI Edge Gallery repository
- [x] 分析现有项目结构和 LiteRT-LM 集成方式
- [x] 添加 NanoHTTPD 依赖到 `libs.versions.toml` 和 `build.gradle.kts`

### ✅ 第二阶段:API Token 管理系统
- [x] 创建 `ApiToken.kt` - Token 数据模型
- [x] 创建 `OpenAiModels.kt` - OpenAI 兼容的请求/响应模型
- [x] 创建 `TokenManager.kt` - Token 生成、验证、存储管理

### ✅ 第三阶段:认证中间件
- [x] 创建 `AuthMiddleware.kt` - Bearer Token 验证中间件

## 待完成的工作

### 🔄 第四阶段:NanoHTTPD 服务器 (进行中)
需要创建以下文件:
- [ ] `ApiService.kt` - NanoHTTPD 服务器实现
- [ ] `ApiServerService.kt` - Android Foreground Service
- [ ] `ApiServerController.kt` - 服务器生命周期管理

### 🔄 第五阶段:API 路由和处理
需要创建以下文件:
- [ ] `ApiRouter.kt` - 请求路由分发
- [ ] `ResponseBuilder.kt` - 统一响应构建
- [ ] `ChatCompletionHandler.kt` - /v1/chat/completions 处理器
- [ ] `TokenHandler.kt` - /v1/tokens 处理器

### 🔄 第六阶段:LiteRT-LM 推理集成
需要创建以下文件:
- [ ] `LiteRtAdapter.kt` - 将 OpenAI 请求转换为 LiteRT-LM 调用
- [ ] 研究并复用现有的 `LlmModelHelper` 接口

### 🔄 第七阶段:配置和文档
- [ ] 更新 `AndroidManifest.xml` - 添加权限和 Service 声明
- [ ] 创建 API 使用文档和示例
- [ ] 添加 UI 控制入口(可选)

## 关键发现

### LiteRT-LM 集成方式
Gallery 使用以下架构进行推理:

1. **LlmModelHelper 接口** (`runtime/LlmModelHelper.kt`)
   - `initialize()` - 初始化模型
   - `resetConversation()` - 重置对话上下文
   - `runInference()` - 执行推理(支持流式响应)
   - `stopResponse()` - 停止响应生成

2. **推理调用方式**:
```kotlin
model.runtimeHelper.runInference(
    model = model,
    input = input,
    resultListener = { partialResult, done, thinkingResult ->
        // 处理流式响应
    },
    onError = { errorMessage ->
        // 处理错误
    }
)
```

3. **依赖库**:
   - `com.google.ai.edge.litertlm:litertlm-android:0.11.0`
   - 使用 `Conversation` 和 `MessageCallback` 进行异步推理

## 下一步行动

### 立即可执行的步骤:

1. **创建 NanoHTTPD 服务器基础结构**
   ```bash
   # 在 gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/ 目录下创建:
   - ApiService.kt
   - ApiServerService.kt  
   - ApiServerController.kt
   ```

2. **实现核心 API 端点**
   - `/v1/chat/completions` (POST) - 聊天补全
   - `/v1/tokens` (POST) - 生成新 Token
   - `/v1/tokens` (GET) - 列出所有 Token

3. **集成 LiteRT-LM**
   - 通过 `LlmModelHelper` 接口调用现有推理逻辑
   - 将 OpenAI 格式消息转换为 LiteRT-LM 的 `Contents` 格式

4. **测试 API**
   ```bash
   # 启动服务器后测试
   curl -X POST http://127.0.0.1:8080/v1/tokens \
     -H "Content-Type: application/json" \
     -d '{"name": "test", "expires_in_days": 30}'
   ```

## 技术注意事项

### Token 存储
当前实现使用内存存储(`mutableMapOf`)，生产环境应改为:
- **EncryptedSharedPreferences** - 简单场景
- **Room Database + SQLCipher** - 复杂查询需求

### 线程安全
- NanoHTTPD 的请求处理在独立线程中
- 使用 Kotlin Coroutines (`Dispatchers.IO`) 处理异步操作
- Token 访问需要同步机制 (当前实现简单,生产环境需加锁)

### 内存管理
- LiteRT-LM 模型占用大量内存 (2-4GB)
- API 服务应与 Gallery App 共享模型实例
- 避免重复加载模型

### 安全性
- Token 以 `sk-` 前缀开头 (类似 OpenAI)
- 所有敏感端点需要 Bearer Token 认证
- 建议仅监听 localhost (127.0.0.1)

## 文件结构

```
gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/api/
├── model/
│   ├── ApiToken.kt              ✅ 已完成
│   └── OpenAiModels.kt          ✅ 已完成
├── middleware/
│   └── AuthMiddleware.kt        ✅ 已完成
├── TokenManager.kt              ✅ 已完成
├── ApiService.kt                ⏳ 待创建
├── ApiServerService.kt          ⏳ 待创建
├── ApiServerController.kt       ⏳ 待创建
├── router/
│   └── ApiRouter.kt             ⏳ 待创建
├── handler/
│   ├── ChatCompletionHandler.kt ⏳ 待创建
│   └── TokenHandler.kt          ⏳ 待创建
├── response/
│   └── ResponseBuilder.kt       ⏳ 待创建
└── inference/
    └── LiteRtAdapter.kt         ⏳ 待创建
```

## 参考资源

- **NanoHTTPD 文档**: https://github.com/NanoHttpd/nanohttpd
- **LiteRT-LM Kotlin API**: https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- **OpenAI API 参考**: https://platform.openai.com/docs/api-reference/chat/create

## 预计完成时间

基于当前进度,预计还需要 2-3 小时完成核心功能:
- NanoHTTPD 服务器集成: 30 分钟
- API 路由和处理器: 45 分钟
- LiteRT-LM 适配器: 45 分钟
- 测试和调试: 30 分钟

---

**最后更新**: 2026-05-15
**状态**: 基础架构完成 30%,核心功能开发中
