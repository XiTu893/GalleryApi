# API 服务重构至 UseCase 层计划

## 一、架构评估：API 服务放到 UseCase 是否更合适？

**结论：是的，非常合适，且应该继续完成已开始的重构。**

### 当前问题

| 问题 | 说明 |
|------|------|
| **双重控制路径** | `ApiServiceControlPanel` 直接调用 `ApiServerController` 静态单例，而 `ApiServiceViewModel` 通过 UseCase 调用，两套并行路径未统一 |
| **编译错误** | `GetApiServerStatusUseCase.observeStatus()` 引用了 `ApiServerController.serverStatusFlow`，但该字段不存在 |
| **ApiConfig 双实例** | `ApiServiceControlPanel` 中 `remember { ApiConfig(context) }` 直接创建实例，与 Hilt 单例不一致 |
| **BroadcastReceiver 耦合** | UI 通过广播接收端口变更，与 Android 系统组件耦合，不利于测试 |
| **ViewModel 未被使用** | `ApiServiceViewModel` 已定义但无 Composable 引用 |

### UseCase 架构的优势

1. **单一控制路径**：UI 只通过 ViewModel → UseCase 操作，消除直接调用静态单例
2. **可测试性**：UseCase 可 mock，ViewModel 可单元测试
3. **状态管理统一**：StateFlow 替代 @Volatile + BroadcastReceiver，响应式更新
4. **DI 一致性**：所有依赖通过 Hilt 注入，消除手动实例化
5. **关注点分离**：UI 层只关心展示，业务逻辑在 UseCase 中

### 目标架构

```
ApiServiceControlPanel (Composable)
    │
    ▼ hiltViewModel()
ApiServiceViewModel
    │
    ├── StartApiServerUseCase ────> Context.startForegroundService()
    ├── StopApiServerUseCase ─────> Context.stopService()
    ├── GetApiServerStatusUseCase > ApiServerController.serverStatusFlow (StateFlow)
    ├── UpdateApiConfigUseCase ───> ApiConfig (Hilt 单例)
    └── ManageApiTokensUseCase ──> TokenManager (Hilt 单例)
```

---

## 二、实施步骤

### 步骤 1：为 ApiServerController 添加 StateFlow

**文件**：`api/ApiServerController.kt`

- 将 `@Volatile isRunning` 和 `@Volatile actualPort` 替换为 `MutableStateFlow<ServerStatus>`
- 添加 `serverStatusFlow: StateFlow<ServerStatus>` 公开只读流
- 修改 `updateState()` 方法，更新 StateFlow 而非 volatile 变量
- 保留 `isServerRunning()` 和 `getActualPort()` 作为便捷方法（从 StateFlow 派生）
- 移除 `getServerAddress()` 方法（IP 获取逻辑移至 ViewModel）

```kotlin
object ApiServerController {
    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatusFlow: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    fun isServerRunning(): Boolean = _serverStatus.value.isRunning
    fun getActualPort(): Int = _serverStatus.value.port

    fun updateState(running: Boolean, port: Int) {
        _serverStatus.value = ServerStatus(isRunning = running, port = port)
    }
    // startServer/stopServer 保持不变
}
```

### 步骤 2：更新 GetApiServerStatusUseCase

**文件**：`api/usecase/GetApiServerStatusUseCase.kt`

- `observeStatus()` 现在可以正确引用 `ApiServerController.serverStatusFlow`
- 无需其他修改（步骤 1 修复了编译错误）

### 步骤 3：增强 ApiServiceViewModel

**文件**：`ui/home/ApiServiceViewModel.kt`

- 添加 `localIp` 状态（从 Context 获取设备局域网 IP）
- 添加 `serverAddress` 派生状态（组合 localIp + port）
- 添加 `copyAddress()` 方法（复制到剪贴板）
- 添加 Token 管理相关方法（暴露 `ManageApiTokensUseCase` 功能）

