import React, { useEffect, useState } from 'react'
import { Table, Button, Tag, Space, Card, Statistic, Row, Col, Popconfirm, message } from 'antd'
import api from '../api'

export default function ContainerList() {
  const [containers, setContainers] = useState([])
  const [stats, setStats] = useState({})
  const [loading, setLoading] = useState(false)

  const fetchData = async () => {
    setLoading(true)
    try {
      const [cRes, sRes] = await Promise.all([
        api.get('/api/admin/containers'),
        api.get('/api/admin/stats'),
      ])
      setContainers(cRes.data?.data || [])
      setStats(sRes.data?.data || {})
    } catch {
      message.error('加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchData() }, [])

  const stopContainer = async (uid) => {
    try {
      await api.post(`/api/admin/containers/${uid}/stop`)
      message.success('已停止')
      fetchData()
    } catch {
      message.error('操作失败')
    }
  }

  const removeContainer = async (uid) => {
    try {
      await api.post(`/api/admin/containers/${uid}/remove`)
      message.success('已删除')
      fetchData()
    } catch {
      message.error('操作失败')
    }
  }

  const columns = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 80 },
    { title: '容器名', dataIndex: 'containerName', key: 'containerName' },
    {
      title: '状态', dataIndex: 'status', key: 'status',
      render: s => <Tag color={s === 'RUNNING' ? 'green' : s === 'STOPPED' ? 'orange' : 'red'}>{s}</Tag>
    },
    { title: '端口', dataIndex: 'hostPort', key: 'hostPort', width: 80 },
    {
      title: '启动时间', dataIndex: 'startedAt', key: 'startedAt',
      render: t => t ? new Date(t).toLocaleString('zh-CN') : '-'
    },
    {
      title: '操作', key: 'action', width: 160,
      render: (_, r) => (
        <Space>
          {r.status === 'RUNNING' && (
            <Popconfirm title="确认停止?" onConfirm={() => stopContainer(r.userId)}>
              <Button size="small" danger>停止</Button>
            </Popconfirm>
          )}
          <Popconfirm title="确认删除容器?" onConfirm={() => removeContainer(r.userId)}>
            <Button size="small" danger type="primary">删除</Button>
          </Popconfirm>
        </Space>
      )
    },
  ]

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}><Card><Statistic title="总数" value={stats.total ?? '-'} /></Card></Col>
        <Col span={8}><Card><Statistic title="运行中" value={stats.running ?? '-'} valueStyle={{ color: '#3f8600' }} /></Card></Col>
        <Col span={8}><Card><Statistic title="已停止" value={stats.stopped ?? '-'} valueStyle={{ color: '#cf1322' }} /></Card></Col>
      </Row>
      <Table
        columns={columns}
        dataSource={containers}
        rowKey="userId"
        loading={loading}
        pagination={{ pageSize: 20 }}
      />
    </div>
  )
}
