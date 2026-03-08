import React from 'react'
import { Layout, Space, Typography, Button } from 'antd'
import { LogoutOutlined, DashboardOutlined } from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import FilePanel from './FilePanel'
import OpenClawFrame from './OpenClawFrame'
import api from '../../api'

const { Header, Sider, Content } = Layout
const { Text } = Typography

export default function Workbench({ username, onLogout }) {
  const navigate = useNavigate()
  const location = useLocation()
  const containerName = location.state?.containerName

  const handleLogout = async () => {
    await api.get('/portal/logout')
    onLogout()
  }

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Header style={{
        background: '#001529',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 16px',
        height: 48,
        lineHeight: '48px',
        flexShrink: 0,
      }}>
        <Text style={{ color: '#fff', fontWeight: 'bold' }}>OpenClaw Cloud</Text>
        <Space>
          <Button
            size="small"
            icon={<DashboardOutlined />}
            onClick={() => navigate('/dashboard')}
            type="text"
            style={{ color: '#aaa' }}
          >
            我的实例
          </Button>
          <Text style={{ color: '#aaa' }}>{username}</Text>
          <Button
            size="small"
            icon={<LogoutOutlined />}
            onClick={handleLogout}
            type="text"
            style={{ color: '#aaa' }}
          >
            退出
          </Button>
        </Space>
      </Header>
      <Layout style={{ flex: 1, overflow: 'hidden' }}>
        <Sider
          width={260}
          style={{ background: '#fafafa', borderRight: '1px solid #e8e8e8', overflow: 'auto' }}
        >
          <FilePanel containerName={containerName} />
        </Sider>
        <Content style={{ flex: 1, overflow: 'hidden' }}>
          <OpenClawFrame />
        </Content>
      </Layout>
    </Layout>
  )
}
