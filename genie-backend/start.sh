#!/bin/bash

echo "=== JoyAgent 后端服务启动 ==="

# 进入脚本所在目录
cd "$(dirname "$0")"

# 找到Maven
MAVEN_CMD=""
if command -v mvn &> /dev/null; then
    MAVEN_CMD="mvn"
elif [ -f "/opt/homebrew/bin/mvn" ]; then
    MAVEN_CMD="/opt/homebrew/bin/mvn"
elif [ -f "/usr/local/bin/mvn" ]; then
    MAVEN_CMD="/usr/local/bin/mvn"
else
    echo "❌ 未找到Maven，请先安装Maven"
    exit 1
fi

# 检查JAR文件是否存在
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "⚠️  JAR文件不存在，开始构建..."
    if $MAVEN_CMD clean package -DskipTests; then
        echo "✅ 构建成功"
    else
        echo "❌ 构建失败"
        exit 1
    fi
fi

# 设置日志文件
LOGFILE="./genie-backend_startup.log"

# 启动应用（后台运行）
echo "🚀 启动后端服务..."
java -jar "$JAR_FILE" \
    -Dfile.encoding=UTF-8 \
    --server.port=8080 \
    > $LOGFILE 2>&1 &

# 获取进程ID
PID=$!
echo "✅ 后端服务已启动 (PID: $PID)"
echo "📝 日志文件: $LOGFILE"
echo "🌐 访问地址: http://localhost:8080"

# 将PID写入文件供其他脚本使用
echo $PID > genie-backend.pid
