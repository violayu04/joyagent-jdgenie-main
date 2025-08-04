#!/bin/bash

echo "=== JoyAgent 修复和构建脚本 ==="

# 进入后端目录
cd "$(dirname "$0")"

# 检查Maven安装位置
MAVEN_CMD=""
if command -v mvn &> /dev/null; then
    MAVEN_CMD="mvn"
    echo "✅ 找到Maven: $(which mvn)"
elif [ -f "/opt/homebrew/bin/mvn" ]; then
    MAVEN_CMD="/opt/homebrew/bin/mvn"
    echo "✅ 找到Homebrew Maven: /opt/homebrew/bin/mvn"
elif [ -f "/usr/local/bin/mvn" ]; then
    MAVEN_CMD="/usr/local/bin/mvn"
    echo "✅ 找到Maven: /usr/local/bin/mvn"
else
    echo "❌ 未找到Maven，请先安装:"
    echo "   brew install maven"
    echo "   或从 https://maven.apache.org/download.cgi 下载安装"
    exit 1
fi

# 检查Java版本
echo "检查Java版本..."
if ! java -version 2>&1 | grep -q "version"; then
    echo "❌ Java未安装，请先安装Java 17:"
    echo "   brew install openjdk@17"
    exit 1
fi

# 显示Maven版本
echo "Maven版本:"
$MAVEN_CMD -version

echo ""

# 清理项目
echo "1. 清理项目..."
$MAVEN_CMD clean

# 编译项目
echo "2. 编译项目..."
if $MAVEN_CMD compile; then
    echo "✅ 编译成功!"
else
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi

# 打包项目
echo "3. 打包项目..."
if $MAVEN_CMD package -DskipTests; then
    echo "✅ 打包成功!"
else
    echo "❌ 打包失败，请检查错误信息"
    exit 1
fi

# 检查生成的JAR文件
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    echo "✅ JAR文件生成成功: $JAR_FILE"
    echo "文件大小: $(ls -lh $JAR_FILE | awk '{print $5}')"
    
    echo ""
    echo "=== 构建完成! ==="
    echo "现在可以启动应用:"
    echo "java -jar $JAR_FILE"
    echo ""
    echo "或者运行: ./start-simple.sh"
else
    echo "❌ JAR文件未生成"
    exit 1
fi
