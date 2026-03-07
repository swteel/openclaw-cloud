import React, { useEffect, useState } from 'react'
import { Card, Button, Tag, Space, Typography, Layout, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { PoweroffOutlined, ArrowRightOutlined, LogoutOutlined } from '@ant-design/icons'
import api from '../api'

const { Header, Content } = Layout
const { Title, Text } = Typography

export default function Dashboard({ username, onLogout }) {
  const [container, setContainer] = useState(null)
  const [loading, setLoading] = useState(false)
  const [starting, setStarting] = useState(false)
  const navigate = useNavigate()

  const fetchContainer = async () => {
    setLoading(true)
    try {
      const res = await api.get('/api/containers/my')
      setContainer(res.data?.data)
    } catch {
      // no container yet
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchContainer() }, [])

  const startContainer = async () => {
    setStarting(true)
    try {
      await api.post('/api/containers/my/start')
      message.success('容器启动中...')
      setTimeout(fetchContainer, 2000)
    } catch {
      message.error('启动失败')
    } finally {
      setStarting(false)
    }
  }

  const handleLogout = async () => {
    await api.get('/portal/logout')
    onLogout()
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ background: '#001529', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px' }}>
        <Text style={{ color: '#fff', fontSize: 18, fontWeight: 'bold' }}>OpenClaw Cloud</Text>
        <Space>
          <Text style={{ color: '#aaa' }}>{username}</Text>
          <Button icon={<LogoutOutlined />} onClick={handleLogout} type="text" style={{ color: '#aaa' }}>退出</Button>
        </Space>
      </Header>
      <Content style={{ padding: 40, maxWidth: 600, margin: '40px auto', width: '100%' }}>
        <Title level={4}>我的实例</Title>
        <Card loading={loading}>
          {container ? (
            <Space direction="vertical" size="large" style={{ width: '100%' }}>
              <div>
                <Text type="secondary">容器名称：</Text>
                <Text>{container.containerName}</Text>
              </div>
              <div>
                <Text type="secondary">状态：</Text>
                <Tag color={container.status === 'RUNNING' ? 'green' : 'orange'}>{container.status}</Tag>
              </div>
              {container.startedAt && (
                <div>
                  <Text type="secondary">启动时间：</Text>
                  <Text>{new Date(container.startedAt).toLocaleString('zh-CN')}</Text>
                </div>
              )}
              <Space>
                {container.status === 'STOPPED' && (
                  <Button type="primary" icon={<PoweroffOutlined />} loading={starting} onClick={startContainer}>
                    启动容器
                  </Button>
                )}
                {container.status === 'RUNNING' && (
                  <Button type="primary" icon={<ArrowRightOutlined />} onClick={() => navigate('/workbench')}>
                    进入工作台
                  </Button>
                )}
                <Button onClick={fetchContainer}>刷新</Button>
              </Space>
            </Space>
          ) : (
            <Text type="secondary">暂无容器，请联系管理员</Text>
          )}
        </Card>
      </Content>
    </Layout>
  )
}
