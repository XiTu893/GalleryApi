# Gallery API 服务实施计划

## 📋 项目目标

为 Google AI Edge Gallery 添加本地 API Token 服务，提供 OpenAI 兼容的 `/v1/chat/completions` 接口。

### 核心价值
- ✅ **完全离线运行** - 无需云端，所有计算在本地设备
- ✅ **数据隐私保护** - 用户数据永不离开设备
- ✅ **OpenAI API 兼容** - 现有应用可无缝切换
- ✅ **Token 认证管理** - 安全的访问控制
- ✅ **UI 控制面板** - 直观的 API 服务管理

---

## 🎯 实施阶段

### 阶段 1: 核心基础架构 (优先级: 🔴 高)
**预计时间**: 2-3小时

#### 1.1 依赖配置
- [ ] 添加 NanoHTTPD 依赖到 `build.gradle.kts`
- [ ] 确认 Gson 和 Coroutines 依赖已存在

#### 1.2 数据模型层
- [x] `ApiToken.kt` - Token 数据模型 ✅ 已创建
- [ ] `OpenAiModels.kt` - OpenAI 兼容的请求/响应模型
  - ChatCompletionRequest/Response
  - Message, Choice, Usage
  - GenerateTokenRequest/Response
  - TokenListResponse/TokenInfo
  - OpenAiError

#### 1.3 Token 管理系统
- [ ] `TokenManager.kt` - Token CRUD 操作
  - 生成 Token (UUID-based, sk- 前缀)
  - Token 验证 (active + expiry)
  - Token 撤销
  - Token 列表查询
  - 使用统计追踪
  - **存储方案**: SharedPreferences 或 JSON 文件（无需数据库）

---

### 阶段 2: HTTP 服务器 (优先级: 🔴 高)
**预计时间**: 2-3小时

#### 2.1 NanoHTTPD 服务器
- [ ] `ApiService.kt` - NanoHTTPD 服务器实现
  - 端口配置 (8080)
  - 请求路由
  - 错误处理

#### 2.2 Foreground Service
- [ ] `ApiServerService.kt` - Android 前台服务
  - 通知渠道创建
  - 前台服务管理
  - 生命周期控制

#### 2.3 服务器控制器
- [ ] `ApiServerController.kt` - 启动/停止控制

#### 2.4 Android 配置
- [ ] 更新 `AndroidManifest.xml`
  - INTERNET 权限
  - FOREGROUND_SERVICE 权限
  - ApiServerService 声明

---

### 阶段 3: API 路由和处理 (优先级: 🟡 中)
**预计时间**: 2-3小时

#### 3.1 认证中间件
- [ ] `AuthMiddleware.kt` - Bearer Token 验证

#### 3.2 API 路由器
- [ ] `ApiRouter.kt` - 请求路由分发
  - `/v1/chat/completions` POST
  - `/v1/tokens` POST/GET
  - `/v1/tokens/{token}` DELETE
  - `/health` GET

#### 3.3 响应构建器
- [ ] `ResponseBuilder.kt` - 统一响应格式

#### 3.4 请求处理器
- [ ] `ChatCompletionHandler.kt` - 聊天补全端点
- [ ] `TokenHandler.kt` - Token 管理端点

---

### 阶段 4: LiteRT-LM 集成 (优先级: 🟡 中)
**预计时间**: 3-4小时

#### 4.1 推理适配器
- [ ] `LiteRtAdapter.kt` - OpenAI → LiteRT 转换
  - Prompt 构建
  - 推理调用
  - 响应格式化
  - Token 计数

#### 4.2 模型管理
- [ ] 获取现有 LlmModelHelper 实例
- [ ] 支持多模型选择
- [ ] 模型加载/卸载管理

---

### 阶段 5: UI 控制面板 (优先级: 🟢 低)
**预计时间**: 2-3小时

#### 5.1 设置界面
- [ ] `ApiServiceControlPanel.kt` - API 服务控制面板
  - 服务启停按钮
  - 服务器地址显示
  - Token 管理入口
  - 端口配置

#### 5.2 Token 管理 UI
- [ ] Token 列表展示
- [ ] 新建 Token 对话框
- [ ] Token 详情和撤销

---

### 阶段 6: 增强功能 (优先级: 🟢 低)
**预计时间**: 4-6小时

#### 6.1 高级特性
- [ ] 端口自动回退 (8080-8099)
- [ ] 流式响应 (SSE)
- [ ] 速率限制
- [ ] 请求日志

#### 6.2 安全加固
- [ ] Token 加密存储 (EncryptedSharedPreferences)
- [ ] HTTPS 支持 (可选)
- [ ] IP 白名单

---

## 💾 数据存储方案

### 推荐方案：SharedPreferences + JSON

**优点**：
- ✅ 轻量级，无需额外依赖
- ✅ Android 原生支持
- ✅ 适合小规模数据（Token 数量通常 < 100）
- ✅ 简单易用，易于调试

