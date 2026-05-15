# API 服务端口回退和启停控制 - 功能更新总结

## 📋 更新概览

**版本**: v0.3.0-beta  
**日期**: 2026-05-15  
**状态**: ✅ 已完成

---

## ✨ 新增功能

### 1. 端口自动回退机制

**问题**: 如果 8080 端口被占用，API 服务无法启动

**解决方案**: 
- 自动尝试 8080-8099 范围内的下一个可用端口
- 成功后广播实际使用的端口号
- UI 显示实际端口并提示用户

**示例**:
```
请求端口: 8080 (被占用)
→ 尝试 8081 (被占用)
→ 尝试 8082 (可用) ✓
→ 服务在 8082 启动
→ UI 显示: "Using port 8082 (requested 8080 was unavailable)"
```

### 2. 可配置端口号

**功能**:
- 用户可在设置中自定义端口（8080-8099）
- 配置持久化保存
- 服务停止时可修改

**UI 位置**: Settings → API Service → Server Port

### 3. 服务启停控制

**功能**:
- Start/Stop 按钮控制服务
- 实时显示服务状态（Running/Stopped）
- 状态通过广播同步到 UI

**UI 位置**: Settings → API Service → Server Status

### 4. 实时状态同步

**技术实现**:
- Service 启动后发送广播
- UI 组件监听广播更新状态
- 确保显示信息准确

---

## 🔧 技术实现

### 修改的文件

1. **ApiConfig.kt**
   - 添加 `serverPort` 配置项
   - 添加端口范围验证
   - 默认值: 8080, 范围: 8080-8099

2. **ApiService.kt**
   - 支持构造函数传入端口号
   - 定义端口常量 (DEFAULT_PORT, MIN_PORT, MAX_PORT)

3. **ApiServerService.kt**
   - 实现 `startServerWithFallback()` 方法
   - 端口占用时自动重试
   - 广播实际使用端口
   - 通知显示实际端口

4. **ApiServerController.kt**
   - 添加 `checkServiceRunning()` 方法
   - 通过 ActivityManager 检查服务状态

5. **ApiServiceControlPanel.kt**
   - 添加服务启停按钮
   - 添加端口配置输入框
   - 添加广播接收器
   - 显示实际端口提示
   - 实时更新服务器地址

### 核心算法

```kotlin
// 端口回退逻辑
private fun startServerWithFallback(startPort: Int): Int {
    var currentPort = startPort
    
    while (currentPort <= MAX_PORT) {
        try {
            apiService = ApiService(context, currentPort).apply {
                start()
            }
            return currentPort  // 成功
        } catch (e: Exception) {
            currentPort++  // 失败，尝试下一个
        }
    }
    
    throw RuntimeException("所有端口都被占用")
}
```

---

## 🎯 用户体验改进

### Before (v0.2.0)
- ❌ 端口被占用时服务启动失败
- ❌ 无 UI 控制，需代码启动
- ❌ 固定端口 8080，无法自定义
- ❌ 无状态反馈

### After (v0.3.0)
- ✅ 端口被占用时自动回退
- ✅ UI 一键启停服务
- ✅ 可自定义端口号
- ✅ 实时状态显示
- ✅ 智能提示信息

---

## 📊 测试建议

### 快速测试步骤

1. **正常启动**
   ```
   打开 App → Settings → API Service → Start
   预期: 服务在 8080 启动，显示 Running
   ```

2. **端口占用测试**
   ```
   占用 8080 端口 → 启动服务
   预期: 自动使用 8081 或其他可用端口
   UI 显示: "Using port 8081 (requested 8080 was unavailable)"
   ```

3. **自定义端口**
   ```
   Stop 服务 → 修改 Port 为 8090 → Start
   预期: 服务在 8090 启动（或被占用则回退）
   ```

4. **状态同步**
   ```
   启动服务 → 关闭设置 → 重新打开
   预期: 正确显示 Running 状态和实际端口
   ```

### 使用测试脚本

**PowerShell** (Windows):
```powershell
.\doc\test_port_fallback.ps1
```

**Bash** (Linux/Mac):
```bash
chmod +x doc/test_port_fallback.sh
./doc/test_port_fallback.sh
```

---

## 📝 API 使用示例

```bash
# 假设服务运行在 8082 端口

# 1. 健康检查
curl http://127.0.0.1:8082/health

# 2. 生成 Token
curl -X POST http://127.0.0.1:8082/v1/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "my-app", "expires_in_days": 30}'

# 3. 聊天补全
curl -X POST http://127.0.0.1:8082/v1/chat/completions \
  -H "Authorization: Bearer sk-your-token" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-e2b-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

---

## 🔍 日志查看

```bash
# 查看 API 服务日志
adb logcat | grep -E "(ApiServerService|ApiService|ApiConfig)"

# 关键日志示例
# I/ApiServerService: Attempting to start API server on port 8080
# W/ApiServerService: Port 8080 is in use: Address already in use
# D/ApiServerService: Trying port 8081...
# I/ApiServerService: Successfully bound to port 8081
# D/ApiServerService: Broadcasted port update: 8081
```

---

## ⚠️ 注意事项

1. **端口修改限制**
   - 仅在服务停止时可修改端口
   - 服务运行时输入框禁用

2. **端口范围**
   - 最小: 8080
   - 最大: 8099
   - 超出范围会自动调整到边界值

3. **服务持久性**
   - 服务作为前台服务运行
   - 即使应用切换到后台仍会继续
   - 系统极端情况下可能终止服务

4. **网络访问**
   - 默认仅监听 localhost (127.0.0.1)
   - 外部设备无法访问
   - 如需局域网访问需额外配置

---

## 📚 相关文档

- [详细功能文档](API_SERVICE_PORT_FALLBACK.md)
- [完整实施报告](COMPLETION_REPORT.md)
- [快速开始指南](QUICK_START.md)

---

## 🎉 总结

本次更新显著提升了 API 服务的可用性和用户体验：

✅ **可靠性提升**: 端口冲突不再导致服务失败  
✅ **易用性提升**: 直观的 UI 控制和状态显示  
✅ **灵活性提升**: 可自定义端口适应不同场景  
✅ **透明度提升**: 实时显示实际使用的端口  

下一步计划集成真实的 LiteRT-LM 推理引擎，实现完整的端到端功能。
