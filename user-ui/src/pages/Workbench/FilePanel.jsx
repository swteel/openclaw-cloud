import React, { useEffect, useState, useRef } from 'react'
import { List, Button, Upload, Typography, Popconfirm, message, Divider, Spin } from 'antd'
import { UploadOutlined, DeleteOutlined, FileOutlined } from '@ant-design/icons'
import api from '../../api'

const { Text } = Typography

export default function FilePanel() {
  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)

  const fetchFiles = async () => {
    setLoading(true)
    try {
      const res = await api.get('/portal/files')
      setFiles(res.data?.files || [])
    } catch {
      message.error('加载文件列表失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchFiles() }, [])

  const deleteFile = async (path) => {
    try {
      await api.delete(`/portal/files/${encodeURIComponent(path)}`)
      message.success('已删除')
      fetchFiles()
    } catch {
      message.error('删除失败')
    }
  }

  const customUpload = async ({ file, onSuccess, onError }) => {
    setUploading(true)
    const formData = new FormData()
    formData.append('file', file)
    try {
      await api.post(`/portal/upload/${encodeURIComponent(file.name)}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      message.success(`${file.name} 上传成功`)
      onSuccess(null, file)
      fetchFiles()
    } catch {
      message.error(`${file.name} 上传失败`)
      onError(new Error('Upload failed'))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div style={{ padding: 12 }}>
      <Text strong style={{ fontSize: 14 }}>工作区文件</Text>
      <Divider style={{ margin: '8px 0' }} />
      <Upload
        customRequest={customUpload}
        showUploadList={false}
        multiple={false}
      >
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
          renderItem={item => (
            <List.Item
              style={{ padding: '4px 0' }}
              actions={[
                <Popconfirm
                  title={`删除 ${item}?`}
                  onConfirm={() => deleteFile(item)}
                  key="del"
                >
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                  />
                </Popconfirm>
              ]}
            >
              <List.Item.Meta
                avatar={<FileOutlined style={{ color: '#1677ff' }} />}
                title={<Text ellipsis style={{ maxWidth: 150, fontSize: 12 }} title={item}>{item}</Text>}
              />
            </List.Item>
          )}
        />
      )}
    </div>
  )
}
