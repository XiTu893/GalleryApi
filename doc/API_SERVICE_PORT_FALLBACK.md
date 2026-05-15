# API 服务端口自动回退和启停控制功能

## 概述

本次更新为 Gallery API 服务添加了以下功能：

1. **端口自动回退**：如果配置的端口被占用，自动尝试下一个可用端口（范围：8080-8099）
2. **可配置端口**：用户可以在设置中自定义服务器端口
3. **实时状态显示**：UI 显示实际使用的端口和服务运行状态
4. **服务启停控制**：通过 UI 按钮启动/停止 API 服务

## 实现细节

### 1. 配置层 (ApiConfig.kt)

添加了端口配置支持：

```kotlin
var serverPort: Int  // 可配置的端口号（8080-8099）
fun getPortRange(): Pair<Int, Int>  // 获取有效端口范围
```

**特性：**
- 默认端口：8080
- 端口范围：8080-8099（共 20 个端口）
- 自动验证：超出范围的端口会被限制到边界值
- 持久化存储：使用 SharedPreferences 保存配置

### 2. 服务层 (ApiServerService.kt)

实现了端口自动回退机制：

```kotlin
private fun startServerWithFallback(startPort: Int): Int {
    var currentPort = startPort
    
    while (currentPort <= ApiService.MAX_PORT) {
        try {
            apiService = ApiService(applicationContext, currentPort).apply {
                start()
            }
            return currentPort  // 成功绑定，返回实际端口
        } catch (e: Exception) {
            currentPort++  // 端口被占用，尝试下一个
        }
    }
    
    throw RuntimeException("所有端口都被占用")
}
```

**工作流程：**
1. 读取配置的端口号
2. 尝试绑定该端口
3. 如果失败，依次尝试下一个端口（8081, 8082, ...）
4. 成功后广播实际使用的端口号
5. 更新通知显示实际端口

**广播机制：**
```kotlin
ACTION_PORT_UPDATED = "com.google.ai.edge.gallery.api.PORT_UPDATED"
EXTRA_PORT = "extra_port"
```

当服务启动时，会发送广播通知 UI 组件实际使用的端口。

### 3. UI 层 (ApiServiceControlPanel.kt)

增强了控制面板功能：

#### 新增功能：

**a) 服务启停按钮**
- 显示当前状态（Running/Stopped）
- Start 按钮：启动服务
- Stop 按钮：停止服务
- 实时状态同步

**b) 端口配置输入框**
- 显示当前配置的端口
- 允许用户修改端口号
- 仅在服务停止时可编辑
- 自动保存到配置

**c) 实际端口提示**
- 如果实际端口与配置不同，显示提示信息
- 例如："ℹ️ Using port 8082 (requested 8080 was unavailable)"

**d) 广播接收器**
```kotlin
DisposableEffect(Unit) {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ApiServerService.ACTION_PORT_UPDATED) {
                actualPort = intent.getIntExtra(EXTRA_PORT, 0)
                isServerRunning = actualPort > 0
            }
        }
    }
    // 注册接收器...
}
```

## 使用场景

### 场景 1：正常启动

1. 打开设置 → API Service
2. 点击 "Start" 按钮
3. 服务在端口 8080 启动
4. 显示地址：`http://127.0.0.1:8080`

### 场景 2：端口被占用

1. 其他应用占用了 8080 端口
2. 用户点击 "Start"
3. 系统自动尝试 8081、8082...
4. 假设 8082 可用，服务在该端口启动
5. UI 显示：
   - Server Address: `http://127.0.0.1:8082`
   - 提示信息：`ℹ️ Using port 8082 (requested 8080 was unavailable)`

### 场景 3：自定义端口

1. 停止服务（如果正在运行）
2. 在 "Server Port" 输入框中输入新端口（如 8090）
3. 点击 "Start"
4. 服务尝试在 8090 启动
5. 如果 8090 被占用，自动回退到 8091、8092...

### 场景 4：所有端口都被占用

1. 8080-8099 全部被占用
2. 启动失败
3. 通知显示错误信息
4. 日志记录详细错误

## API 端点示例

