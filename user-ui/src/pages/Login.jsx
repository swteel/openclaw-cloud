import React, { useState } from 'react'
import { Form, Input, Button, Tabs, Card, Typography, Alert } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import api from '../api'

const { Title } = Typography

function LoginForm({ onLogin }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const onFinish = async (values) => {
    setLoading(true)
    setError('')
    try {
      await api.post('/portal/login', values)
      onLogin(values.username)
    } catch {
      setError('用户名或密码错误')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Form onFinish={onFinish} autoComplete="off">
      {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
      <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
        <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
      </Form.Item>
      <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" block size="large" loading={loading}>
          登录
        </Button>
      </Form.Item>
    </Form>
  )
}

function RegisterForm({ onLogin }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const onFinish = async (values) => {
    setLoading(true)
    setError('')
    try {
      const res = await api.post('/api/register', {
        username: values.username,
        password: values.password,
      })
      if (!res.data?.success) {
        setError(res.data?.message || '注册失败')
        return
      }
      // Auto login after registration
      await api.post('/portal/login', { username: values.username, password: values.password })
      onLogin(values.username)
    } catch (e) {
      setError(e.response?.data?.message || '注册失败，用户名可能已存在')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Form onFinish={onFinish} autoComplete="off">
      {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
      <Form.Item name="username" rules={[{ required: true, min: 3, message: '用户名至少3位' }]}>
        <Input prefix={<UserOutlined />} placeholder="用户名（3-64位）" size="large" />
      </Form.Item>
      <Form.Item name="password" rules={[{ required: true, min: 6, message: '密码至少6位' }]}>
        <Input.Password prefix={<LockOutlined />} placeholder="密码（6位以上）" size="large" />
      </Form.Item>
      <Form.Item
        name="confirm"
        dependencies={['password']}
        rules={[
          { required: true, message: '请确认密码' },
          ({ getFieldValue }) => ({
            validator(_, value) {
              if (!value || getFieldValue('password') === value) return Promise.resolve()
              return Promise.reject('两次密码不一致')
            },
          }),
        ]}
      >
        <Input.Password prefix={<LockOutlined />} placeholder="确认密码" size="large" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" block size="large" loading={loading}>
          注册并自动登录
        </Button>
      </Form.Item>
    </Form>
  )
}

export default function Login({ onLogin }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#f0f2f5' }}>
      <Card style={{ width: 420 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>OpenClaw Cloud</Title>
        <Tabs
          defaultActiveKey="login"
          centered
          items={[
            { key: 'login', label: '登录', children: <LoginForm onLogin={onLogin} /> },
            { key: 'register', label: '注册', children: <RegisterForm onLogin={onLogin} /> },
          ]}
        />
      </Card>
    </div>
  )
}
