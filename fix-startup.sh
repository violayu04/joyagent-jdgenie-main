#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 JoyAgent 启动问题修复脚本${NC}"
echo "=================================="

# 检查当前目录
if [ ! -d "genie-backend" ]; then
    echo -e "${RED}❌ 请在JoyAgent项目根目录运行此脚本${NC}"
    exit 1
fi

echo -e "${BLUE}1. 修复后端启动脚本...${NC}"

# 修复后端start.sh脚本
cat > genie-backend/start.sh << 'EOF'
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
EOF

chmod +x genie-backend/start.sh
echo -e "${GREEN}✅ 后端启动脚本已修复${NC}"

echo -e "${BLUE}2. 检查后端构建状态...${NC}"

cd genie-backend

# 找到Maven
MAVEN_CMD=""
if command -v mvn &> /dev/null; then
    MAVEN_CMD="mvn"
elif [ -f "/opt/homebrew/bin/mvn" ]; then
    MAVEN_CMD="/opt/homebrew/bin/mvn"
elif [ -f "/usr/local/bin/mvn" ]; then
    MAVEN_CMD="/usr/local/bin/mvn"
fi

if [ -z "$MAVEN_CMD" ]; then
    echo -e "${RED}❌ 未找到Maven，请先安装Maven${NC}"
    echo -e "${YELLOW}💡 安装命令: brew install maven${NC}"
    cd ..
    exit 1
fi

# 检查并构建项目
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}⚠️  JAR文件不存在，开始构建...${NC}"
    if $MAVEN_CMD clean package -DskipTests; then
        echo -e "${GREEN}✅ 后端构建成功${NC}"
    else
        echo -e "${RED}❌ 后端构建失败${NC}"
        cd ..
        exit 1
    fi
else
    echo -e "${GREEN}✅ 后端JAR文件已存在${NC}"
fi

cd ..

echo -e "${BLUE}3. 测试后端启动...${NC}"

# 测试后端启动
cd genie-backend
echo -e "${BLUE}🧪 测试后端服务启动...${NC}"

# 停止可能存在的服务
if [ -f "genie-backend.pid" ]; then
    OLD_PID=$(cat genie-backend.pid)
    if kill -0 $OLD_PID 2>/dev/null; then
        echo -e "${YELLOW}⚠️  停止旧的后端服务 (PID: $OLD_PID)${NC}"
        kill $OLD_PID
        sleep 2
    fi
    rm -f genie-backend.pid
fi

# 启动服务
echo -e "${BLUE}🚀 启动后端服务进行测试...${NC}"
java -jar "$JAR_FILE" \
    -Dfile.encoding=UTF-8 \
    --server.port=8080 \
    > genie-backend_startup.log 2>&1 &

TEST_PID=$!
echo -e "${GREEN}✅ 测试启动中 (PID: $TEST_PID)${NC}"

# 等待服务启动
echo -e "${BLUE}⏳ 等待服务启动...${NC}"
MAX_WAIT=30
WAIT_COUNT=0

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if curl -s http://localhost:8080/web/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 后端服务测试成功！${NC}"
        echo -e "${GREEN}🌐 访问地址: http://localhost:8080${NC}"
        echo -e "${GREEN}📤 原始文件上传: http://localhost:8080/api/raw-file/upload${NC}"
        
        # 停止测试服务
        kill $TEST_PID 2>/dev/null
        sleep 2
        
        echo -e "${GREEN}🎉 后端修复完成！${NC}"
        echo ""
        echo -e "${BLUE}现在可以使用以下方式启动：${NC}"
        echo -e "1. 只启动后端: cd genie-backend && ./start-correct.sh"
        echo -e "2. 完整启动: ./Genie_start.sh"
        echo ""
        cd ..
        exit 0
    fi
    
    # 检查进程是否还在运行
    if ! kill -0 $TEST_PID 2>/dev/null; then
        echo -e "${RED}❌ 后端服务测试失败，进程已退出${NC}"
        echo -e "${YELLOW}📋 错误日志:${NC}"
        tail -20 genie-backend_startup.log
        cd ..
        exit 1
    fi
    
    WAIT_COUNT=$((WAIT_COUNT + 1))
    echo -e "${YELLOW}   等待中... ($WAIT_COUNT/$MAX_WAIT)${NC}"
    sleep 2
done

# 超时处理
echo -e "${RED}❌ 后端服务启动超时${NC}"
echo -e "${YELLOW}📋 日志内容:${NC}"
tail -20 genie-backend_startup.log
kill $TEST_PID 2>/dev/null
cd ..
exit 1
