import React, { useMemo, useRef, useState, useCallback } from "react";
import { Input, Button, Tooltip, Upload, message, Card, List, Tag, Space, Modal } from "antd";
import { PaperClipOutlined, DeleteOutlined, FileTextOutlined, LoadingOutlined } from '@ant-design/icons';
import classNames from "classnames";
import { TextAreaRef } from "antd/es/input/TextArea";
import { getOS } from "@/utils";
import type { UploadProps } from 'antd';

const { TextArea } = Input;

// Document Analysis Service - focused on content extraction for LLM context
class DocumentAnalysisService {
  private static readonly API_BASE_URL = 'http://localhost:8188';
  
  static async extractDocumentContent(file: File, sessionId: string): Promise<CHAT.DocumentAnalysisResult> {
    const formData = new FormData();
    formData.append('query', '请提取文档的完整内容和关键信息，以便后续基于此内容进行问答。');
    formData.append('session_id', sessionId);
    formData.append('analysis_type', 'content_extraction');
    formData.append('files', file);

    try {
      const response = await fetch(`${this.API_BASE_URL}/v1/document/analyze`, {
        method: 'POST',
        body: formData
      });

      const data = await response.json();
      if (data.code === 200 && data.data.results.length > 0) {
        return data.data.results[0];
      }
      throw new Error(data.message || '文档内容提取失败');
    } catch (error) {
      throw new Error(`内容提取失败: ${error instanceof Error ? error.message : '未知错误'}`);
    }
  }

  static async getSupportedFormats(): Promise<Record<string, string>> {
    try {
      const response = await fetch(`${this.API_BASE_URL}/v1/document/supported-formats`);
      const data = await response.json();
      return data.code === 200 ? data.data.supported_formats : {};
    } catch (error) {
      return {
        '.pdf': 'application/pdf',
        '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        '.txt': 'text/plain',
        '.csv': 'text/csv',
        '.json': 'application/json',
        '.md': 'text/markdown'
      };
    }
  }
}

type Props = {
  placeholder: string;
  showBtn: boolean;
  disabled: boolean;
  size: string;
  product?: CHAT.Product;
  send: (p: CHAT.TInputInfo) => void;
};

