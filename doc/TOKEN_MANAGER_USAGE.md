# TokenManager 使用示例

## 📝 简介

`TokenManager` 使用 SharedPreferences 存储 API Token，无需数据库，轻量高效。

---

## 🔧 基本用法

### 1. 初始化

```kotlin
// 在 Activity、Fragment 或 ViewModel 中
val tokenManager = TokenManager(context)
```

### 2. 生成新 Token

```kotlin
// 生成一个30天后过期的 Token
val token = tokenManager.generateToken(
    name = "My Python App",
    expiresInDays = 30
)

println("Token: ${token.token}")
// 输出: Token: sk-a1b2c3d4e5f6g7h8i9j0...

println("Masked: ${token.mask()}")
// 输出: Masked: sk-a1...j0
```

### 3. 验证 Token

```kotlin
val isValid = tokenManager.validateToken("sk-a1b2c3d4...")

if (isValid) {
    println("Token is valid!")
} else {
    println("Token is invalid or expired")
}
```

### 4. 列出所有 Token

```kotlin
val tokens = tokenManager.listTokens()

tokens.forEach { token ->
    println("Name: ${token.name}")
    println("Token: ${token.mask()}")
    println("Created: ${Date(token.createdAt)}")
    println("Expires: ${token.expiresAt?.let { Date(it) } ?: "Never"}")
    println("Usage Count: ${token.usageCount}")
    println("Active: ${token.isActive}")
    println("---")
}
```

### 5. 撤销 Token

```kotlin
val success = tokenManager.revokeToken("sk-a1b2c3d4...")

if (success) {
    println("Token revoked successfully")
} else {
    println("Token not found")
}
```

### 6. 删除 Token

```kotlin
val deleted = tokenManager.deleteToken("sk-a1b2c3d4...")

if (deleted) {
    println("Token deleted permanently")
}
```

### 7. 更新使用统计

```kotlin
// 每次 API 调用后递增使用计数
tokenManager.incrementUsage("sk-a1b2c3d4...")
```

---

## 💡 实际应用场景

### 场景 1: API 认证中间件

```kotlin
class AuthMiddleware(private val tokenManager: TokenManager) {
    
    fun authenticate(headers: Map<String, String>): Boolean {
        // 从 Authorization header 提取 Token
        val authHeader = headers["authorization"] ?: return false
        
        if (!authHeader.startsWith("Bearer ")) {
            return false
        }
        
        val token = authHeader.substringAfter("Bearer ")
        
        // 验证 Token
        return tokenManager.validateToken(token)
    }
}

// 使用
val middleware = AuthMiddleware(tokenManager)
if (middleware.authenticate(request.headers)) {
    // Token 有效，处理请求
} else {
    // 返回 401 Unauthorized
}
```

### 场景 2: Token 管理 UI

```kotlin
@Composable
fun TokenListScreen(tokenManager: TokenManager) {
    val tokens = remember { tokenManager.listTokens() }
    
    LazyColumn {
        items(tokens) { token ->
            Card(modifier = Modifier.padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = token.name, fontWeight = FontWeight.Bold)
                    Text(text = token.mask())
                    Text(text = "Usage: ${token.usageCount} requests")
                    
                    Row {
                        Button(onClick = {
                            tokenManager.revokeToken(token.token)
                        }) {
                            Text("Revoke")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(onClick = {
                            tokenManager.deleteToken(token.token)
                        }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
```

### 场景 3: 生成 Token API 端点

```kotlin
class TokenHandler(private val tokenManager: TokenManager) {
    
    fun handleGenerateToken(body: String): Response {
        // 解析请求体
        val request = Gson().fromJson(body, GenerateTokenRequest::class.java)
        
        // 生成 Token
        val token = tokenManager.generateToken(
            name = request.name,
            expiresInDays = request.expiresInDays
        )
        
        // 返回响应
        val responseJson = Gson().toJson(token)
        return createJsonResponse(responseJson, 201)
    }
}

data class GenerateTokenRequest(
    val name: String,
    val expiresInDays: Int? = 30
)
```

