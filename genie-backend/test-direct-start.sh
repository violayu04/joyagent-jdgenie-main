#!/bin/bash

echo "=== 直接前台启动测试 ==="

cd "$(dirname "$0")"

JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR文件不存在: $JAR_FILE"
    echo "请先运行: mvn clean package -DskipTests"
    exit 1
fi

echo "✅ 找到JAR文件: $JAR_FILE"
echo "📋 文件信息: $(ls -lh $JAR_FILE)"
echo ""

echo "🚀 直接启动JAR文件 (前台模式):"
echo "按 Ctrl+C 停止"
echo "=================================="

# 直接在前台启动，这样可以看到所有错误信息
java -jar "$JAR_FILE" --server.port=8080
