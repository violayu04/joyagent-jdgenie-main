import React, { useState, useEffect } from 'react';
import {
  Card,
  List,
  Button,
  Modal,
  Form,
  Input,
  message,
  Empty,
  Tag,
  Tooltip,
  Space,
  Upload,
  Progress,
  Divider
} from 'antd';
import {
  PlusOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  UploadOutlined,
  SearchOutlined,
  DeleteOutlined,
  EyeOutlined,
  EditOutlined,
  BlockOutlined
} from '@ant-design/icons';
import { useAuth } from '../../contexts/AuthContext';
import type { KnowledgeBase, CreateKnowledgeBaseRequest, SearchResult } from '../../types/knowledgebase';
import './KnowledgeBaseManager.css';

const { TextArea } = Input;

interface KnowledgeBaseManagerProps {
  onKnowledgeBaseSelect?: (knowledgeBase: KnowledgeBase) => void;
  selectedKnowledgeBaseId?: number;
}

const KnowledgeBaseManager: React.FC<KnowledgeBaseManagerProps> = ({
  onKnowledgeBaseSelect,
  selectedKnowledgeBaseId
}) => {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [uploadModalVisible, setUploadModalVisible] = useState(false);
  const [searchModalVisible, setSearchModalVisible] = useState(false);
  const [detailsModalVisible, setDetailsModalVisible] = useState(false);
  const [selectedKB, setSelectedKB] = useState<KnowledgeBase | null>(null);
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);
  const [documents, setDocuments] = useState<any[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileDescription, setFileDescription] = useState('');
  const [editingDocumentId, setEditingDocumentId] = useState<string | null>(null);
  const [editingFilename, setEditingFilename] = useState('');
  const [chunksModalVisible, setChunksModalVisible] = useState(false);
  const [chunks, setChunks] = useState<any[]>([]);
  const [chunksLoading, setChunksLoading] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState<any>(null);
  const [chunksPagination, setChunksPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });
  const [chunksSearch, setChunksSearch] = useState('');
  
  const [createForm] = Form.useForm();
  const [searchForm] = Form.useForm();
  const [uploadForm] = Form.useForm();
  const { token } = useAuth();

  useEffect(() => {
    loadKnowledgeBases();
  }, []);

  const loadKnowledgeBases = async () => {
    if (!token) return;
    
    setLoading(true);
    try {
      const response = await fetch('/api/knowledge-base', {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (response.ok) {
        const data = await response.json();
        setKnowledgeBases(data);
      } else {
        message.error('加载知识库列表失败');
      }
    } catch (error) {
      console.error('Failed to load knowledge bases:', error);
      message.error('网络错误');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateKnowledgeBase = async (values: CreateKnowledgeBaseRequest) => {
    try {
      const response = await fetch('/api/knowledge-base', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(values),
      });

      if (response.ok) {
        const newKB = await response.json();
        setKnowledgeBases([newKB, ...knowledgeBases]);
        setCreateModalVisible(false);
        createForm.resetFields();
        message.success('知识库创建成功');
      } else {
        const errorData = await response.json();
        message.error(errorData.message || '创建失败');
      }
    } catch (error) {
      console.error('Failed to create knowledge base:', error);
      message.error('网络错误');
    }
  };

  const handleUploadDocument = async () => {
    if (!selectedKB || !selectedFile) return;

    const formData = new FormData();
    formData.append('file', selectedFile);
    if (fileDescription) {
      formData.append('description', fileDescription);
    }

    try {
      setUploadProgress(0);
      const response = await fetch(`/api/knowledge-base/${selectedKB.id}/documents`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        body: formData,
      });

      if (response.ok) {
        setUploadProgress(100);
        const result = await response.json();
        console.log('Upload successful:', result);
        message.success('文档上传成功，正在进行向量化处理...');
        setUploadModalVisible(false);
        setSelectedFile(null);
        setFileDescription('');
        uploadForm.resetFields();
        setUploadProgress(0); // 重置进度
        loadKnowledgeBases(); // 刷新列表以更新文档数量
      } else {
        let errorMessage = '上传失败';
        try {
          const errorData = await response.json();
          errorMessage = errorData.message || `HTTP ${response.status}: ${response.statusText}`;
        } catch {
          errorMessage = `HTTP ${response.status}: ${response.statusText}`;
        }
        console.error('Upload failed:', response.status, response.statusText, errorMessage);
        message.error(errorMessage);
      }
    } catch (error) {
      console.error('Failed to upload document:', error);
      message.error('网络错误');
    }
  };

  const handleSearch = async (values: { query: string; topK?: number }) => {
    if (!selectedKB) return;

    setSearchLoading(true);
    try {
      const response = await fetch(`/api/knowledge-base/${selectedKB.id}/search`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          query: values.query,
          topK: values.topK || 10
        }),
      });

      if (response.ok) {
        const results = await response.json();
        setSearchResults(results);
      } else {
        const errorData = await response.json();
        message.error(errorData.message || '搜索失败');
      }
    } catch (error) {
      console.error('Failed to search:', error);
      message.error('网络错误');
    } finally {
      setSearchLoading(false);
    }
  };

  const handleViewDetails = async (kb: KnowledgeBase) => {
    setSelectedKB(kb);
    setDetailsModalVisible(true);
    
    try {
      const response = await fetch(`/api/knowledge-base/${kb.id}/documents`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (response.ok) {
        const docs = await response.json();
        setDocuments(docs);
      } else {
        const errorData = await response.json();
        message.error(errorData.message || '加载文档列表失败');
        setDocuments([]);
      }
    } catch (error) {
      console.error('Failed to load documents:', error);
      message.error('网络错误');
      setDocuments([]);
    }
  };

  const handleDeleteDocument = async (document: any) => {
    // Simple confirm dialog instead of Modal.confirm for testing
    const confirmed = window.confirm(`确定要删除文档"${document.filename}"吗？此操作不可撤销。`);
    
    if (!confirmed) {
      return;
    }
    
    console.log('Delete document confirmed:', document);
    console.log('Document ID:', document.documentId);
    console.log('Token:', token ? 'exists' : 'missing');
    
    try {
      console.log('Making delete request to:', `/api/knowledge-base/documents/${document.documentId}`);
      const response = await fetch(`/api/knowledge-base/documents/${document.documentId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      console.log('Delete response status:', response.status);
      console.log('Delete response ok:', response.ok);
      
      if (response.ok) {
        console.log('Document deleted successfully');
        message.success('文档删除成功');
        // 重新加载文档列表
        if (selectedKB) {
          handleViewDetails(selectedKB);
        }
        // 重新加载知识库列表以更新文档数量
        loadKnowledgeBases();
      } else {
        const errorData = await response.json();
        console.error('Delete failed with error:', errorData);
        message.error(errorData.message || '删除文档失败');
      }
    } catch (error) {
      console.error('Failed to delete document:', error);
      message.error('网络错误');
    }
  };

  const handleRenameDocument = async (document: any) => {
    const newFilename = prompt('请输入新的文件名:', document.filename);
    
    if (!newFilename || newFilename.trim() === '' || newFilename === document.filename) {
      return;
    }
    
    try {
      console.log('Renaming document:', document.documentId, 'to:', newFilename);
      const response = await fetch(`/api/knowledge-base/documents/${document.documentId}`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ filename: newFilename.trim() }),
      });

      console.log('Rename response status:', response.status);
      console.log('Rename response ok:', response.ok);
      
      if (response.ok) {
        console.log('Document renamed successfully');
        message.success('文档重命名成功');
        // 重新加载文档列表
        if (selectedKB) {
          handleViewDetails(selectedKB);
        }
      } else {
        const errorData = await response.json();
        console.error('Rename failed with error:', errorData);
        message.error(errorData.message || '重命名失败');
      }
    } catch (error) {
      console.error('Failed to rename document:', error);
      message.error('网络错误');
    }
  };

  const handleRenameKnowledgeBase = async (kb: KnowledgeBase) => {
    const newName = prompt('请输入新的知识库名称:', kb.name);
    
    if (!newName || newName.trim() === '' || newName === kb.name) {
      console.log('Rename cancelled: no new name or same name');
      return;
    }
    
    try {
      console.log('Renaming knowledge base:', kb.id, 'to:', newName);
      console.log('Token:', token ? 'exists' : 'missing');
      console.log('URL:', `/api/knowledge-base/${kb.id}`);
      console.log('Request body:', { name: newName.trim() });
      
      const response = await fetch(`/api/knowledge-base/${kb.id}`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name: newName.trim() }),
      });

      console.log('KB Rename response status:', response.status);
      console.log('KB Rename response ok:', response.ok);
      console.log('KB Rename response headers:', Object.fromEntries(response.headers.entries()));
      
      if (response.ok) {
        const responseData = await response.json();
        console.log('Knowledge base renamed successfully:', responseData);
        message.success('知识库重命名成功');
        // 重新加载知识库列表
        loadKnowledgeBases();
      } else {
        let errorMessage;
        try {
          const errorData = await response.json();
          console.error('KB Rename failed with error data:', errorData);
          errorMessage = errorData.message || `HTTP ${response.status}: ${response.statusText}`;
        } catch (parseError) {
          console.error('Failed to parse error response:', parseError);
          errorMessage = `HTTP ${response.status}: ${response.statusText}`;
        }
        message.error(errorMessage);
      }
    } catch (error) {
      console.error('Failed to rename knowledge base:', error);
      message.error('网络错误: ' + error.message);
    }
  };

  const handleDeleteKnowledgeBase = async (kb: KnowledgeBase) => {
    // Simple confirm dialog instead of Modal.confirm for testing
    const confirmed = window.confirm(`确定要删除知识库"${kb.name}"吗？此操作不可撤销。`);
    
    if (!confirmed) {
      return;
    }
    
    try {
      console.log('Deleting knowledge base:', kb.name, 'ID:', kb.id);
      const response = await fetch(`/api/knowledge-base/${kb.id}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      console.log('KB Delete response status:', response.status);
      console.log('KB Delete response ok:', response.ok);

      if (response.ok) {
        setKnowledgeBases(knowledgeBases.filter(k => k.id !== kb.id));
        message.success('知识库删除成功');
        console.log('Knowledge base deleted successfully');
      } else {
        const errorData = await response.json();
        console.error('KB Delete failed with error:', errorData);
        message.error(errorData.message || '删除失败');
      }
    } catch (error) {
      console.error('Failed to delete knowledge base:', error);
      message.error('网络错误');
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('zh-CN');
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return 'success';
      case 'PROCESSING': return 'processing';
      case 'FAILED': return 'error';
      default: return 'default';
    }
  };

  // 加载文档chunks
  const loadDocumentChunks = async (document: any, page: number = 1, search: string = '') => {
    if (!token || !selectedKB) return;
    
    setChunksLoading(true);
    try {
      const searchParams = new URLSearchParams({
        page: (page - 1).toString(),
        size: chunksPagination.pageSize.toString(),
      });
      
      if (search) {
        searchParams.append('search', search);
      }
      
      const response = await fetch(
        `/api/knowledge-base/${selectedKB.id}/documents/${document.documentId}/chunks?${searchParams}`,
        {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        }
      );

      if (response.ok) {
        const data = await response.json();
        setChunks(data.chunks);
        setChunksPagination(prev => ({
          ...prev,
          current: page,
          total: data.totalElements
        }));
      } else {
        message.error('获取文档分块失败');
      }
    } catch (error) {
      console.error('Failed to load document chunks:', error);
      message.error('网络错误');
    } finally {
      setChunksLoading(false);
    }
  };

  // 打开chunks预览
  const handleViewChunks = (document: any) => {
    setSelectedDocument(document);
    setChunksModalVisible(true);
    setChunksSearch('');
    setChunksPagination(prev => ({ ...prev, current: 1 }));
    loadDocumentChunks(document, 1, '');
  };

  // 处理chunks搜索
  const handleChunksSearch = (value: string) => {
    setChunksSearch(value);
    loadDocumentChunks(selectedDocument, 1, value);
  };

  // 处理chunks分页
  const handleChunksPagination = (page: number) => {
    loadDocumentChunks(selectedDocument, page, chunksSearch);
  };

  return (
    <div className="knowledge-base-manager">
      <div className="kb-header">
        <div className="kb-title">
          <DatabaseOutlined />
          <span>知识库管理</span>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreateModalVisible(true)}
        >
          创建知识库
        </Button>
      </div>

      {knowledgeBases.length === 0 ? (
        <Empty
          description="暂无知识库"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        >
          <Button type="primary" onClick={() => setCreateModalVisible(true)}>
            创建知识库
          </Button>
        </Empty>
      ) : (
        <List
          loading={loading}
          grid={{ gutter: 16, xs: 1, sm: 2, md: 2, lg: 3, xl: 3, xxl: 4 }}
          dataSource={knowledgeBases}
          renderItem={(kb) => (
            <List.Item>
              <Card
                className={`kb-card ${selectedKnowledgeBaseId === kb.id ? 'selected' : ''}`}
                actions={[
                  <Tooltip title="上传文档">
                    <UploadOutlined
                      onClick={() => {
                        setSelectedKB(kb);
                        setUploadModalVisible(true);
                      }}
                    />
                  </Tooltip>,
                  <Tooltip title="语义搜索">
                    <SearchOutlined
                      onClick={() => {
                        setSelectedKB(kb);
                        setSearchModalVisible(true);
                      }}
                    />
                  </Tooltip>,
                  <Tooltip title="查看详情">
                    <EyeOutlined
                      onClick={() => handleViewDetails(kb)}
                    />
                  </Tooltip>,
                  <Tooltip title="重命名">
                    <EditOutlined
                      onClick={() => handleRenameKnowledgeBase(kb)}
                      style={{ color: '#1890ff' }}
                    />
                  </Tooltip>,
                  <Tooltip title="删除">
                    <DeleteOutlined
                      onClick={() => handleDeleteKnowledgeBase(kb)}
                      style={{ color: '#ff4d4f' }}
                    />
                  </Tooltip>
                ]}
                onClick={() => {
                  if (onKnowledgeBaseSelect) {
                    onKnowledgeBaseSelect(kb);
                  }
                }}
              >
                <Card.Meta
                  avatar={<DatabaseOutlined className="kb-icon" />}
                  title={
                    <div className="kb-card-title">
                      <span>{kb.name}</span>
                      <Tag color="blue">{kb.documentCount} 文档</Tag>
                    </div>
                  }
                  description={
                    <div className="kb-description">
                      <div>{kb.description || '暂无描述'}</div>
                      <div className="kb-meta">
                        <small>创建于 {formatDate(kb.createdAt)}</small>
                      </div>
                    </div>
                  }
                />
              </Card>
            </List.Item>
          )}
        />
      )}

      {/* 创建知识库弹窗 */}
      <Modal
        title="创建知识库"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          createForm.resetFields();
        }}
        footer={null}
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={handleCreateKnowledgeBase}
        >
          <Form.Item
            name="name"
            label="知识库名称"
            rules={[{ required: true, message: '请输入知识库名称' }]}
          >
            <Input placeholder="请输入知识库名称" />
          </Form.Item>
          
          <Form.Item
            name="description"
            label="描述"
          >
            <TextArea 
              rows={3}
              placeholder="请输入知识库描述（可选）"
            />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                创建
              </Button>
              <Button onClick={() => {
                setCreateModalVisible(false);
                createForm.resetFields();
              }}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 上传文档弹窗 */}
      <Modal
        title={`上传文档到"${selectedKB?.name}"`}
        open={uploadModalVisible}
        onCancel={() => {
          setUploadModalVisible(false);
          setSelectedFile(null);
          setFileDescription('');
          uploadForm.resetFields();
        }}
        footer={null}
      >
        <Form
          form={uploadForm}
          layout="vertical"
          onFinish={handleUploadDocument}
        >
          <Form.Item
            name="file"
            label="选择文件"
            rules={[{ required: true, message: '请选择要上传的文件' }]}
          >
            <Upload.Dragger
              name="file"
              multiple={false}
              beforeUpload={(file) => {
                setSelectedFile(file);
                uploadForm.setFieldsValue({ file: file });
                return false;
              }}
              accept=".pdf,.docx,.txt,.md,.json,.csv"
              fileList={selectedFile ? [{ 
                uid: '1', 
                name: selectedFile.name, 
                status: 'done' as const,
                url: '#'
              }] : []}
              onRemove={() => {
                setSelectedFile(null);
                uploadForm.setFieldsValue({ file: undefined });
              }}
            >
              <p className="ant-upload-drag-icon">
                <FileTextOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽文件到此处选择</p>
              <p className="ant-upload-hint">
                支持 PDF, DOCX, TXT, MD, JSON, CSV 格式
              </p>
            </Upload.Dragger>
          </Form.Item>

          <Form.Item
            name="description"
            label="文档描述（可选）"
          >
            <TextArea
              rows={3}
              placeholder="请输入文档描述或备注信息"
              value={fileDescription}
              onChange={(e) => setFileDescription(e.target.value)}
            />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button 
                type="primary" 
                htmlType="submit"
                disabled={!selectedFile || uploadProgress > 0}
                loading={uploadProgress > 0 && uploadProgress < 100}
              >
                确认上传
              </Button>
              <Button onClick={() => {
                setUploadModalVisible(false);
                setSelectedFile(null);
                setFileDescription('');
                uploadForm.resetFields();
              }}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
        
        {uploadProgress > 0 && uploadProgress < 100 && (
          <Progress percent={uploadProgress} style={{ marginTop: 16 }} />
        )}
      </Modal>

      {/* 搜索弹窗 */}
      <Modal
        title={`在"${selectedKB?.name}"中搜索`}
        open={searchModalVisible}
        onCancel={() => {
          setSearchModalVisible(false);
          setSearchResults([]);
          searchForm.resetFields();
        }}
        width={800}
        footer={null}
      >
        <Form
          form={searchForm}
          layout="vertical"
          onFinish={handleSearch}
        >
          <Form.Item
            name="query"
            label="搜索内容"
            rules={[{ required: true, message: '请输入搜索内容' }]}
          >
            <Input.Search
              placeholder="请输入要搜索的内容"
              enterButton="搜索"
              size="large"
              loading={searchLoading}
              onSearch={(value) => {
                if (value.trim()) {
                  handleSearch({ query: value.trim() });
                }
              }}
            />
          </Form.Item>
        </Form>

        {searchResults.length > 0 && (
          <>
            <Divider>搜索结果 ({searchResults.length})</Divider>
            <List
              dataSource={searchResults}
              renderItem={(result, index) => (
                <List.Item>
                  <List.Item.Meta
                    avatar={<span className="search-rank">#{index + 1}</span>}
                    title={
                      <div className="search-result-title">
                        <span>{result.filename}</span>
                        <Tag color="green">相似度: {(result.score * 100).toFixed(1)}%</Tag>
                      </div>
                    }
                    description={
                      <div className="search-result-content">
                        <div className="content-preview">
                          {result.content.length > 200 
                            ? result.content.substring(0, 200) + '...'
                            : result.content
                          }
                        </div>
                        <div className="search-result-meta">
                          <small>
                            第 {result.chunkIndex + 1} 段 • {result.tokenCount} 个词
                          </small>
                        </div>
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </>
        )}
      </Modal>

      {/* 知识库详情弹窗 */}
      <Modal
        title={`知识库详情: ${selectedKB?.name || ''}`}
        open={detailsModalVisible}
        onCancel={() => {
          setDetailsModalVisible(false);
          setDocuments([]);
          setSelectedKB(null);
        }}
        width={800}
        footer={[
          <Button key="close" onClick={() => {
            setDetailsModalVisible(false);
            setDocuments([]);
            setSelectedKB(null);
          }}>
            关闭
          </Button>
        ]}
      >
        <div className="kb-details">
          <div className="kb-info" style={{ marginBottom: 16 }}>
            <p><strong>描述:</strong> {selectedKB?.description || '无描述'}</p>
            <p><strong>文档数量:</strong> {selectedKB?.documentCount || 0}</p>
            <p><strong>创建时间:</strong> {selectedKB ? formatDate(selectedKB.createdAt) : ''}</p>
            <p><strong>更新时间:</strong> {selectedKB ? formatDate(selectedKB.updatedAt) : ''}</p>
          </div>
          
          <Divider>文档列表</Divider>
          
          {documents.length === 0 ? (
            <Empty description="该知识库暂无文档" />
          ) : (
            <List
              dataSource={documents}
              renderItem={(doc: any) => (
                <List.Item
                  actions={[
                    <Tooltip title="查看分块">
                      <Button
                        type="text"
                        icon={<BlockOutlined />}
                        onClick={() => handleViewChunks(doc)}
                        size="small"
                        disabled={doc.status !== 'COMPLETED'}
                      />
                    </Tooltip>,
                    <Tooltip title="重命名文档">
                      <Button
                        type="text"
                        icon={<EditOutlined />}
                        onClick={() => handleRenameDocument(doc)}
                        size="small"
                      />
                    </Tooltip>,
                    <Tooltip title="删除文档">
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => handleDeleteDocument(doc)}
                        size="small"
                      />
                    </Tooltip>
                  ]}
                >
                  <List.Item.Meta
                    avatar={<FileTextOutlined />}
                    title={
                      <div className="document-title">
                        <span>{doc.filename}</span>
                        <Tag color={getStatusColor(doc.status)} style={{ marginLeft: 8 }}>
                          {doc.status}
                        </Tag>
                      </div>
                    }
                    description={
                      <div className="document-description">
                        <div>
                          <strong>类型:</strong> {doc.contentType || '未知'}
                          <strong style={{ marginLeft: 16 }}>大小:</strong> {(doc.fileSize / 1024).toFixed(1)} KB
                        </div>
                        <div style={{ marginTop: 4 }}>
                          <strong>上传时间:</strong> {formatDate(doc.createdAt)}
                        </div>
                        {doc.errorMessage && (
                          <div style={{ marginTop: 4, color: '#ff4d4f' }}>
                            <strong>错误信息:</strong> {doc.errorMessage}
                          </div>
                        )}
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          )}
        </div>
      </Modal>

      {/* 文档分块预览弹窗 */}
      <Modal
        title={
          <div>
            <BlockOutlined style={{ marginRight: 8 }} />
            文档分块预览: {selectedDocument?.filename}
          </div>
        }
        open={chunksModalVisible}
        onCancel={() => {
          setChunksModalVisible(false);
          setChunks([]);
          setSelectedDocument(null);
          setChunksSearch('');
        }}
        footer={null}
        width={800}
        styles={{ body: { maxHeight: '70vh', overflowY: 'auto' } }}
      >
        <div style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="搜索分块内容..."
            allowClear
            onSearch={handleChunksSearch}
            style={{ marginBottom: 16 }}
          />
          <div style={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center',
            marginBottom: 16,
            fontSize: '14px',
            color: '#666'
          }}>
            <span>共 {chunksPagination.total} 个分块</span>
            <span>
              页面大小: {chunksPagination.pageSize} • 
              当前: {chunksPagination.current}/{Math.ceil(chunksPagination.total / chunksPagination.pageSize)}
            </span>
          </div>
        </div>

        <List
          loading={chunksLoading}
          dataSource={chunks}
          renderItem={(chunk: any, index) => (
            <List.Item 
              style={{ 
                border: '1px solid #f0f0f0', 
                borderRadius: 6, 
                marginBottom: 12,
                padding: 16
              }}
            >
              <List.Item.Meta
                avatar={
                  <div style={{
                    backgroundColor: '#1890ff',
                    color: 'white',
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '14px',
                    fontWeight: 'bold'
                  }}>
                    {chunk.chunkIndex + 1}
                  </div>
                }
                title={
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>第 {chunk.chunkIndex + 1} 块</span>
                    <div>
                      <Tag color="blue">{chunk.tokenCount} tokens</Tag>
                      <Tag color="green">
                        {chunk.startPos}-{chunk.endPos}
                      </Tag>
                    </div>
                  </div>
                }
                description={
                  <div>
                    <div style={{ 
                      backgroundColor: '#f9f9f9', 
                      padding: 12, 
                      borderRadius: 4,
                      marginTop: 8,
                      lineHeight: 1.6,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word'
                    }}>
                      {chunk.content}
                    </div>
                    <div style={{ 
                      marginTop: 8, 
                      fontSize: '12px', 
                      color: '#999',
                      textAlign: 'right'
                    }}>
                      创建时间: {formatDate(chunk.createdAt)}
                    </div>
                  </div>
                }
              />
            </List.Item>
          )}
          pagination={{
            current: chunksPagination.current,
            pageSize: chunksPagination.pageSize,
            total: chunksPagination.total,
            onChange: handleChunksPagination,
            showSizeChanger: false,
            showQuickJumper: true,
            showTotal: (total, range) => `显示 ${range[0]}-${range[1]} / 共 ${total} 个分块`
          }}
        />
      </Modal>
    </div>
  );
};

export default KnowledgeBaseManager;