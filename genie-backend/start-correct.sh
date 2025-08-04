#!/bin/bash

echo "=== JoyAgent 正确的启动脚本 ==="

# 进入后端目录
cd "$(dirname "$0")"

# 检查JAR文件是否存在
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR文件不存在: $JAR_FILE"
    echo "请先运行构建: mvn clean package -DskipTests"
    exit 1
fi

echo "✅ 找到JAR文件: $JAR_FILE"

# 设置日志文件
LOGFILE="./genie-backend_startup.log"

# 清空之前的日志
> $LOGFILE

echo "🚀 启动JoyAgent后端服务..."
echo "📁 日志文件: $LOGFILE"
echo "🌐 访问地址: http://localhost:8080"
echo "📤 原始文件上传API: http://localhost:8080/api/raw-file/upload"
echo "❤️  健康检查: http://localhost:8080/web/health"
echo ""
echo "使用 Ctrl+C 停止服务，或查看日志: tail -f $LOGFILE"
echo "================================================"

# 使用正确的方式启动Spring Boot应用
# 注意：这里用的是 -jar 参数，而不是 -classpath
java -jar "$JAR_FILE" \
    -Dfile.encoding=UTF-8 \
    -Dspring.profiles.active=prod \
    --server.port=8080 \
    > $LOGFILE 2>&1 &

# 获取进程ID
PID=$!
echo "🔢 进程ID: $PID"

# 等待几秒让服务启动
echo "⏳ 等待服务启动..."
sleep 5

# 检查进程是否还在运行
if kill -0 $PID 2>/dev/null; then
    echo "✅ 服务启动成功! PID: $PID"
    echo "📋 查看实时日志: tail -f $LOGFILE"
    echo "🛑 停止服务: kill $PID"
else
    echo "❌ 服务启动失败，请查看日志:"
    cat $LOGFILE
    exit 1
fi
