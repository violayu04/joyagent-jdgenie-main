#!/bin/bash

echo "=== JoyAgent 简单启动脚本 ==="

# 进入后端目录
cd "$(dirname "$0")"

# 检查JAR文件是否存在
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR文件不存在: $JAR_FILE"
    echo "请先运行构建脚本: ./build-fixed.sh"
    exit 1
fi

echo "✅ 找到JAR文件: $JAR_FILE"
echo "文件大小: $(ls -lh $JAR_FILE | awk '{print $5}')"

echo ""
echo "启动JoyAgent后端服务..."
echo "访问地址: http://localhost:8080"
echo "原始文件上传API: http://localhost:8080/api/raw-file/upload"
echo "健康检查: http://localhost:8080/web/health"
echo ""
echo "使用 Ctrl+C 停止服务"
echo "================================================"

# 启动应用
java -jar "$JAR_FILE"