**实现方式**：
```kotlin
// 方案 1: SharedPreferences (推荐用于少量 Token)
class TokenManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("api_tokens", Context.MODE_PRIVATE)
    
    fun saveToken(token: ApiToken) {
        val json = Gson().toJson(token)
        prefs.edit().putString(token.token, json).apply()
    }
    
    fun getToken(tokenValue: String): ApiToken? {
        val json = prefs.getString(tokenValue, null) ?: return null
        return Gson().fromJson(json, ApiToken::class.java)
    }
}

// 方案 2: JSON 文件 (适合需要备份/导出的场景)
class TokenFileStorage(private val context: Context) {
    private val tokensFile = File(context.filesDir, "api_tokens.json")
    
    fun saveTokens(tokens: List<ApiToken>) {
        val json = Gson().toJson(tokens)
        tokensFile.writeText(json)
    }
    
    fun loadTokens(): List<ApiToken> {
        if (!tokensFile.exists()) return emptyList()
        val json = tokensFile.readText()
        return Gson().fromJson(json, Array<ApiToken>::class.java).toList()
    }
}
```

**为什么不使用 Room Database**：
- ❌ 过度设计 - Token 数据结构简单，不需要复杂查询
- ❌ 增加复杂度 - 需要定义 Entity、DAO、Database
- ❌ 性能开销 - 对于少量数据，SharedPreferences 更快
- ❌ 维护成本 - 数据库迁移、版本管理等

**何时考虑升级**：
- Token 数量 > 1000
- 需要复杂查询（如按日期范围筛选）
- 需要关系型数据（Token 关联用户、权限等）

---

## 📊 当前进度

**总体完成度**: 10% (2/20 核心文件)

| 阶段 | 进度 | 状态 |
|------|------|------|
| 阶段 1: 核心基础架构 | 40% | 🔄 进行中 |
| 阶段 2: HTTP 服务器 | 0% | ⏸️ 待开始 |
| 阶段 3: API 路由和处理 | 0% | ⏸️ 待开始 |
| 阶段 4: LiteRT-LM 集成 | 0% | ⏸️ 待开始 |
| 阶段 5: UI 控制面板 | 0% | ⏸️ 待开始 |
| 阶段 6: 增强功能 | 0% | ⏸️ 待开始 |

### ✅ 已完成文件
1. `api/model/ApiToken.kt` - Token 数据模型
2. `api/TokenManager.kt` - Token 管理器（SharedPreferences 存储）

---

## 🚀 快速开始建议

### 立即可做的 (今天)
1. ✅ 已完成 `ApiToken.kt`
2. 创建 `OpenAiModels.kt` - 定义 API 数据结构
3. 添加 NanoHTTPD 依赖

### 明天可以做的
4. 实现 `TokenManager.kt` - Token 管理核心
5. 创建 `ApiService.kt` - 基础 HTTP 服务器
6. 配置 `AndroidManifest.xml`

### 本周目标
7. 完成阶段 1-2 (基础架构 + HTTP 服务器)
8. 能够启动服务器并响应 `/health` 端点
9. 实现基本的 Token 生成和验证

---

## 📝 技术要点

### 关键依赖
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 核心接口
```kotlin
// Token 管理器
interface TokenManager {
    suspend fun generateToken(name: String, expiresInDays: Int?): ApiToken
    suspend fun validateToken(token: String): Boolean
    suspend fun revokeToken(token: String): Boolean
    suspend fun listTokens(): List<ApiToken>
}

// 推理适配器
interface InferenceAdapter {
    suspend fun generateCompletion(request: ChatCompletionRequest): ChatCompletionResponse
}
```

### API 端点
```
POST   /v1/chat/completions    # 聊天补全 (OpenAI 兼容)
POST   /v1/tokens              # 生成新 Token
GET    /v1/tokens              # 列出所有 Token
DELETE /v1/tokens/{token}      # 撤销 Token
GET    /health                 # 健康检查
```

---

## ⚠️ 注意事项

1. **LiteRT-LM 集成是关键难点**
   - 需要找到 Gallery 中现有的 LlmModelHelper
   - 确保线程安全（推理可能是阻塞操作）
   - 处理模型加载/卸载

2. **Foreground Service 保活**
   - Android 系统可能杀死后台服务
   - 需要通知和用户授权
   - 考虑电池优化影响

3. **内存管理**
   - LLM 模型占用大量内存
   - 监控 OOM 风险
   - 实现懒加载和缓存清理

4. **安全性**
   - Token 必须加密存储
   - 默认仅监听 localhost
   - 远程访问需明确警告

---

## 📚 参考文档

- [Gallery_API_Token_服务改造方案.md](Gallery_API_Token_服务改造方案.md) - 完整设计方案
- [COMPLETION_REPORT.md](COMPLETION_REPORT.md) - 之前的实施报告（参考）
- [API_QUICK_REFERENCE.md](API_QUICK_REFERENCE.md) - API 使用指南

---

**最后更新**: 2026-05-15  
**下一步**: 创建 `OpenAiModels.kt` 和添加 NanoHTTPD 依赖
