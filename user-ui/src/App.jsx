import React, { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Workbench from './pages/Workbench/index'
import api from './api'

export default function App() {
  const [authed, setAuthed] = useState(null)
  const [username, setUsername] = useState('')

  useEffect(() => {
    // Use fetch directly to avoid the axios 401-redirect interceptor during initial check
    fetch('/api/containers/my', { credentials: 'include' })
      .then(res => setAuthed(res.ok))
      .catch(() => setAuthed(false))
  }, [])

  if (authed === null) return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <Spin size="large" />
    </div>
  )

  return (
    <BrowserRouter>
      <Routes>
        {!authed ? (
          <>
            <Route path="/" element={<Login onLogin={(u) => { setUsername(u); setAuthed(true) }} />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        ) : (
          <>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard username={username} onLogout={() => setAuthed(false)} />} />
            <Route path="/workbench" element={<Workbench username={username} onLogout={() => setAuthed(false)} />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </>
        )}
      </Routes>
    </BrowserRouter>
  )
}
