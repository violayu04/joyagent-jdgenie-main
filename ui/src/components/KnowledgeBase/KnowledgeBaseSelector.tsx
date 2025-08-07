import React, { useState, useEffect } from 'react';
import { Select, Button, Tooltip, Space, Tag, Modal } from 'antd';
import { DatabaseOutlined, SettingOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { useAuth } from '../../contexts/AuthContext';
import KnowledgeBaseManager from './KnowledgeBaseManager';
import type { KnowledgeBase } from '../../types/knowledgebase';

const { Option } = Select;

interface KnowledgeBaseSelectorProps {
  value?: number;
  onChange?: (knowledgeBaseId: number | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
}

const KnowledgeBaseSelector: React.FC<KnowledgeBaseSelectorProps> = ({
  value,
  onChange,
  placeholder = "选择知识库（可选）",
  disabled = false
}) => {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(false);
  const [managerVisible, setManagerVisible] = useState(false);
  const { token } = useAuth();

  useEffect(() => {
    loadKnowledgeBases();
  }, [token]);

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
      }
    } catch (error) {
      console.error('Failed to load knowledge bases:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (selectedValue: number | undefined) => {
    onChange?.(selectedValue);
  };

  const getSelectedKB = () => {
    return knowledgeBases.find(kb => kb.id === value);
  };

  return (
    <div className="knowledge-base-selector">
      <Space>
        <Select
          value={value}
          onChange={handleChange}
          placeholder={placeholder}
          loading={loading}
          disabled={disabled}
          allowClear
          style={{ minWidth: 200 }}
          dropdownRender={menu => (
            <div>
              {menu}
              <div style={{ padding: '8px', borderTop: '1px solid #f0f0f0' }}>
                <Button
                  type="link"
                  size="small"
                  icon={<SettingOutlined />}
                  onClick={() => setManagerVisible(true)}
                  style={{ padding: 0 }}
                >
                  管理知识库
                </Button>
              </div>
            </div>
          )}
          suffixIcon={<DatabaseOutlined />}
        >
          {knowledgeBases.map(kb => (
            <Option key={kb.id} value={kb.id}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>{kb.name}</span>
                <Tag size="small" color="blue">
                  {kb.documentCount}
                </Tag>
              </div>
            </Option>
          ))}
        </Select>

        {value && (
          <Tooltip
            title={
              <div>
                <div><strong>{getSelectedKB()?.name}</strong></div>
                <div>文档数量: {getSelectedKB()?.documentCount}</div>
                <div>描述: {getSelectedKB()?.description || '无'}</div>
              </div>
            }
          >
            <InfoCircleOutlined style={{ color: '#1890ff' }} />
          </Tooltip>
        )}
      </Space>

      <Modal
        title="知识库管理"
        open={managerVisible}
        onCancel={() => setManagerVisible(false)}
        footer={null}
        width="90%"
        style={{ top: 20 }}
        bodyStyle={{ padding: 0, height: '80vh', overflow: 'auto' }}
      >
        <KnowledgeBaseManager
          onKnowledgeBaseSelect={(kb) => {
            handleChange(kb.id);
            setManagerVisible(false);
          }}
          selectedKnowledgeBaseId={value}
        />
      </Modal>
    </div>
  );
};

export default KnowledgeBaseSelector;