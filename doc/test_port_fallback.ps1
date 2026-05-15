# API 服务端口回退功能测试脚本 (PowerShell)
# 用于验证端口占用时的自动回退机制

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "API 服务端口回退功能测试" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# 检查 adb 是否可用
try {
    $adbVersion = & adb version 2>&1
    Write-Host "✓ ADB 可用: $($adbVersion[0])" -ForegroundColor Green
} catch {
    Write-Host "错误: 未找到 adb 命令，请确保 Android SDK 已正确配置" -ForegroundColor Red
    exit 1
}

# 检查设备连接
$devices = & adb devices | Select-String "device$"
$deviceCount = $devices.Count

if ($deviceCount -eq 0) {
    Write-Host "错误: 未检测到连接的 Android 设备" -ForegroundColor Red
    exit 1
}

Write-Host "✓ 检测到 $deviceCount 个设备" -ForegroundColor Green
Write-Host ""

# 测试 1: 检查当前服务状态
Write-Host "测试 1: 检查 API 服务状态" -ForegroundColor Yellow
Write-Host "--------------------------------------" -ForegroundColor Gray
$serviceProcess = & adb shell ps | Select-String "ApiServerService"
if ($serviceProcess) {
    Write-Host "✓ API 服务正在运行" -ForegroundColor Green
} else {
    Write-Host "○ API 服务未运行" -ForegroundColor Yellow
}
Write-Host ""

# 测试 2: 检查端口占用情况
Write-Host "测试 2: 检查端口占用情况 (8080-8085)" -ForegroundColor Yellow
Write-Host "--------------------------------------" -ForegroundColor Gray
for ($port = 8080; $port -le 8085; $port++) {
    $result = & adb shell netstat -tuln 2>$null | Select-String ":$port "
    if ($result) {
        Write-Host "  端口 $port`: 被占用" -ForegroundColor Red
    } else {
        Write-Host "  端口 $port`: 可用" -ForegroundColor Green
    }
}
Write-Host ""

# 测试 3: 健康检查
Write-Host "测试 3: API 健康检查" -ForegroundColor Yellow
Write-Host "--------------------------------------" -ForegroundColor Gray
$foundPort = $false
for ($port = 8080; $port -le 8085; $port++) {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/health" -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            Write-Host "✓ 端口 $port`: API 服务正常响应" -ForegroundColor Green
            Write-Host "  地址: http://127.0.0.1:$port/health" -ForegroundColor Cyan
            
            # 获取详细信息
            $healthInfo = $response.Content
            Write-Host "  响应: $healthInfo" -ForegroundColor Gray
            $foundPort = $true
            break
        }
    } catch {
        # 端口不可用，继续尝试下一个
    }
}

if (-not $foundPort) {
    Write-Host "○ 在 8080-8085 范围内未找到运行的 API 服务" -ForegroundColor Yellow
}
Write-Host ""

# 测试 4: 查看日志
Write-Host "测试 4: 查看最近的 API 服务日志" -ForegroundColor Yellow
Write-Host "--------------------------------------" -ForegroundColor Gray
$logOutput = & adb logcat -d 2>$null | Select-String "(ApiServerService|ApiService)" | Select-Object -Last 20
if ($logOutput) {
    $logOutput | ForEach-Object { Write-Host $_ }
} else {
    Write-Host "○ 未找到相关日志" -ForegroundColor Yellow
}
Write-Host ""

# 测试 5: 模拟端口占用（需要 root 权限）
Write-Host "测试 5: 模拟端口占用场景" -ForegroundColor Yellow
Write-Host "--------------------------------------" -ForegroundColor Gray
Write-Host "注意: 此测试需要 root 权限" -ForegroundColor Red
Write-Host ""
Write-Host "手动测试步骤:" -ForegroundColor Cyan
Write-Host "1. 在设备上启动一个占用 8080 端口的服务" -ForegroundColor White
Write-Host "   例如: adb shell python3 -m http.server 8080" -ForegroundColor Gray
Write-Host ""
Write-Host "2. 通过 UI 启动 API 服务" -ForegroundColor White
Write-Host ""
Write-Host "3. 验证服务是否自动使用 8081 或其他可用端口" -ForegroundColor White
Write-Host ""
Write-Host "4. 检查通知和 UI 显示的端口号是否正确" -ForegroundColor White
Write-Host ""

# 清理建议
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "清理和维护" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "停止 API 服务:" -ForegroundColor Yellow
Write-Host "  adb shell am stopservice com.google.ai.edge.gallery/.api.ApiServerService" -ForegroundColor Gray
Write-Host ""
Write-Host "清除应用数据:" -ForegroundColor Yellow
Write-Host "  adb shell pm clear com.google.ai.edge.gallery" -ForegroundColor Gray
Write-Host ""
Write-Host "查看完整日志:" -ForegroundColor Yellow
Write-Host '  adb logcat | Select-String "(ApiServerService|ApiService|ApiConfig)"' -ForegroundColor Gray
Write-Host ""

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "测试完成" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
