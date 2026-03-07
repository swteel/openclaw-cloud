import React, { useEffect, useState } from 'react'
import { Form, Input, Button, Card, Descriptions, message, Alert } from 'antd'
import api from '../api'

export default function PlatformConfig() {
  const [config, setConfig] = useState({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  const fetchConfig = async () => {
    setLoading(true)
    try {
      const res = await api.get('/api/admin/config')
      setConfig(res.data?.data || {})
    } catch {
      message.error('加载配置失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchConfig() }, [])

  const onFinish = async (values) => {
    setSaving(true)
    try {
      await api.put('/api/admin/config', values)
      message.success('配置已保存')
      fetchConfig()
      form.resetFields()
    } catch {
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ maxWidth: 600 }}>
      <Card title="当前配置" loading={loading} style={{ marginBottom: 24 }}>
        <Descriptions column={1} bordered>
          <Descriptions.Item label="DASHSCOPE_API_KEY">{config.dashscopeApiKey || '未设置'}</Descriptions.Item>
          <Descriptions.Item label="最大容器数">{config.maxContainers}</Descriptions.Item>
          <Descriptions.Item label="端口范围">{config.portRange}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="更新配置">
        <Alert
          message="更新 API Key 后，新创建的容器将使用新 Key"
          type="info"
          style={{ marginBottom: 16 }}
        />
        <Form form={form} onFinish={onFinish} layout="vertical">
          <Form.Item
            name="dashscopeApiKey"
            label="DASHSCOPE_API_KEY"
            rules={[{ required: true, message: '请输入 API Key' }]}
          >
            <Input.Password placeholder="输入新的 API Key" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={saving}>保存</Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
