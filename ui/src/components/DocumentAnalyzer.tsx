import React, { useState, useCallback } from 'react';
import { Upload, Button, Input, Select, Card, List, Typography, message, Spin, Tag, Space, Alert } from 'antd';
import { InboxOutlined, FileTextOutlined, DeleteOutlined, BarChartOutlined } from '@ant-design/icons';
import type { UploadProps, UploadFile } from 'antd';

const { Dragger } = Upload;
const { TextArea } = Input;
const { Option } = Select;
const { Title, Text, Paragraph } = Typography;

interface DocumentAnalysisResult {
  success: boolean;
  analysis: string;
  metadata: {
    filename: string;
    file_type: string;
    file_size: number;
    word_count: number;
    page_count?: number;
  };
  error?: string;
  timestamp: string;
}

interface AnalysisResponse {
  code: number;
  message: string;
  data: {
    total_files: number;
    successful_analyses: number;
    results: DocumentAnalysisResult[];
    query: string;
    session_id: string;
    timestamp: string;
  };
}

interface DocumentAnalyzerProps {
  sessionId: string;
  userId?: string;
  onAnalysisComplete?: (results: DocumentAnalysisResult[]) => void;
}

// API base URL - adjust to match your backend
const API_BASE_URL = 'http://localhost:8188';

