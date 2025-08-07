import React, { useState } from 'react';
import { Drawer, Button } from 'antd';
import { MessageOutlined } from '@ant-design/icons';
import SessionHistory from '@/components/SessionHistory/SessionHistory';
import './GlobalSidebar.css';

interface GlobalSidebarProps {
  onSessionSelect: (sessionId: string) => void;
  onNewSession: () => void;
  selectedSessionId?: string;
  onSessionTitleUpdate?: (sessionId: string, newTitle: string) => void;
}

const GlobalSidebar: React.FC<GlobalSidebarProps> = ({
  onSessionSelect,
  onNewSession,
  selectedSessionId,
  onSessionTitleUpdate
}) => {
  console.log('GlobalSidebar rendered with selectedSessionId:', selectedSessionId);
  const [visible, setVisible] = useState(false);

  const showDrawer = () => {
    setVisible(true);
  };

  const onClose = () => {
    setVisible(false);
  };

  const handleSessionSelect = (sessionId: string) => {
    onSessionSelect(sessionId);
    setVisible(false); // 选择会话后关闭侧边栏
  };

  const handleNewSession = () => {
    onNewSession();
    setVisible(false); // 创建新会话后关闭侧边栏
  };

  return (
    <>
      {/* 触发按钮 */}
      <Button
        type="text"
        icon={<MessageOutlined />}
        onClick={showDrawer}
        className="global-sidebar-trigger"
        size="large"
      >
      </Button>

      {/* 侧边栏抽屉 */}
      <Drawer
        title="会话管理"
        placement="left"
        onClose={onClose}
        open={visible}
        width={320}
        className="global-sidebar-drawer"
        styles={{ body: { padding: 0 } }}
      >
        <SessionHistory
          onSessionSelect={handleSessionSelect}
          onNewSession={handleNewSession}
          selectedSessionId={selectedSessionId}
          onSessionTitleUpdate={onSessionTitleUpdate}
        />
      </Drawer>
    </>
  );
};

export default GlobalSidebar;
