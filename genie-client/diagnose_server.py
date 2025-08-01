#!/usr/bin/env python3
"""
Diagnostic script to check server startup issues
"""

import sys
import os
import subprocess
from pathlib import Path

def check_dependencies():
    """Check if all required dependencies are installed"""
    print("🔍 Checking Dependencies")
    print("-" * 30)
    
    required_packages = [
        'fastapi',
        'uvicorn',
        'httpx',
        'aiofiles',
        'pydantic-settings',
        'python-multipart'
    ]
    
    missing_packages = []
    
    for package in required_packages:
        try:
            __import__(package.replace('-', '_'))
            print(f"✅ {package}")
        except ImportError:
            print(f"❌ {package} - Missing")
            missing_packages.append(package)
    
    return missing_packages

def check_port():
    """Check if port 8188 is available"""
    print(f"\n🌐 Checking Port 8188")
    print("-" * 30)
    
    try:
        import socket
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            result = s.connect_ex(('localhost', 8188))
            if result == 0:
                print("❌ Port 8188 is already in use")
                return False
            else:
                print("✅ Port 8188 is available")
                return True
    except Exception as e:
        print(f"⚠️ Error checking port: {e}")
        return True

def check_python_version():
    """Check Python version"""
    print("🐍 Checking Python Version")
    print("-" * 30)
    
    version = sys.version_info
    print(f"Python {version.major}.{version.minor}.{version.micro}")
    
    if version.major == 3 and version.minor >= 10:
        print("✅ Python version is compatible")
        return True
    else:
        print("❌ Python 3.10+ required")
        return False

def check_environment():
    """Check environment configuration"""
    print(f"\n🔧 Checking Environment")
    print("-" * 30)
    
    # Load .env file
    env_file = Path('.env')
    if env_file.exists():
        print("✅ .env file found")
        
        with open(env_file, 'r') as f:
            content = f.read()
            
        if 'DASHSCOPE_API_KEY=' in content:
            api_key = os.getenv('DASHSCOPE_API_KEY', '')
            if api_key and api_key.strip():
                print(f"✅ DASHSCOPE_API_KEY is set")
            else:
                print(f"⚠️ DASHSCOPE_API_KEY is empty (document analysis will be disabled)")
        else:
            print(f"❌ DASHSCOPE_API_KEY not found in .env")
            
        if 'LLM_API_BASE=' in content:
            print(f"✅ LLM_API_BASE is configured")
        
        if 'LLM_MODEL=' in content:
            print(f"✅ LLM_MODEL is configured")
    else:
        print("❌ .env file not found")
        return False
    
    return True

def try_import_modules():
    """Try importing key modules"""
    print(f"\n📦 Testing Module Imports")
    print("-" * 30)
    
    modules_to_test = [
        ('app.config', 'get_settings'),
        ('app.document_analyzer', 'DocumentAnalysisManager'),
        ('app.logger', 'default_logger'),
        ('fastapi', 'FastAPI'),
        ('uvicorn', None)
    ]
    
    for module_name, attr_name in modules_to_test:
        try:
            module = __import__(module_name, fromlist=[attr_name] if attr_name else [])
            if attr_name:
                getattr(module, attr_name)
            print(f"✅ {module_name}")
        except ImportError as e:
            print(f"❌ {module_name} - {e}")
            return False
        except Exception as e:
            print(f"⚠️ {module_name} - {e}")
    
    return True

def install_missing_packages(packages):
    """Install missing packages"""
    if not packages:
        return True
        
    print(f"\n📥 Installing Missing Packages")
    print("-" * 30)
    
    for package in packages:
        print(f"Installing {package}...")
        try:
            subprocess.check_call([sys.executable, '-m', 'pip', 'install', package])
            print(f"✅ {package} installed successfully")
        except subprocess.CalledProcessError:
            print(f"❌ Failed to install {package}")
            return False
    
    return True

def main():
    """Run all diagnostics"""
    print("🚀 JoyAgent Server Diagnostic")
    print("=" * 50)
    
    # Change to correct directory
    os.chdir('/Users/yuhaiyan/Desktop/joyagent-jdgenie-main/genie-client')
    
    all_good = True
    
    # Check Python version
    if not check_python_version():
        all_good = False
    
    # Check dependencies
    missing = check_dependencies()
    if missing:
        print(f"\n💡 Installing missing packages...")
        if not install_missing_packages(missing):
            all_good = False
    
    # Check port availability
    if not check_port():
        all_good = False
    
    # Check environment
    if not check_environment():
        all_good = False
    
    # Test imports
    if not try_import_modules():
        all_good = False
    
    print(f"\n" + "=" * 50)
    if all_good:
        print("🎉 All checks passed! Try starting the server:")
        print("   python server.py")
        print("   or")
        print("   ./start_server.sh")
    else:
        print("❌ Some issues found. Please fix the errors above.")
        print("💡 You may need to:")
        print("   1. Install missing dependencies")
        print("   2. Add your DashScope API key to .env")
        print("   3. Check Python version (3.10+ required)")

if __name__ == "__main__":
    main()
