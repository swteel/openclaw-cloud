import React, { useEffect, useState } from 'react'
import { Card, Button, Tag, Space, Typography, Layout, message, List } from 'antd'
import { useNavigate } from 'react-router-dom'
import { PoweroffOutlined, ArrowRightOutlined, LogoutOutlined } from '@ant-design/icons'
import api from '../api'

const { Header, Content } = Layout
const { Title, Text } = Typography

export default function Dashboard({ username, onLogout }) {
  const [containers, setContainers] = useState([])
  const [loading, setLoading] = useState(false)
  const [starting, setStarting] = useState({})
  const navigate = useNavigate()

  const fetchContainers = async () => {
    setLoading(true)
    try {
      const res = await api.get('/api/containers/my/all')
      setContainers(res.data?.data || [])
    } catch {
      // no containers yet
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchContainers() }, [])

  const startContainer = async (containerName) => {
    setStarting(prev => ({ ...prev, [containerName]: true }))
    try {
      await api.post('/api/containers/my/start')
      message.success('容器启动中...')
      setTimeout(fetchContainers, 2000)
    } catch {
      message.error('启动失败')
    } finally {
      setStarting(prev => ({ ...prev, [containerName]: false }))
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
      <Content style={{ padding: 40, maxWidth: 700, margin: '40px auto', width: '100%' }}>
        <Title level={4}>我的实例</Title>
        <Card loading={loading}>
          {containers.length > 0 ? (
            <List
              dataSource={containers}
              renderItem={container => (
                <List.Item
                  key={container.containerName}
                  actions={[
                    container.status === 'STOPPED' && (
                      <Button
                        key="start"
                        type="primary"
                        icon={<PoweroffOutlined />}
                        loading={!!starting[container.containerName]}
                        onClick={() => startContainer(container.containerName)}
                      >
                        启动
                      </Button>
                    ),
                    container.status === 'RUNNING' && (
                      <Button
                        key="enter"
                        type="primary"
                        icon={<ArrowRightOutlined />}
                        onClick={() => navigate('/workbench', { state: { containerName: container.containerName } })}
                      >
                        进入工作台
                      </Button>
                    ),
                  ].filter(Boolean)}
                >
                  <List.Item.Meta
                    title={<Text>{container.containerName}</Text>}
                    description={
                      <Space>
                        <Tag color={container.status === 'RUNNING' ? 'green' : 'orange'}>{container.status}</Tag>
                        {container.startedAt && (
                          <Text type="secondary">启动时间：{new Date(container.startedAt).toLocaleString('zh-CN')}</Text>
                        )}
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          ) : (
            <Text type="secondary">暂无容器，请联系管理员</Text>
          )}
          <div style={{ marginTop: 16 }}>
            <Button onClick={fetchContainers}>刷新</Button>
          </div>
        </Card>
      </Content>
    </Layout>
  )
}