```kotlin
@HiltViewModel
class ApiServiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    // ... 现有 UseCase 注入
) : ViewModel() {

    val localIp: String = getLocalIpAddress(context)

    val serverAddress: StateFlow<String> = serverStatus.map { status ->
        if (status.isRunning && status.port > 0) "http://$localIp:${status.port}" else ""
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun copyAddress() { /* ClipboardManager 复制 */ }
    fun generateToken(name: String) { /* ... */ }
    fun listTokens() { /* ... */ }
    fun deleteToken(tokenValue: String) { /* ... */ }
}
```

### 步骤 4：重构 ApiServiceControlPanel 使用 ViewModel

**文件**：`ui/home/ApiServiceControlPanel.kt`

- 移除所有 `ApiServerController` 直接调用
- 移除 `ApiConfig(context)` 直接实例化
- 移除 `BroadcastReceiver` 注册/注销
- 使用 `hiltViewModel<ApiServiceViewModel>()` 获取 ViewModel
- 从 ViewModel 的 StateFlow 读取状态
- 通过 ViewModel 方法执行操作

关键变更：
```kotlin
@Composable
fun ApiServiceControlPanel(modifier: Modifier = Modifier) {
    val viewModel: ApiServiceViewModel = hiltViewModel()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val apiConfig by viewModel.apiConfig.collectAsState()
    val serverAddress by viewModel.serverAddress.collectAsState()

    // 启动: viewModel.startServer()
    // 停止: viewModel.stopServer()
    // 更新端口: viewModel.updatePort(port)
    // 更新认证: viewModel.updateAuthEnabled(enabled)
    // 复制地址: viewModel.copyAddress()
}
```

### 步骤 5：移除 ApiServerService 中的广播机制

**文件**：`api/ApiServerService.kt`

- 移除 `ACTION_PORT_UPDATED` 和 `EXTRA_PORT` 常量
- 移除 `broadcastPort()` 方法及其调用
- `ApiServerController.updateState()` 更新 StateFlow 后，ViewModel 自动收到通知

### 步骤 6：清理 AppModule

**文件**：`di/AppModule.kt`

- 确认 `TokenManager`、`ApiConfig`、`ModelRegistry`、`LiteRtAdapter` 的 `@Provides` 方法保留
- 无需额外修改（UseCase 通过 `@Inject constructor` 自动由 Hilt 管理）

### 步骤 7：验证与提交

- 本地编译验证（`./gradlew assembleDebug`）
- 提交代码并推送
- 验证 CI 构建通过

---

## 三、变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `api/ApiServerController.kt` | 修改 | @Volatile → StateFlow |
| `api/usecase/GetApiServerStatusUseCase.kt` | 无需修改 | 步骤 1 修复后自动可用 |
| `ui/home/ApiServiceViewModel.kt` | 修改 | 添加 localIp、serverAddress、copyAddress、Token 方法 |
| `ui/home/ApiServiceControlPanel.kt` | 修改 | 移除直接调用，改用 ViewModel |
| `api/ApiServerService.kt` | 修改 | 移除广播机制 |
| `di/AppModule.kt` | 无需修改 | 现有配置已满足 |

---

## 四、风险与注意事项

1. **StateFlow 线程安全**：`MutableStateFlow.value` 的设置是线程安全的，无需额外同步
2. **Service 生命周期**：`ApiServerController.updateState()` 仍由 `ApiServerService` 调用，StateFlow 更新在 Service 主线程，UI 在主线程收集，无跨线程问题
3. **IP 地址缓存**：`localIp` 在 ViewModel 创建时获取一次，如果网络切换（WiFi ↔ 移动数据），可能需要刷新。可在 `serverStatus` 变化时重新获取
4. **向后兼容**：`ApiServerController.isServerRunning()` 和 `getActualPort()` 保留为便捷方法，避免破坏其他可能的调用点
