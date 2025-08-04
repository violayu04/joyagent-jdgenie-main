#!/bin/bash

echo "=== 快速编译测试 ==="

cd "$(dirname "$0")"

# 尝试找到Maven
MAVEN_CMD=""
if command -v mvn &> /dev/null; then
    MAVEN_CMD="mvn"
elif [ -f "/opt/homebrew/bin/mvn" ]; then
    MAVEN_CMD="/opt/homebrew/bin/mvn"
elif [ -f "/usr/local/bin/mvn" ]; then
    MAVEN_CMD="/usr/local/bin/mvn"
else
    echo "❌ 未找到Maven"
    exit 1
fi

echo "使用Maven: $MAVEN_CMD"

# 只编译，不打包，快速检查语法错误
echo "编译检查中..."
$MAVEN_CMD compile -q

if [ $? -eq 0 ]; then
    echo "✅ 编译成功! 所有语法错误已修复"
    echo "现在可以运行完整构建: ./build-fixed.sh"
else
    echo "❌ 仍有编译错误，请检查错误信息"
fi
