#!/bin/bash

echo "=== JoyAgent 后端构建和启动脚本 ==="

# 进入后端目录
cd "$(dirname "$0")"

# 清理之前的构建
echo "1. 清理之前的构建..."
mvn clean

# 编译项目
echo "2. 编译项目..."
mvn compile

# 打包项目
echo "3. 打包项目..."
mvn package -DskipTests

# 检查打包是否成功
if [ ! -f "target/genie-backend-0.0.1-SNAPSHOT.jar" ]; then
    echo "❌ 打包失败，请检查编译错误"
    exit 1
fi

echo "✅ 构建成功！"

# 启动应用
echo "4. 启动应用..."
java -jar target/genie-backend-0.0.1-SNAPSHOT.jar
