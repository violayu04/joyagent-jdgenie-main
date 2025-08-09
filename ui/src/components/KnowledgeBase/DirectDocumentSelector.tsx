import React, { useState, useEffect } from 'react';
import { Select, Space, Tag, Spin, Empty } from 'antd';
import { FileTextOutlined, DatabaseOutlined } from '@ant-design/icons';
import { useAuth } from '../../contexts/AuthContext';
import type { Document, KnowledgeBase } from '../../types/knowledgebase';

const { Option, OptGroup } = Select;

interface DocumentWithKB extends Document {
  knowledgeBaseName: string;
  knowledgeBaseId: number;
}

interface DirectDocumentSelectorProps {
  value?: string[];
  onChange?: (documentIds: string[], knowledgeBaseIds: number[]) => void;
  placeholder?: string;
  disabled?: boolean;
}

const DirectDocumentSelector: React.FC<DirectDocumentSelectorProps> = ({
  value = [],
  onChange,
  placeholder = "选择知识库文档（可选）",
  disabled = false
}) => {
  const [documents, setDocuments] = useState<DocumentWithKB[]>([]);
  const [loading, setLoading] = useState(false);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const { token } = useAuth();

  useEffect(() => {
    loadAllDocuments();
  }, [token]);

  const loadAllDocuments = async () => {
    if (!token) return;
    
    setLoading(true);
    try {
      // First load all knowledge bases
      const kbResponse = await fetch('/api/knowledge-base', {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!kbResponse.ok) return;
      
      const knowledgeBases: KnowledgeBase[] = await kbResponse.json();
      setKnowledgeBases(knowledgeBases);

      // Then load documents for each knowledge base
      const allDocuments: DocumentWithKB[] = [];
      
      for (const kb of knowledgeBases) {
        try {
          const docResponse = await fetch(`/api/knowledge-base/${kb.id}/documents`, {
            headers: {
              'Authorization': `Bearer ${token}`,
            },
          });

          if (docResponse.ok) {
            const documents: Document[] = await docResponse.json();
            documents.forEach(doc => {
              allDocuments.push({
                ...doc,
                knowledgeBaseName: kb.name,
                knowledgeBaseId: kb.id
              });
            });
          }
        } catch (error) {
          console.error(`Failed to load documents for knowledge base ${kb.id}:`, error);
        }
      }

      setDocuments(allDocuments);
    } catch (error) {
      console.error('Failed to load knowledge bases:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (selectedValues: string[]) => {
    // Get the knowledge base IDs for the selected documents
    const selectedDocs = documents.filter(doc => selectedValues.includes(doc.documentId));
    const knowledgeBaseIds = [...new Set(selectedDocs.map(doc => doc.knowledgeBaseId))];
    
    onChange?.(selectedValues, knowledgeBaseIds);
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

  // Group documents by knowledge base
  const groupedDocuments = knowledgeBases.reduce((acc, kb) => {
    const kbDocs = documents.filter(doc => doc.knowledgeBaseId === kb.id);
    if (kbDocs.length > 0) {
      acc[kb.name] = kbDocs;
    }
    return acc;
  }, {} as Record<string, DocumentWithKB[]>);

  return (
    <div className="direct-document-selector">
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
        notFoundContent={loading ? <Spin size="small" /> : (
          <Empty 
            description={
              <div style={{ textAlign: 'center' }}>
                <div>暂无文档</div>
                <div style={{ fontSize: '12px', color: '#999', marginTop: '4px' }}>
                  点击左上角 <DatabaseOutlined /> 管理知识库
                </div>
              </div>
            } 
          />
        )}
        dropdownStyle={{ 
          zIndex: 9999,
          maxHeight: 400 
        }}
        getPopupContainer={(triggerNode) => document.body}
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
                <DatabaseOutlined style={{ fontSize: '10px' }} />
                <span style={{ fontSize: '11px', opacity: 0.7 }}>
                  {doc?.knowledgeBaseName}
                </span>
                <span>•</span>
                <FileTextOutlined />
                <span title={doc?.filename} style={{ 
                  maxWidth: 100, 
                  overflow: 'hidden', 
                  textOverflow: 'ellipsis', 
                  whiteSpace: 'nowrap', 
                  display: 'inline-block' 
                }}>
                  {doc?.filename}
                </span>
              </Space>
            </Tag>
          );
        }}
      >
        {Object.entries(groupedDocuments).map(([kbName, docs]) => (
          <OptGroup key={kbName} label={
            <Space>
              <DatabaseOutlined />
              <span>{kbName}</span>
              <Tag size="small" color="blue">{docs.length}</Tag>
            </Space>
          }>
            {docs.map(doc => (
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
          </OptGroup>
        ))}
      </Select>
      
      {value.length > 0 && (
        <div style={{ marginTop: 4, fontSize: '12px', color: '#666', textAlign: 'right' }}>
          已选择 {value.length} 个文档，来自 {[...new Set(getSelectedDocuments().map(d => d.knowledgeBaseId))].length} 个知识库
        </div>
      )}
    </div>
  );
};

export default DirectDocumentSelector;