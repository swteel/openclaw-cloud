import React, { useEffect, useState } from 'react'
import { List, Button, Upload, Typography, Popconfirm, message, Divider, Spin, Space } from 'antd'
import { UploadOutlined, DeleteOutlined, FileOutlined, DownloadOutlined } from '@ant-design/icons'

const { Text } = Typography

export default function FilePanel({ containerName: containerNameProp }) {
  const [containerName, setContainerName] = useState(containerNameProp || null)
  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)

  // If containerName not passed via prop, resolve from API
  useEffect(() => {
    if (containerNameProp) {
      setContainerName(containerNameProp)
      return
    }
    fetch('/api/containers/my/all', { credentials: 'include' })
      .then(r => r.json())
      .then(data => {
        const list = data?.data || []
        if (list.length > 0) setContainerName(list[0].containerName)
      })
      .catch(() => {})
  }, [containerNameProp])

  const fetchFiles = async () => {
    if (!containerName) return
    setLoading(true)
    try {
      const res = await fetch(`/portal/container-files/${encodeURIComponent(containerName)}`, {
        credentials: 'include',
      })
      const data = await res.json()
      setFiles(data?.data || [])
    } catch {
      message.error('加载文件列表失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (containerName) fetchFiles()
  }, [containerName])

  const deleteFile = async (filename) => {
    try {
      const res = await fetch(
        `/portal/container-files/${encodeURIComponent(containerName)}?filename=${encodeURIComponent(filename)}`,
        { method: 'DELETE', credentials: 'include' }
      )
      if (res.ok) {
        message.success('已删除')
        fetchFiles()
      } else {
        message.error('删除失败')
      }
    } catch {
      message.error('删除失败')
    }
  }

  const downloadFile = (filename) => {
    const url = `/portal/container-files/${encodeURIComponent(containerName)}/download?filename=${encodeURIComponent(filename)}`
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
  }

  const customUpload = async ({ file, onSuccess, onError }) => {
    if (!containerName) { onError(new Error('no container')); return }
    setUploading(true)
    const formData = new FormData()
    formData.append('file', file)
    try {
      const res = await fetch(
        `/portal/container-files/${encodeURIComponent(containerName)}/upload`,
        { method: 'POST', credentials: 'include', body: formData }
      )
      if (res.ok) {
        message.success(`${file.name} 上传成功`)
        onSuccess(null, file)
        fetchFiles()
      } else {
        throw new Error('Upload failed')
      }
    } catch {
      message.error(`${file.name} 上传失败`)
      onError(new Error('Upload failed'))
    } finally {
      setUploading(false)
    }
  }

  if (!containerName) {
    return (
      <div style={{ padding: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>正在获取容器信息…</Text>
      </div>
    )
  }

  return (
    <div style={{ padding: 12 }}>
      <Text strong style={{ fontSize: 14 }}>工作区文件</Text>
      <div style={{ marginTop: 2, marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 11 }} ellipsis title={containerName}>
          {containerName}
        </Text>
      </div>
      <Divider style={{ margin: '0 0 8px 0' }} />
      <Upload customRequest={customUpload} showUploadList={false} multiple={false}>
        <Button icon={<UploadOutlined />} loading={uploading} block size="small" style={{ marginBottom: 8 }}>
          上传文件
        </Button>
      </Upload>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 20 }}><Spin /></div>
      ) : (
        <List
          size="small"
          dataSource={files}
          locale={{ emptyText: '暂无文件' }}
          renderItem={filename => (
            <List.Item
              style={{ padding: '4px 0' }}
              actions={[
                <Space size={2} key="actions">
                  <Button
                    type="text"
                    size="small"
                    icon={<DownloadOutlined />}
                    onClick={() => downloadFile(filename)}
                    title="下载"
                  />
                  <Popconfirm
                    title={`删除 ${filename}?`}
                    onConfirm={() => deleteFile(filename)}
                  >
                    <Button type="text" danger size="small" icon={<DeleteOutlined />} title="删除" />
                  </Popconfirm>
                </Space>
              ]}
            >
              <List.Item.Meta
                avatar={<FileOutlined style={{ color: '#1677ff' }} />}
                title={
                  <Text ellipsis style={{ maxWidth: 120, fontSize: 12 }} title={filename}>
                    {filename}
                  </Text>
                }
              />
            </List.Item>
          )}
        />
      )}
    </div>
  )
}
