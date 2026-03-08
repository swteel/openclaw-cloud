import React, { useEffect, useState } from 'react'
import { Table, Tag, Select, Button, message } from 'antd'
import api from '../api'

export default function UserList() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(false)

  const fetchUsers = async () => {
    setLoading(true)
    try {
      const res = await api.get('/api/admin/users')
      setUsers(res.data?.data || [])
    } catch {
      message.error('加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchUsers() }, [])

  const createContainer = async (uid) => {
    try {
      await api.post(`/api/admin/containers/${uid}/create`)
      message.success('操作成功')
      fetchUsers()
    } catch {
      message.error('操作失败')
    }
  }

  const changeRole = async (uid, role) => {
    try {
      await api.put(`/api/admin/users/${uid}/role`, { role })
      message.success('角色已更新')
      fetchUsers()
    } catch {
      message.error('操作失败')
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username', key: 'username' },
    {
      title: '角色', dataIndex: 'role', key: 'role', width: 120,
      render: (role, r) => (
        <Select
          value={role}
          size="small"
          style={{ width: 100 }}
          onChange={val => changeRole(r.id, val)}
          options={[
            { value: 'USER', label: 'USER' },
            { value: 'ADMIN', label: 'ADMIN' },
          ]}
        />
      )
    },
    {
      title: '拥有容器', dataIndex: 'containerNames', key: 'containerNames',
      render: names => {
        if (!names || names.length === 0) return <Tag>—</Tag>
        return <span>{names.join(', ')}</span>
      }
    },
    {
      title: '注册时间', dataIndex: 'createdAt', key: 'createdAt',
      render: t => t ? new Date(t).toLocaleString('zh-CN') : '-'
    },
    {
      title: '最后活跃', dataIndex: 'lastActiveAt', key: 'lastActiveAt',
      render: t => t ? new Date(t).toLocaleString('zh-CN') : '-'
    },
    {
      title: '操作', key: 'actions',
      render: (_, r) => {
        const names = r.containerNames || []
        if (names.length === 0) {
          return <Button size="small" type="primary" onClick={() => createContainer(r.id)}>创建容器</Button>
        }
        return null
      }
    },
  ]

  return (
    <Table
      columns={columns}
      dataSource={users}
      rowKey="id"
      loading={loading}
      pagination={{ pageSize: 20 }}
    />
  )
}
