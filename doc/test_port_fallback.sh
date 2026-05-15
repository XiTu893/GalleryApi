#!/bin/bash

# API 服务端口回退功能测试脚本
# 用于验证端口占用时的自动回退机制

echo "======================================"
echo "API 服务端口回退功能测试"
echo "======================================"
echo ""

# 检查 adb 是否可用
if ! command -v adb &> /dev/null; then
    echo "错误: 未找到 adb 命令，请确保 Android SDK 已正确配置"
    exit 1
fi

# 检查设备连接
DEVICE_COUNT=$(adb devices | grep -c "device$")
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "错误: 未检测到连接的 Android 设备"
    exit 1
fi

echo "✓ 检测到 $DEVICE_COUNT 个设备"
echo ""

# 测试 1: 检查当前服务状态
echo "测试 1: 检查 API 服务状态"
echo "--------------------------------------"
adb shell ps | grep "ApiServerService"
if [ $? -eq 0 ]; then
    echo "✓ API 服务正在运行"
else
    echo "○ API 服务未运行"
fi
echo ""

# 测试 2: 检查端口占用情况
echo "测试 2: 检查端口占用情况 (8080-8099)"
echo "--------------------------------------"
for port in $(seq 8080 8085); do
    RESULT=$(adb shell netstat -tuln 2>/dev/null | grep ":$port " || echo "")
    if [ -n "$RESULT" ]; then
        echo "  端口 $port: 被占用"
    else
        echo "  端口 $port: 可用"
    fi
done
echo ""

# 测试 3: 健康检查
echo "测试 3: API 健康检查"
echo "--------------------------------------"
for port in $(seq 8080 8085); do
    RESPONSE=$(adb shell curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:$port/health" 2>/dev/null)
    if [ "$RESPONSE" = "200" ]; then
        echo "✓ 端口 $port: API 服务正常响应"
        echo "  地址: http://127.0.0.1:$port/health"
        
        # 获取详细信息
        HEALTH_INFO=$(adb shell curl -s "http://127.0.0.1:$port/health" 2>/dev/null)
        echo "  响应: $HEALTH_INFO"
        break
    fi
done
echo ""

# 测试 4: 查看日志
echo "测试 4: 查看最近的 API 服务日志"
echo "--------------------------------------"
adb logcat -d | grep -E "(ApiServerService|ApiService)" | tail -20
echo ""

# 测试 5: 模拟端口占用（需要 root 权限）
echo "测试 5: 模拟端口占用场景"
echo "--------------------------------------"
echo "注意: 此测试需要 root 权限"
echo ""
echo "手动测试步骤:"
echo "1. 在设备上启动一个占用 8080 端口的服务"
echo "   例如: adb shell python3 -m http.server 8080"
echo ""
echo "2. 通过 UI 启动 API 服务"
echo ""
echo "3. 验证服务是否自动使用 8081 或其他可用端口"
echo ""
echo "4. 检查通知和 UI 显示的端口号是否正确"
echo ""

# 清理建议
echo "======================================"
echo "清理和维护"
echo "======================================"
echo ""
echo "停止 API 服务:"
echo "  adb shell am stopservice com.google.ai.edge.gallery/.api.ApiServerService"
echo ""
echo "清除应用数据:"
echo "  adb shell pm clear com.google.ai.edge.gallery"
echo ""
echo "查看完整日志:"
echo "  adb logcat | grep -E \"(ApiServerService|ApiService|ApiConfig)\""
echo ""

echo "======================================"
echo "测试完成"
echo "======================================"
