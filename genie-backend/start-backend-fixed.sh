#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 JoyAgent 后端修复启动脚本${NC}"
echo "=================================="

# 进入后端目录
cd genie-backend

# 找到Maven
MAVEN_CMD=""
if command -v mvn &> /dev/null; then
    MAVEN_CMD="mvn"
elif [ -f "/opt/homebrew/bin/mvn" ]; then
    MAVEN_CMD="/opt/homebrew/bin/mvn"
elif [ -f "/usr/local/bin/mvn" ]; then
    MAVEN_CMD="/usr/local/bin/mvn"
else
    echo -e "${RED}❌ 未找到Maven，请先安装Maven${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 使用Maven: $MAVEN_CMD${NC}"

# 检查JAR文件是否存在
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}⚠️  JAR文件不存在，开始构建...${NC}"
    
    # 清理并构建
    echo -e "${BLUE}🔨 构建项目...${NC}"
    if $MAVEN_CMD clean package -DskipTests; then
        echo -e "${GREEN}✅ 构建成功${NC}"
    else
        echo -e "${RED}❌ 构建失败，请检查错误信息${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✅ 找到JAR文件: $JAR_FILE${NC}"

# 设置日志文件
LOGFILE="./genie-backend_startup.log"
echo -e "${BLUE}📝 日志文件: $LOGFILE${NC}"

# 清空之前的日志
> $LOGFILE

echo -e "${BLUE}🚀 启动JoyAgent后端服务...${NC}"
echo -e "${BLUE}🌐 服务将在以下地址启动:${NC}"
echo -e "   - 主要API: http://localhost:8080"
echo -e "   - 原始文件上传: http://localhost:8080/api/raw-file/upload"  
echo -e "   - 健康检查: http://localhost:8080/web/health"
echo ""

# 使用正确的方式启动Spring Boot应用（后台运行）
java -jar "$JAR_FILE" \
    -Dfile.encoding=UTF-8 \
    -Dspring.profiles.active=prod \
    --server.port=8080 \
    > $LOGFILE 2>&1 &

# 获取进程ID
BACKEND_PID=$!
echo -e "${GREEN}✅ 后端服务启动中... (PID: $BACKEND_PID)${NC}"

# 导出PID到父进程
echo "export BACKEND_PID=$BACKEND_PID" > ../backend_pid.sh

# 等待服务启动
echo -e "${BLUE}⏳ 等待后端服务启动...${NC}"
MAX_WAIT=30
WAIT_COUNT=0

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if curl -s http://localhost:8080/web/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 后端服务启动成功！${NC}"
        echo -e "${GREEN}🌐 访问地址: http://localhost:8080${NC}"
        break
    fi
    
    # 检查进程是否还在运行
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        echo -e "${RED}❌ 后端服务启动失败，进程已退出${NC}"
        echo -e "${YELLOW}📋 最近的错误日志:${NC}"
        tail -20 $LOGFILE
        exit 1
    fi
    
    WAIT_COUNT=$((WAIT_COUNT + 1))
    echo -e "${YELLOW}   等待中... ($WAIT_COUNT/$MAX_WAIT)${NC}"
    sleep 2
done

if [ $WAIT_COUNT -eq $MAX_WAIT ]; then
    echo -e "${RED}❌ 后端服务启动超时${NC}"
    echo -e "${YELLOW}📋 日志内容:${NC}"
    tail -20 $LOGFILE
    kill $BACKEND_PID 2>/dev/null
    exit 1
fi

echo -e "${GREEN}🎉 后端服务启动完成！${NC}"
echo -e "${BLUE}📊 实时日志查看: tail -f $LOGFILE${NC}"
echo -e "${BLUE}🛑 停止服务: kill $BACKEND_PID${NC}"
