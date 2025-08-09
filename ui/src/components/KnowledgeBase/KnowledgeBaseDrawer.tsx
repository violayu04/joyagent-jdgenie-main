import React, { useState } from 'react';
import { Drawer, Button } from 'antd';
import { DatabaseOutlined } from '@ant-design/icons';
import KnowledgeBaseManager from './KnowledgeBaseManager';
import './KnowledgeBaseDrawer.css';

interface KnowledgeBaseDrawerProps {
  onKnowledgeBaseSelect?: (kb: any) => void;
  selectedKnowledgeBaseId?: number;
}

const KnowledgeBaseDrawer: React.FC<KnowledgeBaseDrawerProps> = ({
  onKnowledgeBaseSelect,
  selectedKnowledgeBaseId
}) => {
  const [visible, setVisible] = useState(false);

  const showDrawer = () => {
    setVisible(true);
  };

  const onClose = () => {
    setVisible(false);
  };

  const handleKnowledgeBaseSelect = (kb: any) => {
    onKnowledgeBaseSelect?.(kb);
    setVisible(false); // 选择知识库后关闭侧边栏
  };

  return (
    <>
      {/* 触发按钮 */}
      <Button
        type="text"
        icon={<DatabaseOutlined />}
        onClick={showDrawer}
        className="knowledge-base-drawer-trigger"
        size="large"
        title="知识库管理"
      >
      </Button>

      {/* 知识库管理抽屉 */}
      <Drawer
        title="知识库管理"
        placement="left"
        onClose={onClose}
        open={visible}
        width={800} // 更大的宽度以容纳知识库管理界面
        className="knowledge-base-drawer"
        styles={{ body: { padding: 0 } }}
      >
        <KnowledgeBaseManager
          onKnowledgeBaseSelect={handleKnowledgeBaseSelect}
          selectedKnowledgeBaseId={selectedKnowledgeBaseId}
        />
      </Drawer>
    </>
  );
};

export default KnowledgeBaseDrawer;