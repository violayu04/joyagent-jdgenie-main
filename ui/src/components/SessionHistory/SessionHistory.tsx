import React, { useState, useEffect } from 'react';
import { List, Button, Modal, message, Empty, Tooltip } from 'antd';
import { DeleteOutlined, MessageOutlined, PlusOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { useAuth } from '../../contexts/AuthContext';
import './SessionHistory.css';

interface ChatSession {
  sessionId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
}

interface SessionHistoryProps {
  onSessionSelect: (sessionId: string) => void;
  onNewSession: () => void;
  selectedSessionId?: string;
}

const SessionHistory: React.FC<SessionHistoryProps> = ({
  onSessionSelect,
  onNewSession,
  selectedSessionId
}) => {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [deleteModalVisible, setDeleteModalVisible] = useState(false);
  const [sessionToDelete, setSessionToDelete] = useState<string | null>(null);
  const { token } = useAuth();

  useEffect(() => {
    console.log('SessionHistory component mounted, loading sessions...');
    loadSessions();
  }, []);

  const loadSessions = async () => {
    if (!token) {
      console.log('No token found, cannot load sessions');
      return;
    }
    
    console.log('Loading sessions with token:', token);
    setLoading(true);
    try {
      const response = await fetch('/api/sessions', {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      console.log('Sessions response status:', response.status);
      if (response.ok) {
        const data = await response.json();
        console.log('Sessions loaded:', data);
        setSessions(data);
      } else {
        console.error('Failed to load sessions, status:', response.status);
        message.error('加载会话历史失败');
      }
    } catch (error) {
      console.error('Failed to load sessions:', error);
      message.error('网络错误');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteConfirm = async () => {
    console.log('User clicked OK on delete confirmation');
    if (!sessionToDelete) return;
    
    setDeleteModalVisible(false);
    
    try {
      console.log('User confirmed deletion. Deleting session:', sessionToDelete, 'with token:', token ? 'Token exists' : 'No token');
      console.log('Making DELETE request to:', `/api/sessions/${sessionToDelete}`);

      const response = await fetch(`/api/sessions/${sessionToDelete}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      console.log('Delete API response received. Status:', response.status);
      console.log('Response headers:', Object.fromEntries(response.headers.entries()));

      console.log('Delete response status:', response.status);
      
      if (response.ok) {
        const responseData = await response.json();
        console.log('Delete successful! Response:', responseData);
        message.success('会话删除成功');

        // 更新本地状态，移除已删除的会话
        const updatedSessions = sessions.filter(s => s.sessionId !== sessionToDelete);
        console.log('Updating sessions list. Before:', sessions.length, 'After:', updatedSessions.length);
        setSessions(updatedSessions);

        // 如果删除的是当前选中的会话，清空选择
        if (selectedSessionId === sessionToDelete) {
          console.log('Deleted session was selected, calling onNewSession');
          onNewSession();
        }
      } else {
        const errorData = await response.json().catch(() => ({ message: '未知错误' }));
        console.error('Delete failed:', response.status, errorData);
        message.error(errorData.message || `删除失败 (${response.status})`);
      }
    } catch (error) {
      console.error('Failed to delete session:', error);
      message.error(`网络错误: ${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setSessionToDelete(null);
    }
  };

  const deleteSession = (sessionId: string) => {
    console.log('deleteSession called with sessionId:', sessionId);
    if (!token) {
      console.log('No token available for delete operation');
      return;
    }

    console.log('Showing delete confirmation modal');
    setSessionToDelete(sessionId);
    setDeleteModalVisible(true);
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const dayDiff = Math.floor(diff / (1000 * 60 * 60 * 24));

    if (dayDiff === 0) {
      return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    } else if (dayDiff === 1) {
      return '昨天';
    } else if (dayDiff < 7) {
      return `${dayDiff}天前`;
    } else {
      return date.toLocaleDateString('zh-CN');
    }
  };

  return (
    <div className="session-history">
      <div className="session-header">
        <h3>会话历史</h3>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            console.log('New session button clicked');
            onNewSession();
          }}
          size="small"
        >
          新对话
        </Button>
      </div>

      <div className="session-list">
        {sessions.length === 0 ? (
          <Empty
            description="暂无会话历史"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <List
            loading={loading}
            dataSource={sessions}
            renderItem={(session) => (
              <List.Item
                className={`session-item ${selectedSessionId === session.sessionId ? 'selected' : ''}`}
                onClick={() => {
                  console.log('Session clicked:', session.sessionId);
                  onSessionSelect(session.sessionId);
                }}
              >
                <div className="session-content">
                  <div className="session-main">
                    <div className="session-title">{session.title}</div>
                    <div className="session-meta">
                      <span className="session-time">
                        {formatDate(session.updatedAt)}
                      </span>
                      <span className="session-messages">
                        <MessageOutlined /> {session.messageCount}
                      </span>
                    </div>
                  </div>
                  <div className="session-actions">
                    <Tooltip title="删除会话">
                      <Button
                        type="text"
                        icon={<DeleteOutlined />}
                        size="small"
                        danger
                        onClick={(e) => {
                          console.log('Delete button clicked for session:', session.sessionId);
                          e.stopPropagation();
                          deleteSession(session.sessionId);
                        }}
                      />
                    </Tooltip>
                  </div>
                </div>
              </List.Item>
            )}
          />
        )}
      </div>
      // 在这里添加下面的 Modal 组件
      <Modal
        title={
          <>
            <ExclamationCircleOutlined style={{ color: 'red', marginRight: 8 }} />
            确认删除
          </>
        }
        open={deleteModalVisible}
        onOk={handleDeleteConfirm}
        onCancel={() => {
          console.log('Delete confirmation cancelled');
          setDeleteModalVisible(false);
          setSessionToDelete(null);
        }}
        okText="确认"
        cancelText="取消"
        okButtonProps={{ danger: true }}
      >
        <p>您确定要永久删除这个会话吗？此操作无法撤销。</p>
      </Modal>
    </div>
  );
};

export default SessionHistory;