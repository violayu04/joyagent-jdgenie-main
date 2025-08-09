import React, { useState, useEffect } from 'react';
import { Select, Space, Tag, Spin, Empty } from 'antd';
import { FileTextOutlined } from '@ant-design/icons';
import { useAuth } from '../../contexts/AuthContext';
import type { Document } from '../../types/knowledgebase';

const { Option } = Select;

interface DocumentSelectorProps {
  knowledgeBaseId?: number;
  value?: string[];
  onChange?: (documentIds: string[]) => void;
  placeholder?: string;
  disabled?: boolean;
}

const DocumentSelector: React.FC<DocumentSelectorProps> = ({
  knowledgeBaseId,
  value = [],
  onChange,
  placeholder = "选择特定文档（可选）",
  disabled = false
}) => {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(false);
  const { token } = useAuth();

  useEffect(() => {
    if (knowledgeBaseId) {
      loadDocuments();
    } else {
      setDocuments([]);
      if (value.length > 0) {
        onChange?.([]);
      }
    }
  }, [knowledgeBaseId, token]);

  const loadDocuments = async () => {
    if (!token || !knowledgeBaseId) return;
    
    setLoading(true);
    try {
      const response = await fetch(`/api/knowledge-base/${knowledgeBaseId}/documents`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (response.ok) {
        const data = await response.json();
        setDocuments(data);
      }
    } catch (error) {
      console.error('Failed to load documents:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (selectedValues: string[]) => {
    onChange?.(selectedValues);
  };

  const getSelectedDocuments = () => {
    return documents.filter(doc => value.includes(doc.documentId));
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return 'green';
      case 'PROCESSING': return 'orange';
      case 'FAILED': return 'red';
      default: return 'default';
    }
  };

  if (!knowledgeBaseId) {
    return null;
  }

  return (
    <div className="document-selector" style={{ marginTop: 8 }}>
      <div style={{ marginBottom: 6, fontSize: '13px', color: '#666', fontWeight: 500 }}>
        📄 选择特定文档（可选）：
      </div>
      <Select
        mode="multiple"
        value={value}
        onChange={handleChange}
        placeholder={placeholder}
        loading={loading}
        disabled={disabled}
        allowClear
        style={{ minWidth: 300, width: '100%' }}
        suffixIcon={<FileTextOutlined />}
        showSearch
        filterOption={(input, option) => 
          option?.children?.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0
        }
        notFoundContent={loading ? <Spin size="small" /> : <Empty description="暂无文档" />}
        dropdownStyle={{ 
          zIndex: 9999,
          maxHeight: 300 
        }}
        getPopupContainer={(triggerNode) => triggerNode.parentElement || document.body}
        tagRender={({ label, value: docId, closable, onClose }) => {
          const doc = documents.find(d => d.documentId === docId);
          return (
            <Tag
              color="blue"
              closable={closable}
              onClose={onClose}
              style={{ marginRight: 3, marginBottom: 4 }}
            >
              <Space size={4}>
                <FileTextOutlined />
                <span title={doc?.filename} style={{ maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', display: 'inline-block' }}>
                  {doc?.filename}
                </span>
              </Space>
            </Tag>
          );
        }}
      >
        {documents.map(doc => (
          <Option key={doc.documentId} value={doc.documentId}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Space>
                <FileTextOutlined />
                <span>{doc.filename}</span>
              </Space>
              <Space size={4}>
                <Tag size="small" color={getStatusColor(doc.status)}>
                  {doc.status}
                </Tag>
                <Tag size="small" color="default">
                  {formatFileSize(doc.fileSize)}
                </Tag>
              </Space>
            </div>
          </Option>
        ))}
      </Select>
      
      {value.length > 0 && (
        <div style={{ marginTop: 4, fontSize: '12px', color: '#666', textAlign: 'right' }}>
          已选择 {value.length} 个文档
        </div>
      )}
    </div>
  );
};

export default DocumentSelector;