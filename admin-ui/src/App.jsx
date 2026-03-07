import React, { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, Link, useLocation } from 'react-router-dom'
import { Layout, Menu, Button, Typography, Space, Spin } from 'antd'
import {
  ContainerOutlined,
  UserOutlined,
  SettingOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import Login from './pages/Login'
import ContainerList from './pages/ContainerList'
import UserList from './pages/UserList'
import PlatformConfig from './pages/PlatformConfig'
import api from './api'

const { Header, Sider, Content } = Layout
const { Text } = Typography

function AdminLayout({ username, onLogout }) {
  const location = useLocation()
  const selectedKey = location.pathname.replace('/admin', '') || '/containers'

  const menuItems = [
    { key: '/containers', icon: <ContainerOutlined />, label: <Link to="/admin/containers">容器列表</Link> },
    { key: '/users', icon: <UserOutlined />, label: <Link to="/admin/users">用户列表</Link> },
    { key: '/config', icon: <SettingOutlined />, label: <Link to="/admin/config">平台配置</Link> },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={200} theme="dark">
        <div style={{ padding: '16px', color: '#fff', fontWeight: 'bold', fontSize: 16 }}>
          OpenClaw Cloud
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey.replace('/admin', '') || '/containers']}
          items={menuItems}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }}>
          <Space>
            <Text>{username}</Text>
            <Button icon={<LogoutOutlined />} onClick={onLogout}>退出</Button>
          </Space>
        </Header>
        <Content style={{ margin: '24px', background: '#fff', borderRadius: 8, padding: 24 }}>
          <Routes>
            <Route path="/admin" element={<Navigate to="/admin/containers" replace />} />
            <Route path="/admin/containers" element={<ContainerList />} />
            <Route path="/admin/users" element={<UserList />} />
            <Route path="/admin/config" element={<PlatformConfig />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default function App() {
  const [authed, setAuthed] = useState(null)
  const [username, setUsername] = useState('')

  useEffect(() => {
    fetch('/api/admin/stats', { credentials: 'include' })
      .then(res => setAuthed(res.ok))
      .catch(() => setAuthed(false))
  }, [])

  const handleLogout = async () => {
    await api.get('/portal/logout')
    setAuthed(false)
  }

  if (authed === null) return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <Spin size="large" />
    </div>
  )

  return (
    <BrowserRouter>
      {!authed
        ? <Routes>
            <Route path="*" element={<Login onLogin={(u) => { setUsername(u); setAuthed(true) }} />} />
          </Routes>
        : <AdminLayout username={username} onLogout={handleLogout} />
      }
    </BrowserRouter>
  )
}