const DocumentAnalyzer: React.FC<DocumentAnalyzerProps> = ({
  sessionId,
  userId,
  onAnalysisComplete
}) => {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [query, setQuery] = useState<string>('');
  const [analysisType, setAnalysisType] = useState<string>('general');
  const [loading, setLoading] = useState<boolean>(false);
  const [results, setResults] = useState<DocumentAnalysisResult[]>([]);
  const [supportedFormats, setSupportedFormats] = useState<Record<string, string>>({});

  // Fetch supported formats on component mount
  React.useEffect(() => {
    fetchSupportedFormats();
  }, []);

  const fetchSupportedFormats = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/v1/document/supported-formats`);
      const data = await response.json();
      if (data.code === 200) {
        setSupportedFormats(data.data.supported_formats);
      }
    } catch (error) {
      console.error('Failed to fetch supported formats:', error);
      // Set default supported formats if API fails
      setSupportedFormats({
        '.pdf': 'application/pdf',
        '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        '.txt': 'text/plain',
        '.csv': 'text/csv',
        '.json': 'application/json',
        '.md': 'text/markdown'
      });
    }
  };

  const uploadProps: UploadProps = {
    name: 'files',
    multiple: true,
    fileList,
    beforeUpload: (file) => {
      const fileExtension = '.' + (file.name.split('.').pop()?.toLowerCase() || '');
      const supportedExtensions = Object.keys(supportedFormats);
      
      if (supportedExtensions.length > 0 && !supportedExtensions.includes(fileExtension)) {
        message.error(`不支持的文件格式: ${fileExtension}`);
        return false;
      }
      
      const isLt50M = (file.size || 0) / 1024 / 1024 < 50;
      if (!isLt50M) {
        message.error('文件大小不能超过 50MB!');
        return false;
      }
      
      return false; // Prevent auto upload
    },
    onChange: (info) => {
      setFileList(info.fileList);
    },
    onRemove: (file) => {
      setFileList(prev => prev.filter(item => item.uid !== file.uid));
    },
    accept: Object.keys(supportedFormats).join(',')
  };

  const handleAnalyze = async () => {
    if (fileList.length === 0) {
      message.error('请至少上传一个文件');
      return;
    }

    if (!query.trim()) {
      message.error('请输入分析查询');
      return;
    }

    setLoading(true);
    setResults([]);

    try {
      const formData = new FormData();
      formData.append('query', query);
      formData.append('session_id', sessionId);
      formData.append('analysis_type', analysisType);
      if (userId) {
        formData.append('user_id', userId);
      }

      fileList.forEach((file) => {
        if (file.originFileObj) {
          formData.append('files', file.originFileObj);
        }
      });

      const response = await fetch(`${API_BASE_URL}/v1/document/analyze`, {
        method: 'POST',
        body: formData
      });

      const data: AnalysisResponse = await response.json();

      if (data.code === 200) {
        setResults(data.data.results);
        message.success(`成功分析 ${data.data.successful_analyses}/${data.data.total_files} 个文档`);
        onAnalysisComplete?.(data.data.results);
      } else {
        message.error(data.message || '分析失败');
      }
    } catch (error) {
      console.error('Analysis error:', error);
      message.error('分析过程中发生错误，请检查服务是否正常运行');
    } finally {
      setLoading(false);
    }
  };

  const getFileIcon = (filename: string) => {
    const extension = filename.split('.').pop()?.toLowerCase();
    switch (extension) {
      case 'pdf':
        return '📄';
      case 'docx':
      case 'doc':
        return '📝';
      case 'txt':
      case 'md':
        return '📃';
      case 'csv':
        return '📊';
      case 'json':
        return '⚙️';
      default:
        return '📄';
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  return (
    <div className="document-analyzer" style={{ padding: '24px' }}>
      <Title level={2}>
        <BarChartOutlined /> 文档智能分析
      </Title>
      
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* File Upload Section */}
        <Card title="📎 文档上传" size="small">
          <Dragger {...uploadProps} style={{ marginBottom: '16px' }}>
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p className="ant-upload-hint">
              支持批量上传。支持的格式: {Object.keys(supportedFormats).length > 0 ? Object.keys(supportedFormats).join(', ') : '加载中...'}
            </p>
          </Dragger>
          
          {fileList.length > 0 && (
            <List
              size="small"
              dataSource={fileList}
              renderItem={(file) => (
                <List.Item
                  actions={[
                    <Button
                      key="delete"
                      type="text"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      onClick={() => {
                        const newFileList = fileList.filter(item => item.uid !== file.uid);
                        setFileList(newFileList);
                      }}
                    />
                  ]}
                >
                  <List.Item.Meta
                    avatar={<span style={{ fontSize: '16px' }}>{getFileIcon(file.name)}</span>}
                    title={file.name}
                    description={`${formatFileSize(file.size || 0)}`}
                  />
                </List.Item>
              )}
            />
          )}
        </Card>

        {/* Analysis Configuration */}
        <Card title="🔍 分析配置" size="small">
          <Space direction="vertical" style={{ width: '100%' }}>
            <div>
              <Text strong>分析类型:</Text>
              <Select
                value={analysisType}
                onChange={setAnalysisType}
                style={{ width: '200px', marginLeft: '8px' }}
                size="small"
              >
                <Option value="general">通用分析</Option>
                <Option value="financial">财务分析</Option>
                <Option value="compliance">合规检查</Option>
                <Option value="risk">风险评估</Option>
              </Select>
            </div>
            
            <div>
              <Text strong>分析查询:</Text>
              <TextArea
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="请输入您想要分析的问题，例如：'总结文档中的关键财务指标和风险因素'、'识别合规相关内容'等"
                rows={3}
                style={{ marginTop: '8px' }}
              />
            </div>
            
            <Button
              type="primary"
              size="large"
              loading={loading}
              onClick={handleAnalyze}
              disabled={fileList.length === 0 || !query.trim()}
              icon={<BarChartOutlined />}
              style={{ alignSelf: 'flex-start' }}
            >
              {loading ? '分析中...' : '开始分析'}
            </Button>
          </Space>
        </Card>

        {/* Analysis Results */}
        {(loading || results.length > 0) && (
          <Card title="📊 分析结果" size="small">
            {loading ? (
              <div style={{ textAlign: 'center', padding: '40px' }}>
                <Spin size="large" />
                <div style={{ marginTop: '16px' }}>
                  <Text>正在使用 Qwen 大模型分析文档...</Text>
                </div>
              </div>
            ) : (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                {results.map((result, index) => (
                  <Card
                    key={index}
                    size="small"
                    title={
                      <Space>
                        <span>{getFileIcon(result.metadata.filename)}</span>
                        <Text strong>{result.metadata.filename}</Text>
                        <Tag color={result.success ? 'success' : 'error'}>
                          {result.success ? '分析成功' : '分析失败'}
                        </Tag>
                      </Space>
                    }
                    extra={
                      <Space size="small">
                        <Tag color="blue">{result.metadata.file_type.toUpperCase()}</Tag>
                        <Tag color="green">{result.metadata.word_count} 词</Tag>
                        {result.metadata.page_count && (
                          <Tag color="orange">{result.metadata.page_count} 页</Tag>
                        )}
                      </Space>
                    }
                  >
                    {result.success ? (
                      <div>
                        <Paragraph>
                          <pre style={{ 
                            whiteSpace: 'pre-wrap', 
                            fontFamily: 'inherit',
                            margin: 0,
                            backgroundColor: '#fafafa',
                            padding: '12px',
                            borderRadius: '6px',
                            border: '1px solid #d9d9d9'
                          }}>
                            {result.analysis}
                          </pre>
                        </Paragraph>
                        <Text type="secondary" style={{ fontSize: '12px' }}>
                          分析时间: {new Date(result.timestamp).toLocaleString('zh-CN')}
                        </Text>
                      </div>
                    ) : (
                      <Alert
                        message="分析失败"
                        description={result.error}
                        type="error"
                        showIcon
                      />
                    )}
                  </Card>
                ))}
              </Space>
            )}
          </Card>
        )}

        {/* Usage Tips */}
        <Card title="💡 使用提示" size="small">
          <Space direction="vertical">
            <Text>
              <strong>支持的分析类型:</strong>
            </Text>
            <ul style={{ marginLeft: '16px', marginBottom: '12px' }}>
              <li><strong>通用分析:</strong> 文档摘要、关键信息提取、内容理解</li>
              <li><strong>财务分析:</strong> 财务指标识别、业绩分析、财务风险评估</li>
              <li><strong>合规检查:</strong> 合规要求核查、政策符合性分析</li>
              <li><strong>风险评估:</strong> 风险因素识别、风险等级评估</li>
            </ul>
            
            <Text>
              <strong>查询示例:</strong>
            </Text>
            <ul style={{ marginLeft: '16px' }}>
              <li>"总结这份财务报告的核心指标和趋势"</li>
              <li>"识别文档中提到的主要风险因素"</li>
              <li>"检查是否符合银行业相关监管要求"</li>
              <li>"提取合同中的关键条款和义务"</li>
            </ul>
            
            {Object.keys(supportedFormats).length === 0 && (
              <Alert
                message="服务连接提示"
                description="当前无法连接到文档分析服务，请确保后端服务正在运行 (http://localhost:8188)"
                type="warning"
                showIcon
                style={{ marginTop: '12px' }}
              />
            )}
          </Space>
        </Card>
      </Space>
    </div>
  );
};

export default DocumentAnalyzer;