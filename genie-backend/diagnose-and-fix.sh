#!/bin/bash

echo "=== JoyAgent 问题诊断和修复 ==="

cd "$(dirname "$0")"

echo "1. 检查项目结构..."
echo "主类源文件: $([ -f "src/main/java/com/jd/genie/GenieApplication.java" ] && echo "✅ 存在" || echo "❌ 不存在")"
echo "POM文件: $([ -f "pom.xml" ] && echo "✅ 存在" || echo "❌ 不存在")"

echo ""
echo "2. 检查编译结果..."
echo "主类编译文件: $([ -f "target/classes/com/jd/genie/GenieApplication.class" ] && echo "✅ 存在" || echo "❌ 不存在")"

echo ""
echo "3. 检查JAR文件..."
JAR_FILE="target/genie-backend-0.0.1-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    echo "JAR文件: ✅ 存在"
    echo "文件大小: $(ls -lh $JAR_FILE | awk '{print $5}')"
    
    # 检查JAR内的主类
    echo ""
    echo "4. 检查JAR内部结构..."
    
    # 临时解压MANIFEST查看
    jar -xf "$JAR_FILE" META-INF/MANIFEST.MF >/dev/null 2>&1
    if [ -f "META-INF/MANIFEST.MF" ]; then
        echo "MANIFEST.MF内容:"
        echo "Main-Class: $(grep "Main-Class" META-INF/MANIFEST.MF | cut -d' ' -f2-)"
        echo "Start-Class: $(grep "Start-Class" META-INF/MANIFEST.MF | cut -d' ' -f2-)"
        rm -rf META-INF
    fi
    
    # 检查主类是否在JAR中
    if jar -tf "$JAR_FILE" | grep -q "BOOT-INF/classes/com/jd/genie/GenieApplication.class"; then
        echo "主类在JAR中: ✅ 存在"
    else
        echo "主类在JAR中: ❌ 不存在"
    fi
    
else
    echo "JAR文件: ❌ 不存在"
    echo ""
    echo "🔧 尝试重新构建..."
    
    # 尝试重新构建
    if command -v mvn >/dev/null 2>&1; then
        echo "运行: mvn clean package -DskipTests"
        mvn clean package -DskipTests
        
        if [ -f "$JAR_FILE" ]; then
            echo "✅ 重新构建成功!"
        else
            echo "❌ 重新构建失败"
        fi
    else
        echo "❌ Maven未找到，无法自动构建"
    fi
fi

echo ""
echo "5. 诊断结论和建议..."

if [ -f "$JAR_FILE" ] && [ -f "target/classes/com/jd/genie/GenieApplication.class" ]; then
    echo "✅ 项目构建正常"
    echo ""
    echo "🚀 启动建议:"
    echo "1. 使用正确的启动方式:"
    echo "   java -jar $JAR_FILE"
    echo ""
    echo "2. 或使用我们的启动脚本:"
    echo "   ./start-correct.sh"
    echo ""
    echo "3. 测试JAR文件:"
    echo "   ./test-jar.sh"
    echo ""
    echo "❌ 不要使用原来的 start.sh 脚本，它使用了错误的启动方式"
    
else
    echo "❌ 项目构建有问题"
    echo ""
    echo "🔧 修复建议:"
    echo "1. 重新构建项目:"
    echo "   mvn clean package -DskipTests"
    echo ""
    echo "2. 检查编译错误:"
    echo "   mvn compile"
    echo ""
    echo "3. 如果有编译错误，请解决后重试"
fi

echo ""
echo "=== 诊断完成 ==="
