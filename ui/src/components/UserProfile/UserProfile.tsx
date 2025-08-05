import React from 'react';
import { Dropdown, Avatar, Button, Space } from 'antd';
import { UserOutlined, LogoutOutlined, SettingOutlined } from '@ant-design/icons';
import { useAuth } from '../../contexts/AuthContext';
import type { MenuProps } from 'antd';

const UserProfile: React.FC = () => {
  const { user, logout } = useAuth();

  const handleLogout = () => {
    logout();
  };

  const items: MenuProps['items'] = [
    {
      key: '1',
      icon: <UserOutlined />,
      label: user?.username || '用户',
      disabled: true,
    },
    {
      type: 'divider',
    },
    {
      key: '2',
      icon: <SettingOutlined />,
      label: '设置',
      disabled: true, // 暂时禁用，后续可以添加设置页面
    },
    {
      key: '3',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  return (
    <Dropdown menu={{ items }} placement="bottomRight" arrow>
      <Button type="text" style={{ padding: '4px 8px' }}>
        <Space>
          <Avatar 
            size="small" 
            icon={<UserOutlined />} 
            style={{ backgroundColor: '#1890ff' }}
          />
          <span style={{ color: '#333' }}>{user?.username}</span>
        </Space>
      </Button>
    </Dropdown>
  );
};

export default UserProfile;