```bash
# 健康检查
curl http://127.0.0.1:8082/health

# 生成 Token（假设使用 8082 端口）
curl -X POST http://127.0.0.1:8082/v1/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "test", "expires_in_days": 30}'

# 聊天补全
curl -X POST http://127.0.0.1:8082/v1/chat/completions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

## 技术要点

### 1. 端口冲突检测

NanoHTTPD 在端口被占用时会抛出异常：
```
java.net.BindException: Address already in use
```

我们捕获这个异常并尝试下一个端口。

### 2. 广播通信

Service → UI 的通信使用 LocalBroadcast：
- Service 启动后发送实际端口
- UI 接收广播并更新显示
- 确保 UI 始终显示准确信息

### 3. 状态管理

UI 使用 Compose 的状态管理：
```kotlin
var isServerRunning by remember { mutableStateOf(false) }
var actualPort by remember { mutableStateOf(0) }
var serverPort by remember { mutableStateOf(apiConfig.serverPort) }
```

### 4. 生命周期处理

使用 `DisposableEffect` 管理广播接收器：
- 组件创建时注册接收器
- 组件销毁时取消注册
- 防止内存泄漏

## 安全性考虑

### 1. 端口范围限制

限制端口范围为 8080-8099：
- 避免使用特权端口（< 1024）
- 避免使用过高端口（可能与其他服务冲突）
- 便于防火墙规则配置

### 2. 本地监听

服务仅监听 `127.0.0.1`（localhost）：
- 外部设备无法访问
- 仅本机应用可以调用 API
- 如需局域网访问，需额外配置

### 3. 认证保护

默认启用 Token 认证：
- 防止未授权访问
- 用户可选择禁用（不推荐）

## 故障排查

### 问题 1：服务无法启动

**症状：** 点击 Start 后无响应或显示错误

**检查步骤：**
1. 查看 Logcat 日志：
   ```
   adb logcat | grep ApiServerService
   ```
2. 确认 8080-8099 范围内至少有一个端口可用
3. 检查是否有其他应用占用了所有端口

**解决方案：**
- 释放被占用的端口
- 或更改端口配置到其他范围（需修改代码）

### 问题 2：UI 状态不同步

**症状：** 服务已启动但 UI 显示 Stopped

**检查步骤：**
1. 确认广播接收器已正确注册
2. 检查 AndroidManifest 中的 Service 声明
3. 查看 Logcat 中的广播日志

**解决方案：**
- 重新打开设置对话框
- 重启应用

### 问题 3：端口配置不生效

**症状：** 修改端口后仍使用旧端口

**原因：** 服务运行时无法修改端口

**解决方案：**
1. 先停止服务
2. 修改端口配置
3. 重新启动服务

## 未来改进方向

1. **动态端口范围**：允许用户在设置中自定义端口范围
2. **LAN IP 检测**：自动检测并显示局域网 IP，方便其他设备访问
3. **端口可用性预检**：启动前快速扫描可用端口
4. **HTTPS 支持**：添加 TLS/SSL 加密
5. **连接数监控**：显示当前活跃连接数
6. **带宽统计**：显示流量使用情况

## 相关文件

- 配置管理：`api/ApiConfig.kt`
- HTTP 服务器：`api/ApiService.kt`
- 前台服务：`api/ApiServerService.kt`
- 控制器：`api/ApiServerController.kt`
- UI 组件：`ui/home/ApiServiceControlPanel.kt`
- 清单文件：`AndroidManifest.xml`

## 测试建议

### 单元测试

1. 测试端口配置的有效性验证
2. 测试端口回退逻辑
3. 测试广播消息的发送和接收

### 集成测试

1. 模拟端口占用场景
2. 测试服务启停流程
3. 验证 UI 状态同步

### 手动测试

1. 正常启动服务
2. 占用 8080 端口后启动
3. 修改端口配置并重启
4. 验证复制地址功能
5. 测试认证开关

## 版本历史

- **v0.3.0** (2026-05-15)
  - 添加端口自动回退功能
  - 添加端口配置 UI
  - 添加服务启停控制
  - 实现实时状态同步
