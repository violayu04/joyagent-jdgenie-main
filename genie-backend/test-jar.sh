#!/bin/bash

echo "=== 直接测试JAR文件 ==="

cd "$(dirname "$0")"

JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR文件不存在，请先构建项目"
    exit 1
fi

echo "🧪 测试JAR文件是否可执行..."
echo "JAR文件: $JAR_FILE"
echo "文件大小: $(ls -lh $JAR_FILE | awk '{print $5}')"

# 检查JAR文件内容
echo ""
echo "📦 检查JAR文件结构:"
echo "Main-Class: $(jar -xf $JAR_FILE META-INF/MANIFEST.MF >/dev/null 2>&1 && grep "Main-Class" META-INF/MANIFEST.MF || echo "未找到")"
echo "Start-Class: $(grep "Start-Class" META-INF/MANIFEST.MF 2>/dev/null || echo "未找到")"

# 清理临时文件
rm -rf META-INF 2>/dev/null

echo ""
echo "🚀 尝试启动应用 (前台运行，Ctrl+C停止):"
echo ""

# 直接运行JAR文件
java -jar "$JAR_FILE"
