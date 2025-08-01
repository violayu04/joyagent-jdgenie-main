#!/usr/bin/env python3
"""
Validate the Settings configuration after field name changes
"""

import sys
import os
from dotenv import load_dotenv

# Add current directory to path
sys.path.insert(0, '/Users/yuhaiyan/Desktop/joyagent-jdgenie-main/genie-client')

def validate_settings():
    """Validate that the Settings class works correctly"""
    
    print("🔍 Validating Settings Configuration")
    print("=" * 45)
    
    try:
        # Load environment variables
        load_dotenv()
        
        print("1️⃣ Testing Settings import...")
        from app.config import get_settings, Settings
        print("   ✅ Settings import successful")
        
        print("2️⃣ Testing Settings instantiation...")
        settings = get_settings()
        print("   ✅ Settings instantiated successfully")
        
        print("3️⃣ Validating field names...")
        # Test that the new field names exist
        assert hasattr(settings, 'qwen_model'), "qwen_model field missing"
        assert hasattr(settings, 'qwen_base_url'), "qwen_base_url field missing"
        assert hasattr(settings, 'dashscope_api_key'), "dashscope_api_key field missing"
        print("   ✅ All required fields present")
        
        print("4️⃣ Testing field values...")
        print(f"   📋 qwen_model: {settings.qwen_model}")
        print(f"   📋 qwen_base_url: {settings.qwen_base_url}")
        print(f"   📋 dashscope_api_key: {'Set' if settings.dashscope_api_key else 'Not set'}")
        print(f"   📋 upload_directory: {settings.upload_directory}")
        print(f"   📋 max_file_size_mb: {settings.max_file_size_mb}")
        print(f"   📋 analysis_timeout: {settings.analysis_timeout}")
        
        print("5️⃣ Testing environment variable mapping...")
        # Test that environment variables are properly mapped
        llm_model_env = os.getenv("LLM_MODEL", "qwen-max")
        llm_api_base_env = os.getenv("LLM_API_BASE", "https://dashscope.aliyuncs.com/compatible-mode/v1")
        
        assert settings.qwen_model == llm_model_env, f"qwen_model mismatch: {settings.qwen_model} != {llm_model_env}"
        assert settings.qwen_base_url == llm_api_base_env, f"qwen_base_url mismatch: {settings.qwen_base_url} != {llm_api_base_env}"
        print("   ✅ Environment variables correctly mapped")
        
        print("6️⃣ Testing DocumentAnalysisManager compatibility...")
        from app.document_analyzer import DocumentAnalysisManager
        
        # Test that we can access the settings without errors
        test_key = "test-key"
        manager = DocumentAnalysisManager(api_key=test_key)
        print("   ✅ DocumentAnalysisManager compatible with new settings")
        
        print("\n🎉 All validation tests passed!")
        print("💡 The pydantic ValidationError should now be fixed")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Validation failed:")
        print(f"   {type(e).__name__}: {e}")
        
        import traceback
        print(f"\n📋 Full traceback:")
        traceback.print_exc()
        
        return False

def test_old_field_removal():
    """Test that old field names are no longer present"""
    
    print(f"\n🔍 Testing old field name removal...")
    
    try:
        from app.config import get_settings
        settings = get_settings()
        
        # These should NOT exist anymore
        old_fields = ['llm_model', 'llm_api_base']
        
        for field in old_fields:
            if hasattr(settings, field):
                print(f"   ❌ Old field still exists: {field}")
                return False
            else:
                print(f"   ✅ Old field removed: {field}")
        
        return True
        
    except Exception as e:
        print(f"   ❌ Error testing old fields: {e}")
        return False

if __name__ == "__main__":
    success = validate_settings()
    old_fields_removed = test_old_field_removal()
    
    print("\n" + "=" * 45)
    if success and old_fields_removed:
        print("🎉 Configuration validation successful!")
        print("💡 Your pydantic ValidationError should now be resolved")
        print("💡 Try starting the server: python server.py")
    else:
        print("❌ Validation failed - please check the errors above")
