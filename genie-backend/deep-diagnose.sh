#!/bin/bash

echo "=== JoyAgent 后端详细诊断和修复 ==="

cd "$(dirname "$0")"

# 检查是否在正确目录
if [ ! -f "pom.xml" ]; then
    echo "❌ 请在 genie-backend 目录下运行此脚本"
    exit 1
fi

echo "🔍 开始详细诊断..."
echo ""

# 1. 清理可能的残留进程
echo "1. 清理端口和进程:"
echo "--------------------------------"
PIDS=$(lsof -ti :8080 2>/dev/null || true)
if [ ! -z "$PIDS" ]; then
    echo "发现占用8080端口的进程: $PIDS"
    for pid in $PIDS; do
        echo "停止进程 $pid"
        kill -9 "$pid" 2>/dev/null || true
    done
    sleep 2
else
    echo "✅ 端口8080空闲"
fi

# 2. 检查JAR文件
echo ""
echo "2. 检查JAR文件:"
echo "--------------------------------"
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    echo "✅ JAR文件存在"
    echo "文件大小: $(ls -lh $JAR_FILE | awk '{print $5}')"
    echo "修改时间: $(ls -l $JAR_FILE | awk '{print $6, $7, $8}')"
else
    echo "❌ JAR文件不存在，开始构建..."
    
    # 找到Maven
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
    if $MAVEN_CMD clean package -DskipTests; then
        echo "✅ 构建成功"
    else
        echo "❌ 构建失败"
        exit 1
    fi
fi

# 3. 测试JAR文件有效性
echo ""
echo "3. 测试JAR文件:"
echo "--------------------------------"
echo "检查MANIFEST.MF..."
if jar -tf "$JAR_FILE" | grep -q "META-INF/MANIFEST.MF"; then
    echo "✅ MANIFEST.MF存在"
    
    # 提取并查看MANIFEST.MF
    jar -xf "$JAR_FILE" META-INF/MANIFEST.MF >/dev/null 2>&1
    if [ -f "META-INF/MANIFEST.MF" ]; then
        echo "Main-Class: $(grep "Main-Class" META-INF/MANIFEST.MF | cut -d' ' -f2-)"
        echo "Start-Class: $(grep "Start-Class" META-INF/MANIFEST.MF | cut -d' ' -f2-)"
        rm -rf META-INF
    fi
else
    echo "❌ MANIFEST.MF不存在"
fi

# 4. 直接启动JAR并捕获详细错误
echo ""
echo "4. 直接启动测试:"
echo "--------------------------------"
echo "尝试前台启动JAR文件 (10秒超时)..."

# 创建临时日志文件
TEMP_LOG="temp_startup.log"
> $TEMP_LOG

# 启动JAR并捕获所有输出
echo "java -jar $JAR_FILE --server.port=8080"
timeout 10s java -jar "$JAR_FILE" --server.port=8080 > $TEMP_LOG 2>&1 &
START_PID=$!

# 等待启动
sleep 3

# 检查进程是否还在运行
if kill -0 $START_PID 2>/dev/null; then
    echo "✅ 进程启动正常 (PID: $START_PID)"
    
    # 再等待几秒让服务完全启动
    sleep 5
    
    # 测试健康检查
    if curl -s http://localhost:8080/web/health >/dev/null 2>&1; then
        echo "✅ 健康检查通过"
        echo "🎉 后端服务启动成功！"
        
        # 停止测试进程
        kill $START_PID 2>/dev/null
        
        echo ""
        echo "✅ 诊断结果: 后端服务可以正常启动"
        echo "🚀 可以使用以下命令启动:"
        echo "   java -jar $JAR_FILE --server.port=8080"
        
    else
        echo "❌ 健康检查失败"
        echo "进程在运行但服务不响应"
        
        # 显示日志
        echo ""
        echo "📋 启动日志:"
        cat $TEMP_LOG
        
        kill $START_PID 2>/dev/null
    fi
else
    echo "❌ 进程启动失败或已退出"
    
    # 显示日志
    echo ""
    echo "📋 错误日志:"
    cat $TEMP_LOG
    
    echo ""
    echo "🔍 可能的问题:"
    echo "1. Java版本不兼容"
    echo "2. 依赖包冲突"
    echo "3. 配置文件错误"
    echo "4. 端口权限问题"
fi

# 清理
rm -f $TEMP_LOG

echo ""
echo "=== 诊断完成 ==="
