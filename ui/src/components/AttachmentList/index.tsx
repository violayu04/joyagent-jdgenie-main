import { iconType } from "@/utils/constants";
import docxIcon from "@/assets/icon/docx.png";
import { Tooltip, Tag, Button, Card, Modal } from "antd";
import { EyeOutlined, LoadingOutlined, FileTextOutlined } from '@ant-design/icons';
import { useState } from 'react';

type Props = {
  files: CHAT.TFile[];
  preview?: boolean;
  remove?: (index: number) => void;
  review?: (file: CHAT.TFile) => void;
};

const AttachmentList: GenieType.FC<Props> = (props) => {
  const { files, preview, remove, review } = props;
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedFile, setSelectedFile] = useState<CHAT.TFile | null>(null);

  const formatSize = (size: number) => {
    const units = ["B", "KB", "MB", "GB"];
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    return `${size?.toFixed(2)} ${units[unitIndex]}`;
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

  const combinIcon = (f: CHAT.TFile) => {
    const imgType = ["jpg", "png", "jpeg"];
    if (imgType.includes(f.type)) {
      return f.url;
    } else {
      return iconType[f.type] || docxIcon;
    }
  };

  const removeFile = (index: number) => {
    remove?.(index);
  };

  const reviewFile = (f: CHAT.TFile) => {
    if (f.analysis?.success) {
      setSelectedFile(f);
      setModalVisible(true);
    } else {
      review?.(f);
    }
  };

  const showFullContent = (f: CHAT.TFile) => {
    setSelectedFile(f);
    setModalVisible(true);
  };

  // Check if we have any files with analysis (enhanced display mode)
  const hasAnalysisFiles = files.some(f => f.analysis || f.analyzing);

  const renderEnhancedFile = (f: CHAT.TFile, index: number) => {
    return (
      <Card key={index} size="small" className="bg-blue-50 border-blue-200 mb-2">
        <div className="space-y-2">
          {/* File Header */}
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <span className="text-lg">{getFileIcon(f.name)}</span>
              <div>
                <div className="font-medium text-sm">{f.name}</div>
                <div className="text-xs text-gray-500">
                  {formatSize(f.size)}
                  {f.analysis?.success && (
                    <span> • {f.analysis.metadata.word_count} 词</span>
                  )}
                  {f.analysis?.metadata.page_count && (
                    <span> • {f.analysis.metadata.page_count} 页</span>
                  )}
                </div>
              </div>
            </div>
            
            <div className="flex items-center space-x-1">
              {f.analyzing ? (
                <Tag icon={<LoadingOutlined />} color="processing">
                  提取中
                </Tag>
              ) : f.analysis ? (
                <Tag color={f.analysis.success ? 'success' : 'error'}>
                  {f.analysis.success ? '已提取' : '提取失败'}
                </Tag>
              ) : (
                <Tag color="default">待提取</Tag>
              )}
              
              {preview && f.analysis?.success && (
                <Button
                  type="text"
                  size="small"
                  icon={<EyeOutlined />}
                  onClick={() => reviewFile(f)}
                  title="查看详细分析"
                />
              )}
              
              {!preview && (
                <Button
                  type="text"
                  danger
                  size="small"
                  icon={<i className="font_family icon-jia-1" />}
                  onClick={() => removeFile(index)}
                />
              )}
            </div>
          </div>

          {/* Show Full Content Button Directly */}
          {f.analysis?.success && (
            <div className="mt-2">
              <Button
                type="link"
                size="small"
                onClick={() => showFullContent(f)}
                className="text-blue-600 hover:text-blue-800 p-0 h-auto"
                icon={<EyeOutlined />}
              >
                查看完整内容
              </Button>
            </div>
          )}

          {/* Error Display */}
          {f.analysis && !f.analysis.success && (
            <div className="bg-red-50 border border-red-200 rounded p-2 text-sm text-red-600">
              <div className="font-medium">内容提取失败</div>
              <div>{f.analysis.error}</div>
            </div>
          )}

          {/* Analysis in Progress */}
          {f.analyzing && (
            <div className="bg-blue-50 border border-blue-200 rounded p-2 text-sm text-blue-600">
              <div className="flex items-center space-x-2">
                <LoadingOutlined />
                <span>正在提取文档内容作为对话上下文...</span>
              </div>
            </div>
          )}
        </div>
      </Card>
    );
  };

  const renderBasicFile = (f: CHAT.TFile, index: number) => {
    return (
      <div
        key={index}
        className={`group w-200 h-56 rounded-xl border border-[#E9E9F0] p-[8px] box-border flex items-center relative ${preview ? "cursor-pointer" : "cursor-default"}`}
        onClick={() => reviewFile(f)}
      >
        <img src={combinIcon(f)} alt={f.name} className="w-32 h-32 shrink" />
        <div className="flex-1 ml-[4px] overflow-hidden">
          <Tooltip title={f.name}>
            <div className="w-full overflow-hidden whitespace-nowrap text-ellipsis text-[14px] text-[#27272A] leading-[20px]">
              {f.name}
            </div>
          </Tooltip>
          <div className="w-full text-[12px] text-[#9E9FA3] leading-[18px]">
            {formatSize(f.size)}
          </div>
        </div>
        {!preview ? (
          <i
            className="font_family icon-jia-1 absolute top-[10px] right-[8px] cursor-pointer hidden group-hover:block"
            onClick={() => removeFile(index)}
          ></i>
        ) : null}
      </div>
    );
  };

  if (files.length === 0) return null;

  return (
    <div className="w-full">
      {hasAnalysisFiles ? (
        <div className="space-y-2">
          <div className="flex items-center space-x-2 text-sm text-gray-600 mb-2">
            <FileTextOutlined />
            <span>文档附件 ({files.length})</span>
          </div>
          {files.map((f, index) => renderEnhancedFile(f, index))}
        </div>
      ) : (
        <div className="flex gap-8 flex-wrap">
          {files.map((f, index) => renderBasicFile(f, index))}
        </div>
      )}
      
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
                    <div><strong>大小:</strong> {formatSize(selectedFile.analysis.metadata.file_size)}</div>
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

export default AttachmentList;