#!/usr/bin/env python3
"""
Check if the genie-client server is running
"""

import requests
import sys

def check_server_status():
    """Check if server is running on localhost:8188"""
    
    print("🔍 Checking Genie Client Server Status")
    print("=" * 45)
    
    try:
        print("🌐 Testing connection to http://localhost:8188...")
        response = requests.get("http://localhost:8188/health", timeout=5)
        
        if response.status_code == 200:
            data = response.json()
            print("✅ Server is running!")
            print(f"   📋 Status: {data.get('status', 'unknown')}")
            print(f"   📋 Version: {data.get('version', 'unknown')}")
            print(f"   📋 Document Analysis: {'Enabled' if data.get('document_analysis_enabled') else 'Disabled'}")
            print(f"   📋 Timestamp: {data.get('timestamp', 'unknown')}")
            return True
        else:
            print(f"⚠️ Server responded with status: {response.status_code}")
            print(f"   📋 Response: {response.text}")
            return False
            
    except requests.exceptions.ConnectionError:
        print("❌ Server is not running (Connection refused)")
        print("💡 Try starting the server with:")
        print("   cd /Users/yuhaiyan/Desktop/joyagent-jdgenie-main/genie-client")
        print("   python server.py")
        return False
        
    except requests.exceptions.Timeout:
        print("❌ Server timeout (Server may be starting up)")
        return False
        
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        return False

def check_processes():
    """Check for running Python processes on port 8188"""
    import subprocess
    import re
    
    print(f"\n🔍 Checking for processes on port 8188...")
    
    try:
        # Use lsof to check what's using port 8188
        result = subprocess.run(['lsof', '-i', ':8188'], 
                              capture_output=True, text=True)
        
        if result.returncode == 0 and result.stdout.strip():
            print("📋 Processes using port 8188:")
            print(result.stdout)
        else:
            print("   No processes found using port 8188")
            
    except Exception as e:
        print(f"   Error checking processes: {e}")

if __name__ == "__main__":
    is_running = check_server_status()
    
    if not is_running:
        check_processes()
        
        print(f"\n💡 Next steps:")
        print("   1. Run diagnostics: python diagnose_server.py")
        print("   2. Test startup: python test_server_startup.py") 
        print("   3. Start server: python server.py")
        print("   4. Or use script: ./start_server.sh")
