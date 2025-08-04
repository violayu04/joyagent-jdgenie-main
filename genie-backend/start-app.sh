#!/bin/bash

echo "=== JoyAgent 后端启动脚本 ==="

# 进入后端目录
cd "$(dirname "$0")"

# 检查Java版本
echo "检查Java版本..."
java -version

# 检查Maven是否可用
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven未安装或不在PATH中"
    echo "请安装Maven或检查PATH配置"
    exit 1
fi

# 清理并构建项目
echo "1. 清理并构建项目..."
mvn clean package -DskipTests

# 检查构建是否成功
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ 构建失败，JAR文件不存在: $JAR_FILE"
    echo "请检查Maven构建错误信息"
    exit 1
fi

echo "✅ 构建成功！"

# 启动应用
echo "2. 启动JoyAgent后端服务..."
echo "访问地址: http://localhost:8080"
echo "原始文件上传API: http://localhost:8080/api/raw-file/upload"
echo "健康检查: http://localhost:8080/web/health"
echo ""
echo "启动中... (使用Ctrl+C停止)"

# 使用Spring Boot的方式启动
java -jar "$JAR_FILE" --spring.profiles.active=dev
