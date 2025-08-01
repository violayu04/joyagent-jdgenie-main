# DashScope API Key Setup Guide

## 🚀 Getting Your DashScope API Key

### Step 1: Sign Up for DashScope
1. Go to: https://dashscope.console.aliyun.com/
2. Click "Sign Up" or "登录" (Login)
3. Create an Alibaba Cloud account (free)
4. Verify your email and phone number

### Step 2: Get Your API Key
1. After logging in, go to the DashScope console
2. Navigate to "API-KEY" section in the left sidebar
3. Click "Create API Key" or "创建API-KEY"
4. Copy your new API key (it should start with "sk-" followed by a long string)

### Step 3: Add Credits (if needed)
- DashScope usually provides free trial credits
- You can add more credits if needed for production use
- Check your quota in the console

### Step 4: Update Your Configuration
1. Open your `.env` file
2. Replace the empty `DASHSCOPE_API_KEY=` with your new key:
   ```
   DASHSCOPE_API_KEY=sk-your-actual-key-here
   ```
3. Save the file

### Step 5: Test Your Setup
Run the test script:
```bash
cd /Users/yuhaiyan/Desktop/joyagent-jdgenie-main/genie-client
python test_dashscope.py
```

## 🔧 Alternative: Use Free Local Model

If you prefer not to use cloud APIs, you can set up Ollama with Qwen locally:

### Install Ollama
```bash
# Install Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# Pull Qwen model
ollama pull qwen:7b
```

### Configure for Local Use
Update your `.env` file:
```
DASHSCOPE_API_KEY=local
QWEN_BASE_URL=http://localhost:11434/v1
QWEN_MODEL=qwen:7b
```

## 📝 Notes
- DashScope is Alibaba Cloud's AI platform
- It provides official access to Qwen models
- Free tier is usually sufficient for testing
- Production usage may require paid credits

## 🆘 Troubleshooting
- Make sure your API key is valid
- Check your account has sufficient credits
- Verify network connectivity to Alibaba Cloud
- Try different Qwen models if one doesn't work

## 🌐 Useful Links
- DashScope Console: https://dashscope.console.aliyun.com/
- DashScope Documentation: https://help.aliyun.com/zh/dashscope/
- Qwen Models: https://qianwen.aliyun.com/
