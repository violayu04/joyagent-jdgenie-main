#!/bin/bash

echo "=== JoyAgent 原始文档上传测试 ==="

# 检查应用是否运行
if ! curl -s http://localhost:8080/web/health > /dev/null; then
    echo "❌ JoyAgent应用未运行，请先启动:"
    echo "   ./start-correct.sh"
    exit 1
fi

echo "✅ JoyAgent应用正在运行"

# 创建测试文件
TEST_FILE="test-document.txt"
echo "这是一个测试文档，用于验证原始文档上传功能。

文档内容：
- 项目名称: JoyAgent 原始文档上传测试
- 测试目的: 验证后端不进行文本提取，直接发送给LLM
- 预期行为: LLM直接分析此原始内容

关键指标:
1. 上传速度: 快速
2. 处理方式: 原始内容直接传输
3. 分析责任: 完全由LLM负责

测试数据:
数值A: 123.45
数值B: 678.90
总计: 802.35

结论: 此文档应该被LLM直接分析，无需后端预处理。" > $TEST_FILE

echo "📄 创建测试文件: $TEST_FILE"

# 测试原始文件上传API
echo ""
echo "🧪 测试原始文件上传API..."
echo "接口: POST /api/raw-file/upload"

RESPONSE=$(curl -s -X POST http://localhost:8080/api/raw-file/upload \
  -F "file=@$TEST_FILE" \
  -F "description=测试原始文档上传功能")

echo ""
echo "📋 API响应:"
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

# 检查响应是否成功
if echo "$RESPONSE" | grep -q '"success":true'; then
    echo ""
    echo "✅ 原始文件上传API测试成功!"
    echo ""
    
    # 提取LLM响应
    LLM_RESPONSE=$(echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if 'llmResponse' in data:
        print('LLM分析结果:')
        print(data['llmResponse'][:500] + '...' if len(data['llmResponse']) > 500 else data['llmResponse'])
    else:
        print('未找到LLM响应')
except:
    pass
" 2>/dev/null)
    
    if [ ! -z "$LLM_RESPONSE" ]; then
        echo "🤖 $LLM_RESPONSE"
    fi
    
else
    echo ""
    echo "❌ 原始文件上传API测试失败"
    echo "请检查应用日志获取详细错误信息"
fi

# 清理测试文件
rm -f $TEST_FILE

echo ""
echo "🔍 验证要点："
echo "1. 检查应用日志中是否出现 '开始原始文件上传（不进行分析）'"
echo "2. 确认没有出现 '文档分析' 或 '文本提取' 相关日志"
echo "3. 验证LLM直接接收并分析了原始文档内容"
echo ""
echo "📊 查看实时日志:"
echo "   tail -f genie-backend_startup.log | grep -E '原始文件|RAW|LLM'"
echo ""
echo "=== 测试完成 ==="