const GeneralInput: GenieType.FC<Props> = (props) => {
  const { placeholder, showBtn, disabled, product, send } = props;
  const [question, setQuestion] = useState<string>("");
  const [deepThink, setDeepThink] = useState<boolean>(false);
  const [files, setFiles] = useState<CHAT.TFile[]>([]);
  const [showFileUpload, setShowFileUpload] = useState<boolean>(false);
  const [supportedFormats, setSupportedFormats] = useState<Record<string, string>>({});
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedFile, setSelectedFile] = useState<CHAT.TFile | null>(null);
  const textareaRef = useRef<TextAreaRef>(null);
  const tempData = useRef<{
    cmdPress?: boolean;
    compositing?: boolean;
  }>({});

  // Load supported formats on mount
  React.useEffect(() => {
    DocumentAnalysisService.getSupportedFormats().then(setSupportedFormats);
  }, []);

  const questionChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setQuestion(e.target.value);
  };

  const changeThinkStatus = () => {
    setDeepThink(!deepThink);
  };

  const generateSessionId = () => `genie-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;

  const processFile = useCallback(async (file: CHAT.TFile, originalFile: File) => {
    const sessionId = generateSessionId();
    
    // Update file status to processing
    setFiles(prev => prev.map(f => 
      f.name === file.name ? { ...f, analyzing: true } : f
    ));

    try {
      const contentResult = await DocumentAnalysisService.extractDocumentContent(originalFile, sessionId);
      
      // Update file with extracted content for LLM context
      setFiles(prev => prev.map(f => 
        f.name === file.name ? { 
          ...f, 
          analysis: contentResult, 
          analyzing: false 
        } : f
      ));

      message.success(`${file.name} 内容提取完成，可作为对话上下文`);
    } catch (error) {
      // Update file with error
      setFiles(prev => prev.map(f => 
        f.name === file.name ? { 
          ...f, 
          analysis: {
            success: false,
            analysis: '',
            metadata: {
              filename: file.name,
              file_type: file.type.split('/').pop() || 'unknown',
              file_size: file.size,
              word_count: 0
            },
            error: error instanceof Error ? error.message : '内容提取失败',
            timestamp: new Date().toISOString()
          },
          analyzing: false 
        } : f
      ));

      message.error(`${file.name} 内容提取失败: ${error instanceof Error ? error.message : '未知错误'}`);
    }
  }, []);

  const uploadProps: UploadProps = {
    name: 'files',
    multiple: true,
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

      // Create enhanced file object
      const enhancedFile: CHAT.TFile = {
        name: file.name,
        url: '', // Will be set if needed
        type: file.type,
        size: file.size,
        analyzing: false
      };

      // Add to files and start content extraction
      setFiles(prev => [...prev, enhancedFile]);
      processFile(enhancedFile, file);
      
      return false; // Prevent auto upload
    },
    showUploadList: false,
    accept: Object.keys(supportedFormats).join(',')
  };

  const removeFile = (fileName: string) => {
    setFiles(prev => prev.filter(f => f.name !== fileName));
  };

  const showFullContent = (file: CHAT.TFile) => {
    setSelectedFile(file);
    setModalVisible(true);
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const getFileIcon = (filename: string) => {
    const extension = filename.split('.').pop()?.toLowerCase();
    switch (extension) {
      case 'pdf': return '📄';
      case 'docx': case 'doc': return '📝';
      case 'txt': case 'md': return '📃';
      case 'csv': return '📊';
      case 'json': return '⚙️';
      default: return '📄';
    }
  };

  const pressEnter: React.KeyboardEventHandler<HTMLTextAreaElement> = () => {
    if (tempData.current.compositing) return;
    
    if (tempData.current.cmdPress) {
      const textareaDom = textareaRef.current?.resizableTextArea?.textArea;
      if (!textareaDom) return;
      
      const { selectionStart, selectionEnd } = textareaDom || {};
      const newValue = question.substring(0, selectionStart) + '\n' + question.substring(selectionEnd!);
      
      setQuestion(newValue);
      setTimeout(() => {
        textareaDom.selectionStart = selectionStart! + 1;
        textareaDom.selectionEnd = selectionStart! + 1;
        textareaDom.focus();
      }, 20);
      return;
    }
    
    if (disabled) return;
    sendMessage();
  };

  const sendMessage = () => {
    // Don't send if there are files still being processed
    const processingFiles = files.filter(f => f.analyzing);
    if (processingFiles.length > 0) {
      message.warning(`请等待 ${processingFiles.length} 个文件内容提取完成`);
      return;
    }

    // Auto-generate message if only files uploaded
    const actualMessage = question || (files.length > 0 ? 
      `我上传了 ${files.length} 个文档，请基于这些文档内容回答我的问题。` : '');
    
    if (!actualMessage && files.length === 0) return;

    send({
      message: actualMessage,
      outputStyle: product?.type,
      deepThink,
      files: files.length > 0 ? files : undefined,
    });
    
    setQuestion("");
    // Keep files for context in subsequent messages
  };

  const enterTip = useMemo(() => {
    return `⏎发送，${getOS() === 'Mac' ? '⌘' : '^'} + ⏎ 换行`;
  }, []);

  const hasAnalyzing = files.some(f => f.analyzing);

  return (
    <div className="space-y-2">
      {/* File Upload Panel */}
      {(showFileUpload || files.length > 0) && (
        <Card size="small" className="bg-gray-50">
          <div className="space-y-3">
            {showFileUpload && (
              <Upload.Dragger {...uploadProps} className="bg-white border-dashed border-gray-300">
                <p className="ant-upload-drag-icon">
                  <FileTextOutlined />
                </p>
                <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
                <p className="ant-upload-hint">
                  支持: PDF, DOCX, TXT, CSV, JSON, MD (最大50MB)
                </p>
              </Upload.Dragger>
            )}

            {files.length > 0 && (
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-medium">
                    已上传文档 ({files.length})
                  </span>
                </div>
                
                <List
                  size="small"
                  dataSource={files}
                  renderItem={(file) => (
                    <List.Item className="bg-white rounded px-3 py-2">
                      <List.Item.Meta
                        avatar={<span>{getFileIcon(file.name)}</span>}
                        title={
                          <div className="flex items-center justify-between">
                            <span className="text-sm">{file.name}</span>
                            <Space>
                              {file.analyzing ? (
                                <Tag icon={<LoadingOutlined />} color="processing">
                                  提取中
                                </Tag>
                              ) : file.analysis ? (
                                <Tag
                                  color={file.analysis.success ? 'success' : 'error'}
                                >
                                  {file.analysis.success ? '已提取' : '提取失败'}
                                </Tag>
                              ) : (
                                <Tag color="default">待提取</Tag>
                              )}
                              <Button
                                type="text"
                                danger
                                size="small"
                                icon={<DeleteOutlined />}
                                onClick={() => removeFile(file.name)}
                              />
                            </Space>
                          </div>
                        }
                        description={
                          <div className="text-xs text-gray-500">
                            {formatFileSize(file.size)}
                            {file.analysis?.success && (
                              <span> • {file.analysis.metadata.word_count} 词</span>
                            )}
                          </div>
                        }
                      />
                      
                      {/* Direct Full Content Button */}
                      {file.analysis?.success && (
                        <div className="mt-2">
                          <Button
                            type="link"
                            size="small"
                            onClick={() => showFullContent(file)}
                            className="text-blue-600 hover:text-blue-800 p-0 h-auto"
                            icon={<FileTextOutlined />}
                          >
                            查看完整内容
                          </Button>
                        </div>
                      )}
                      
                      {/* Error Display */}
                      {file.analysis && !file.analysis.success && (
                        <div className="text-xs text-red-600 bg-red-50 p-2 rounded mt-1">
                          {file.analysis.error}
                        </div>
                      )}
                    </List.Item>
                  )}
                />
              </div>
            )}

            <div className="flex justify-between items-center">
              <Button 
                type="text" 
                size="small"
                onClick={() => setShowFileUpload(!showFileUpload)}
              >
                {showFileUpload ? '收起上传' : '展开上传'}
              </Button>
              
              {files.length > 0 && (
                <Button 
                  type="text" 
                  size="small"
                  danger
                  onClick={() => setFiles([])}
                >
                  清空文档
                </Button>
              )}
            </div>
          </div>
        </Card>
      )}

      {/* Main Input Area */}
      <div className={showBtn ? "rounded-[12px] bg-[linear-gradient(to_bottom_right,#4040ff,#ff49fd,#d763fc,#3cc4fa)] p-1" : ""}>
        <div className="rounded-[12px] border border-[#E9E9F0] overflow-hidden p-[12px] bg-[#fff]">
          <div className="relative">
            <TextArea
              ref={textareaRef}
              value={question}
              placeholder={files.length > 0 ? "基于上传的文档，您想了解什么？" : placeholder}
              className={classNames(
                "h-62 no-border-textarea border-0 resize-none p-[0px] focus:border-0 bg-[#fff]",
                showBtn && product ? "indent-86" : ""
              )}
              onChange={questionChange}
              onPressEnter={pressEnter}
              onKeyDown={(event) => {
                tempData.current.cmdPress = event.metaKey || event.ctrlKey;
              }}
              onKeyUp={() => {
                tempData.current.cmdPress = false;
              }}
              onCompositionStart={() => {
                tempData.current.compositing = true;
              }}
              onCompositionEnd={() => {
                tempData.current.compositing = false;
              }}
            />
            {showBtn && product ? (
              <div className="h-[24px] w-[80px] absolute top-0 left-0 flex items-center justify-center rounded-[6px] bg-[#f4f4f9] text-[12px] ">
                <i className={`font_family ${product.img} ${product.color} text-14`}></i>
                <div className="ml-[6px]">{product.name}</div>
              </div>
            ) : null}
          </div>
          
          <div className="h-30 flex justify-between items-center mt-[6px]">
            <div className="flex items-center space-x-2">
              {showBtn ? (
                <Button
                  color={deepThink ? "primary" : "default"}
                  variant="outlined"
                  className={classNames(
                    "text-[12px] p-[8px] h-[28px] transition-all hover:text-[#333] hover:bg-[rgba(64,64,255,0.02)] hover:border-[rgba(64,64,255,0.2)]",
                  )}
                  onClick={changeThinkStatus}
                >
                  <i className="font_family icon-shendusikao"></i>
                  <span className="ml-[-4px]">深度研究</span>
                </Button>
              ) : <div></div>}
              
              {/* File Upload Button */}
              <Button
                type="text"
                size="small"
                icon={<PaperClipOutlined />}
                onClick={() => setShowFileUpload(!showFileUpload)}
                className={classNames(
                  "text-[12px] h-[28px]",
                  files.length > 0 ? "text-blue-600" : "text-gray-400"
                )}
              >
                {files.length > 0 ? `${files.length} 文档` : '上传文档'}
              </Button>
            </div>
            
            <div className="flex items-center">
              <span className="text-[12px] text-gray-300 mr-8 flex items-center">
                {enterTip}
              </span>
              <Tooltip title={hasAnalyzing ? "等待文档内容提取完成" : "发送"}>
                <i
                  className={`font_family icon-fasongtianchong ${
                    (!question && files.length === 0) || disabled || hasAnalyzing
                      ? "cursor-not-allowed text-[#ccc] pointer-events-none" 
                      : "cursor-pointer"
                  }`}
                  onClick={sendMessage}
                />
              </Tooltip>
            </div>
          </div>
        </div>
      </div>
      
      {/* Document Content Modal */}
      <Modal
        title={
          <div className="flex items-center space-x-2">
            <span>{selectedFile ? getFileIcon(selectedFile.name) : '📄'}</span>
            <span>文档内容详情</span>
          </div>
        }
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={1200}
        className="document-content-modal"
      >
        {selectedFile?.analysis && (
          <div className="flex gap-4 h-[70vh]">
            {/* Left Navigation Panel - 25% width */}
            <div className="w-1/4 flex-shrink-0 border-r border-gray-200 pr-4">
              <div className="space-y-4">
                <h4 className="font-medium text-lg mb-3">文档内容详情</h4>

                {/* File Metadata */}
                <Card size="small" className="bg-gray-50">
                  <div className="space-y-2 text-sm">
                    <div><strong>文件名:</strong> {selectedFile.analysis.metadata.filename}</div>
                    <div><strong>类型:</strong> {selectedFile.analysis.metadata.file_type.toUpperCase()}</div>
                    <div><strong>大小:</strong> {formatFileSize(selectedFile.analysis.metadata.file_size)}</div>
                    <div><strong>字数:</strong> {selectedFile.analysis.metadata.word_count.toLocaleString()}</div>
                    {selectedFile.analysis.metadata.page_count && (
                      <div><strong>页数:</strong> {selectedFile.analysis.metadata.page_count}</div>
                    )}
                    <div><strong>提取时间:</strong> {new Date(selectedFile.analysis.timestamp).toLocaleString('zh-CN')}</div>
                  </div>
                </Card>

                {/* Content Usage Info */}
                <Card size="small" title="使用说明" className="bg-blue-50">
                  <div className="space-y-2 text-sm">
                    <div className="flex items-start space-x-2">
                      <span className="w-2 h-2 bg-blue-500 rounded-full mt-1.5 flex-shrink-0"></span>
                      <span>此文档内容已作为上海银行超级智能体的对话上下文</span>
                    </div>
                    <div className="flex items-start space-x-2">
                      <span className="w-2 h-2 bg-green-500 rounded-full mt-1.5 flex-shrink-0"></span>
                      <span>您可以基于此内容进行问答和讨论</span>
                    </div>
                    <div className="flex items-start space-x-2">
                      <span className="w-2 h-2 bg-purple-500 rounded-full mt-1.5 flex-shrink-0"></span>
                      <span>内容将在整个聊天会话中保持可用</span>
                    </div>
                  </div>
                </Card>
              </div>
            </div>

            {/* Right Content Panel - 75% width */}
            <div className="w-3/4 flex-grow min-w-0">
              <div className="h-full flex flex-col">
                <div className="flex items-center justify-between mb-3 flex-shrink-0">
                  <h4 className="font-medium text-lg">文档内容</h4>
                  <Tag color="success">已提取</Tag>
                </div>

                <div className="bg-white border rounded-lg p-4 flex-grow overflow-hidden">
                  <div className="h-full overflow-y-auto">
                    <pre className="whitespace-pre-wrap font-sans text-sm leading-relaxed text-gray-700">
                      {selectedFile.analysis.analysis}
                    </pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default GeneralInput;