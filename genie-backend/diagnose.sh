#!/bin/bash

echo "=== JoyAgent 环境诊断 ==="

# 检查Java
echo "1. 检查Java版本:"
if command -v java &> /dev/null; then
    java -version
else
    echo "❌ Java未安装"
fi

echo ""

# 检查Maven
echo "2. 检查Maven版本:"
if command -v mvn &> /dev/null; then
    mvn -version
else
    echo "❌ Maven未安装"
fi

echo ""

# 检查项目结构
echo "3. 检查项目文件:"
echo "主类文件: $([ -f "src/main/java/com/jd/genie/GenieApplication.java" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo "POM文件: $([ -f "pom.xml" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo "配置文件: $([ -f "src/main/resources/application.yml" ] && echo "✅ 存在" || echo "❌ 不存在")"

echo ""

# 检查新增的原始文件上传功能文件
echo "4. 检查原始文件上传功能文件:"
echo "RawFileRequest.java: $([ -f "src/main/java/com/jd/genie/agent/dto/RawFileRequest.java" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo "IRawFileService.java: $([ -f "src/main/java/com/jd/genie/service/IRawFileService.java" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo "RawFileService.java: $([ -f "src/main/java/com/jd/genie/service/RawFileService.java" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo "RawFileController.java: $([ -f "src/main/java/com/jd/genie/controller/RawFileController.java" ] && echo "✅ 存在" || echo "❌ 不存在")"

echo ""

# 检查构建目录
echo "5. 检查构建状态:"
if [ -d "target" ]; then
    echo "Target目录: ✅ 存在"
    if [ -f "target/genie-backend-0.0.1-SNAPSHOT.jar" ]; then
        echo "JAR文件: ✅ 存在"
        echo "JAR大小: $(ls -lh target/genie-backend-0.0.1-SNAPSHOT.jar | awk '{print $5}')"
    else
        echo "JAR文件: ❌ 不存在 (需要运行 mvn package)"
    fi
else
    echo "Target目录: ❌ 不存在 (需要运行 mvn compile)"
fi

echo ""
echo "=== 诊断完成 ==="

if command -v java &> /dev/null && command -v mvn &> /dev/null; then
    echo "✅ 环境准备就绪，可以运行: ./start-app.sh"
else
    echo "❌ 环境不完整，请先安装Java和Maven"
fi
