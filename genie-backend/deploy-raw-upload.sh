#!/bin/bash

echo "=== JoyAgent 原始文档上传功能部署 ==="

cd "$(dirname "$0")"

echo "🔧 正在部署原始文档上传功能..."
echo ""

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

echo "✅ 使用Maven: $MAVEN_CMD"

# 显示更新内容
echo ""
echo "📋 本次更新内容："
echo "1. ✅ FileTool已更新 - upload命令现在默认使用原始上传"
echo "2. ✅ DocumentAnalyzerTool已禁用 - 防止后端分析"
echo "3. ✅ RawFileService - 核心原始文件处理服务"
echo "4. ✅ RawFileController - REST API支持"
echo "5. ✅ 日志优化 - 清楚显示处理方式"
echo ""

# 重新编译项目
echo "🔨 重新编译项目..."
if $MAVEN_CMD clean compile; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败，请检查错误"
    exit 1
fi

# 打包项目
echo "📦 打包项目..."
if $MAVEN_CMD package -DskipTests; then
    echo "✅ 打包成功"
else
    echo "❌ 打包失败，请检查错误"
    exit 1
fi

echo ""
echo "🎉 原始文档上传功能部署完成！"
echo ""
echo "📈 功能特点："
echo "• 后端作为纯粹管道 - 不进行任何文本提取或分析"
echo "• 原始文档内容直接发送给LLM"
echo "• LLM完全负责文档解读和分析"
echo "• 支持PDF、Word、Excel、图片等多种格式"
echo "• 最大支持50MB文件上传"
echo ""
echo "🚀 启动应用："
echo "   ./start-correct.sh"
echo ""
echo "🧪 测试接口："
echo "   curl -X POST http://localhost:8080/api/raw-file/upload \\"
echo "        -F \"file=@your-document.pdf\" \\"
echo "        -F \"description=测试文档\""
echo ""
echo "📝 关键变化："
echo "• file_tool的upload命令现在默认使用原始上传"
echo "• DocumentAnalyzerTool已禁用，不会进行后端分析"
echo "• 所有文档分析现在完全由LLM处理"
echo ""
echo "⚠️  注意：重启应用后，所有文档上传都将使用新的原始上传方式！"
