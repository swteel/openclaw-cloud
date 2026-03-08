import React, { useEffect, useState } from 'react'
import { Table, Button, Tag, Space, Card, Statistic, Row, Col, Popconfirm, message, Modal, Select } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import api from '../api'

export default function ContainerList() {
  const [containers, setContainers] = useState([])
  const [stats, setStats] = useState({})
  const [loading, setLoading] = useState(false)
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [users, setUsers] = useState([])
  const [selectedUid, setSelectedUid] = useState(null)
  const [creating, setCreating] = useState(false)

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

  const fetchUsers = async () => {
    try {
      const res = await api.get('/api/admin/users')
      setUsers(res.data?.data || [])
    } catch {
      message.error('获取用户列表失败')
    }
  }

  useEffect(() => { fetchData() }, [])

  const stopContainer = async (cid) => {
    try {
      await api.post(`/api/admin/containers/id/${cid}/stop`)
      message.success('已停止')
      fetchData()
    } catch {
      message.error('操作失败')
    }
  }

  const removeContainer = async (cid) => {
    try {
      await api.post(`/api/admin/containers/id/${cid}/remove`)
      message.success('已删除')
      fetchData()
    } catch {
      message.error('操作失败')
    }
  }

  const openCreateModal = async () => {
    setSelectedUid(null)
    await fetchUsers()
    setCreateModalOpen(true)
  }

  const handleCreate = async () => {
    if (!selectedUid) {
      message.warning('请选择用户')
      return
    }
    setCreating(true)
    try {
      await api.post(`/api/admin/containers/${selectedUid}/create`)
      message.success('容器创建成功')
      setCreateModalOpen(false)
      fetchData()
    } catch {
      message.error('创建失败')
    } finally {
      setCreating(false)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
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
      title: '操作', key: 'action', width: 220,
      render: (_, r) => (
        <Space>
          {r.status === 'RUNNING' && (
            <>
              <Button
                size="small"
                type="link"
                onClick={() => window.open(`/admin-proxy/${r.userId}/`, '_blank')}
              >
                访问 WebUI
              </Button>
              <Popconfirm title="确认停止?" onConfirm={() => stopContainer(r.id)}>
                <Button size="small" danger>停止</Button>
              </Popconfirm>
            </>
          )}
          <Popconfirm title="确认删除容器?" onConfirm={() => removeContainer(r.id)}>
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
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
          创建容器
        </Button>
      </div>
      <Table
        columns={columns}
        dataSource={containers}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 20 }}
      />
      <Modal
        title="创建容器"
        open={createModalOpen}
        onOk={handleCreate}
        onCancel={() => setCreateModalOpen(false)}
        confirmLoading={creating}
        okText="创建"
        cancelText="取消"
      >
        <div style={{ marginBottom: 8 }}>选择用户：</div>
        <Select
          style={{ width: '100%' }}
          placeholder="请选择用户"
          value={selectedUid}
          onChange={setSelectedUid}
          options={users.map(u => ({ value: u.id, label: `${u.username} (ID: ${u.id})` }))}
          showSearch
          filterOption={(input, option) => option.label.toLowerCase().includes(input.toLowerCase())}
        />
      </Modal>
    </div>
  )
}
