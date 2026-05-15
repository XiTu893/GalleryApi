# API 服务 - 快速参考

## 🚀 启动服务

1. 打开 Gallery App
2. 进入 Settings (设置)
3. 找到 **API Service** 面板
4. 点击 **Start** 按钮

## 🔧 配置端口

1. 确保服务已停止 (显示 Stopped)
2. 在 **Server Port** 输入框中输入端口号
3. 有效范围: 8080-8099
4. 点击 **Start** 启动服务

## 📊 查看状态

- **Running** (绿色): 服务正在运行
- **Stopped** (红色): 服务已停止
- **Server Address**: 显示实际访问地址
- **端口提示**: 如果使用回退端口会显示提示

## 📋 常用操作

### 复制服务器地址
点击地址旁边的 📋 图标

### 切换认证
- **Require API Key ON**: 需要 Token 访问（推荐）
- **Require API Key OFF**: 无需认证（类似 Ollama）

### 停止服务
点击 **Stop** 按钮

## 🔍 故障排查

### 服务无法启动
```bash
# 查看日志
adb logcat | grep ApiServerService

# 检查端口占用
adb shell netstat -tuln | grep 8080
```

### 端口都被占用
- 尝试修改为其他端口（如 8095）
- 或重启设备释放端口

### UI 状态不同步
- 关闭并重新打开设置对话框
- 或重启应用

## 💻 API 测试

```bash
# 健康检查
curl http://127.0.0.1:8080/health

# 生成 Token
curl -X POST http://127.0.0.1:8080/v1/tokens \
  -d '{"name":"test","expires_in_days":30}'

# 聊天补全
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"model":"gemma","messages":[{"role":"user","content":"Hi"}]}'
```

## 📱 Python 示例

```python
import requests

# 配置
BASE_URL = "http://127.0.0.1:8080"  # 根据实际端口调整

# 生成 Token
response = requests.post(f"{BASE_URL}/v1/tokens", json={
    "name": "python-app",
    "expires_in_days": 7
})
token = response.json()["token"]

# 聊天
response = requests.post(
    f"{BASE_URL}/v1/chat/completions",
    headers={"Authorization": f"Bearer {token}"},
    json={
        "model": "gemma-4-e2b-it",
        "messages": [{"role": "user", "content": "Hello!"}]
    }
)
print(response.json())
```

## ⚙️ 高级配置

### 查看所有日志
```bash
adb logcat -s ApiService:* ApiServerService:* ApiConfig:*
```

### 强制停止服务
```bash
adb shell am stopservice com.google.ai.edge.gallery/.api.ApiServerService
```

### 清除配置
```bash
adb shell pm clear com.google.ai.edge.gallery
```

## 🎯 关键特性

✅ **自动端口回退**: 8080 被占用时自动尝试 8081, 8082...  
✅ **前台服务**: 应用后台运行时服务继续  
✅ **持久化配置**: 端口和认证设置保存  
✅ **实时状态**: UI 同步显示服务状态  
✅ **安全默认**: 默认启用 Token 认证  

## 📞 获取帮助

- 详细文档: `doc/API_SERVICE_PORT_FALLBACK.md`
- 完整报告: `doc/COMPLETION_REPORT.md`
- 测试脚本: `doc/test_port_fallback.ps1` (Windows)

---

**版本**: v0.3.0-beta  
**更新**: 2026-05-15
