import { memo, useState } from 'react';
import { DocumentAnalyzer } from '@/components';
import { Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ArrowLeftOutlined } from '@ant-design/icons';

type DocumentsProps = Record<string, never>;

const Documents: GenieType.FC<DocumentsProps> = memo(() => {
  const navigate = useNavigate();
  const [sessionId] = useState(() => `doc_session_${Date.now()}`);
  const [userId] = useState(() => 'user_001'); // In real app, get from auth context

  const handleAnalysisComplete = (results: any[]) => {
    console.log('Analysis completed:', results);
    // You can add additional logic here, like showing notifications
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-50 to-white">
      {/* Header */}
      <div className="sticky top-0 z-10 bg-white/80 backdrop-blur-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Button 
                type="text" 
                icon={<ArrowLeftOutlined />} 
                onClick={() => navigate('/')}
                className="flex items-center"
              >
                返回主页
              </Button>
              <h1 className="text-2xl font-bold text-gray-900">
                文档智能分析
              </h1>
            </div>
            <div className="text-sm text-gray-500">
              会话 ID: {sessionId}
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="max-w-7xl mx-auto px-4 py-8">
        <DocumentAnalyzer
          sessionId={sessionId}
          userId={userId}
          onAnalysisComplete={handleAnalysisComplete}
        />
      </div>
    </div>
  );
});

Documents.displayName = 'Documents';

export default Documents;