---

## 🔐 安全建议

### 1. Token 显示时始终使用掩码

```kotlin
// ✅ 正确 - 显示掩码
Text(text = token.mask())  // sk-a1...z9

// ❌ 错误 - 显示完整 Token
Text(text = token.token)   // sk-a1b2c3d4e5f6...（不安全！）
```

### 2. 日志中不要记录完整 Token

```kotlin
// ✅ 正确
Log.i(TAG, "Generated token: ${token.mask()}")

// ❌ 错误
Log.i(TAG, "Generated token: ${token.token}")
```

### 3. 定期清理过期 Token

```kotlin
fun cleanupExpiredTokens() {
    val allTokens = tokenManager.listTokens()
    
    allTokens.filter { it.isExpired() }.forEach { token ->
        tokenManager.deleteToken(token.token)
        Log.i(TAG, "Cleaned up expired token: ${token.mask()}")
    }
}
```

---

## 📊 存储说明

### 数据位置
- **SharedPreferences 文件**: `/data/data/com.google.ai.edge.gallery/shared_prefs/api_tokens.xml`
- **格式**: JSON 字符串存储在单个 key 中

### 数据结构
```json
[
  {
    "token": "sk-a1b2c3d4e5f6...",
    "name": "My App",
    "createdAt": 1715750400000,
    "expiresAt": 1718342400000,
    "isActive": true,
    "usageCount": 42
  }
]
```

### 性能特点
- **读取速度**: ~1-5ms（内存缓存）
- **写入速度**: ~5-10ms（异步写入）
- **存储容量**: 适合 < 100 个 Token
- **并发安全**: SharedPreferences 是线程安全的

---

## ⚠️ 注意事项

### 1. Token 数量限制
- 推荐: < 100 个 Token
- 最大: ~1000 个 Token（性能开始下降）
- 超过限制: 考虑迁移到 Room Database

### 2. 备份和恢复
SharedPreferences 会随应用数据一起备份，但如果需要单独备份 Token：

```kotlin
// 导出 Token
fun exportTokens(): String {
    val tokens = tokenManager.listTokens()
    return Gson().toJson(tokens)
}

// 导入 Token
fun importTokens(json: String) {
    val tokens = Gson().fromJson(json, Array<ApiToken>::class.java).toList()
    tokens.forEach { tokenManager.saveToken(it) }
}
```

### 3. 加密存储（可选）

如果需要更高的安全性，可以使用 `EncryptedSharedPreferences`：

```kotlin
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

val encryptedPrefs = EncryptedSharedPreferences.create(
    "api_tokens_encrypted",
    masterKeyAlias,
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

// 然后像普通 SharedPreferences 一样使用
```

---

## 🧪 测试示例

```kotlin
@Test
fun testTokenGeneration() {
    val tokenManager = TokenManager(context)
    
    val token = tokenManager.generateToken("Test App", 30)
    
    assertNotNull(token)
    assertTrue(token.token.startsWith("sk-"))
    assertEquals("Test App", token.name)
    assertTrue(token.isActive)
    assertEquals(0, token.usageCount)
}

@Test
fun testTokenValidation() {
    val tokenManager = TokenManager(context)
    
    val token = tokenManager.generateToken("Test", 30)
    
    assertTrue(tokenManager.validateToken(token.token))
    assertFalse(tokenManager.validateToken("invalid-token"))
}

@Test
fun testTokenRevocation() {
    val tokenManager = TokenManager(context)
    
    val token = tokenManager.generateToken("Test", 30)
    tokenManager.revokeToken(token.token)
    
    assertFalse(tokenManager.validateToken(token.token))
}
```

---

**最后更新**: 2026-05-15  
**版本**: v0.3.0-beta
