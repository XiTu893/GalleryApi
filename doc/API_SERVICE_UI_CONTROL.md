# API 服务 UI 控制功能

## 概述

在 Gallery App 的设置对话框中添加了 API 服务控制面板，允许用户：
1. 查看 API 服务器地址
2. 一键复制服务器地址
3. 切换 API Key 认证开关

## 实现文件

### 新增文件
- `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/ApiServiceControlPanel.kt`
  - API 服务控制面板 UI 组件
  - 显示服务器地址（http://127.0.0.1:8080）
  - 提供复制按钮
  - 提供认证开关（Require API Key）
  - 当认证禁用时显示警告信息

### 修改文件
- `gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/SettingsDialog.kt`
  - 导入 `ApiConfig` 类
  - 创建 `apiConfig` 实例
  - 在设置对话框中添加 `ApiServiceControlPanel` 组件

## UI 布局

设置对话框中的顺序：
1. Theme（主题选择）
2. HuggingFace access token（HF Token 管理）
3. **API Service** ⭐ NEW
   - Server Address: http://127.0.0.1:8080 [复制按钮]
   - Require API Key: [开关]
   - 说明文字（根据开关状态变化）
   - 警告信息（仅当认证禁用时显示）
4. Third-party libraries（第三方库）
5. Terms of Service（服务条款）

## 功能特性

### 1. 服务器地址显示
- 默认显示：`http://127.0.0.1:8080`
- 使用主色调突出显示
- 便于用户快速识别 API 端点

### 2. 一键复制
- 点击复制图标按钮
- 自动将地址复制到剪贴板
- 方便用户在外部应用中使用

### 3. 认证开关
- **启用状态**（默认）：
  - 标签："Clients must provide a valid token"
  - 行为：所有 API 请求需要 Bearer Token
  
- **禁用状态**：
  - 标签："No authentication required (like Ollama)"
  - 行为：允许无认证访问 `/v1/chat/completions`
  - 显示红色警告："⚠️ Warning: Anyone on your network can access the API without authentication"

### 4. 持久化存储
- 使用 `SharedPreferences` 保存配置
- 应用重启后保持设置
- 与 API 层的 `ApiConfig` 完全同步

## 用户体验

### 场景 1：开发者本地测试
1. 打开设置 → 查看 API Service
2. 关闭 "Require API Key" 开关
3. 复制服务器地址
4. 在 Python/JS 代码中直接使用，无需 Token

```python
import requests

# 无需 Token
response = requests.post(
    "http://127.0.0.1:8080/v1/chat/completions",
    json={"model": "gemma", "messages": [{"role": "user", "content": "Hello"}]}
)
```

### 场景 2：生产环境部署
1. 打开设置 → 查看 API Service
2. 确保 "Require API Key" 已启用
3. 生成 Token 并分发给客户端
4. 客户端必须携带 Token 访问

```bash
curl -H "Authorization: Bearer sk-xxx" http://127.0.0.1:8080/v1/chat/completions
```

## 安全考虑

✅ **默认安全**：认证默认启用  
✅ **明确警告**：禁用时显示醒目警告  
✅ **最小权限**：Token 管理端点始终需要认证  
⚠️ **网络限制**：建议仅在受信任的本地网络中禁用认证  

## 未来增强

可能的改进方向：
1. 检测并显示实际 LAN IP 地址（如 192.168.1.100:8080）
2. 添加启动/停止 API 服务器的开关
3. 显示当前活跃连接数
4. 显示 Token 使用统计
5. 支持自定义端口号

## 技术细节

### 状态管理
```kotlin
val apiConfig = remember { ApiConfig(LocalContext.current) }
var isAuthEnabled by remember { mutableStateOf(apiConfig.isAuthEnabled) }
```

### 数据流
```
UI Switch → apiConfig.isAuthEnabled = enabled 
          → SharedPreferences 更新
          → ApiService 读取最新配置
          → 后续请求按新配置验证
```

### Composable 结构
```kotlin
@Composable
fun ApiServiceControlPanel(apiConfig: ApiConfig, modifier: Modifier = Modifier) {
  Card {
    Column {
      Text("API Service")
      Row { Server Address + Copy Button }
      Row { Require API Key Label + Switch }
      if (!isAuthEnabled) Warning Text
    }
  }
}
```

## 测试建议

### 手动测试
1. 打开 App → 设置 → 查看 API Service 面板
2. 点击复制按钮 → 粘贴到文本编辑器验证
3. 切换开关 → 观察文字变化和警告显示
4. 关闭设置 → 重新打开 → 验证配置保持

### API 测试
```bash
# 1. 通过 UI 禁用认证
# 2. 测试无需 Token 的请求
curl http://127.0.0.1:8080/v1/chat/completions \
  -d '{"model":"test","messages":[{"role":"user","content":"Hi"}]}'

# 3. 通过 UI 启用认证
# 4. 测试需要 Token 的请求
curl -H "Authorization: Bearer sk-test" http://127.0.0.1:8080/v1/chat/completions \
  -d '{"model":"test","messages":[{"role":"user","content":"Hi"}]}'
```

## 相关文件

- UI 组件：`ApiServiceControlPanel.kt`
- 设置对话框：`SettingsDialog.kt`
- 配置管理：`ApiConfig.kt`
- API 服务：`ApiService.kt`
- 文档：`doc/API_SERVICE_UI_CONTROL.md`（本文件）
