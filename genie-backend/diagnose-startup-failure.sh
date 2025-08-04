#!/bin/bash

echo "=== JoyAgent 后端启动失败诊断脚本 ==="

cd "$(dirname "$0")"

# 检查是否在正确目录
if [ ! -f "pom.xml" ]; then
    echo "❌ 请在 genie-backend 目录下运行此脚本"
    exit 1
fi

echo "🔍 开始深度诊断后端启动问题..."
echo ""

# 1. 检查Java环境
echo "1. 检查Java环境:"
echo "--------------------------------"
if command -v java &> /dev/null; then
    echo "✅ Java已安装:"
    java -version
    echo ""
    
    # 检查Java版本是否兼容
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 17 ]; then
        echo "✅ Java版本兼容 (需要>=17)"
    else
        echo "❌ Java版本过低 (当前:$JAVA_VERSION, 需要:>=17)"
        echo "请安装Java 17+: brew install openjdk@17"
    fi
else
    echo "❌ Java未安装"
    echo "请安装Java: brew install openjdk@17"
    exit 1
fi

echo ""

# 2. 检查Maven
echo "2. 检查Maven:"
echo "--------------------------------"
MAVEN_CMD=""
if command -v mvn &> /dev/null; then
    MAVEN_CMD="mvn"
    echo "✅ 系统Maven: $(which mvn)"
elif [ -f "/opt/homebrew/bin/mvn" ]; then
    MAVEN_CMD="/opt/homebrew/bin/mvn"
    echo "✅ Homebrew Maven: /opt/homebrew/bin/mvn"
elif [ -f "/usr/local/bin/mvn" ]; then
    MAVEN_CMD="/usr/local/bin/mvn"
    echo "✅ 本地Maven: /usr/local/bin/mvn"
else
    echo "❌ Maven未找到"
    echo "请安装Maven: brew install maven"
    exit 1
fi

$MAVEN_CMD -version
echo ""

# 3. 检查项目文件
echo "3. 检查项目文件:"
echo "--------------------------------"
echo "POM文件: $([ -f "pom.xml" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo "主类文件: $([ -f "src/main/java/com/jd/genie/GenieApplication.java" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo "配置文件: $([ -f "src/main/resources/application.yml" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo ""

# 4. 尝试编译
echo "4. 尝试编译项目:"
echo "--------------------------------"
echo "运行: $MAVEN_CMD clean compile"
if $MAVEN_CMD clean compile; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
    echo ""
    echo "🔍 常见编译问题:"
    echo "- 检查网络连接(下载依赖)"
    echo "- 检查Java版本兼容性"
    echo "- 清理Maven缓存: rm -rf ~/.m2/repository"
    exit 1
fi

echo ""

# 5. 尝试打包
echo "5. 尝试打包项目:"
echo "--------------------------------"
echo "运行: $MAVEN_CMD package -DskipTests"
if $MAVEN_CMD package -DskipTests; then
    echo "✅ 打包成功"
else
    echo "❌ 打包失败"
    exit 1
fi

echo ""

# 6. 检查生成的JAR
echo "6. 检查生成的JAR文件:"
echo "--------------------------------"
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    echo "✅ JAR文件存在: $JAR_FILE"
    echo "文件大小: $(ls -lh $JAR_FILE | awk '{print $5}')"
    
    # 检查JAR内容
    echo ""
    echo "检查JAR内容:"
    echo "主类: $(jar -xf $JAR_FILE META-INF/MANIFEST.MF >/dev/null 2>&1 && grep "Start-Class" META-INF/MANIFEST.MF | cut -d' ' -f2 || echo "未找到")"
    rm -rf META-INF 2>/dev/null
    
    # 检查主类是否在JAR中
    if jar -tf "$JAR_FILE" | grep -q "BOOT-INF/classes/com/jd/genie/GenieApplication.class"; then
        echo "✅ 主类存在于JAR中"
    else
        echo "❌ 主类不存在于JAR中"
    fi
else
    echo "❌ JAR文件不存在"
    exit 1
fi

echo ""

# 7. 尝试启动JAR (前台模式，便于看错误)
echo "7. 尝试启动JAR文件:"
echo "--------------------------------"
echo "运行: java -jar $JAR_FILE"
echo "注意: 这将在前台运行，按Ctrl+C停止"
echo ""
echo "如果启动失败，请仔细查看错误信息:"
echo ""

# 设置较短的超时，便于调试
timeout 30 java -jar "$JAR_FILE" --server.port=8080 || {
    echo ""
    echo "❌ JAR启动失败或超时"
    echo ""
    echo "🔍 可能的问题:"
    echo "1. 端口8080被占用"
    echo "2. 配置文件有问题"
    echo "3. 依赖冲突"
    echo "4. 内存不足"
    echo ""
    echo "🔧 建议排查:"
    echo "1. 检查端口: lsof -i :8080"
    echo "2. 查看完整日志: java -jar $JAR_FILE 2>&1 | tee startup.log"
    echo "3. 增加JVM内存: java -Xmx2g -jar $JAR_FILE"
    echo "4. 检查配置文件语法"
}

echo ""
echo "=== 诊断完成 ==="
