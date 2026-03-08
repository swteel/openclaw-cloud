import React, { useRef, useState, useEffect } from 'react'
import { Spin } from 'antd'

const BLOCKED_ROUTES = /\/(config|settings|providers|nodes|cron|debug|usage)(\/|$|\?)/

export default function OpenClawFrame() {
  const iframeRef = useRef(null)
  const [iframeSrc, setIframeSrc] = useState(null)

  useEffect(() => {
    fetch('/api/containers/my', { credentials: 'include' })
      .then(r => r.json())
      .then(data => {
        const token = data?.data?.gatewayToken
        setIframeSrc(token ? `/app/chat?token=${encodeURIComponent(token)}` : '/app/chat')
      })
      .catch(() => setIframeSrc('/app/chat'))
  }, [])

  const onLoad = () => {
    try {
      const win = iframeRef.current?.contentWindow
      if (!win?.history) return
      const guard = () => {
        try { if (BLOCKED_ROUTES.test(win.location.pathname)) win.history.back() } catch (_) {}
      }
      const origPush = win.history.pushState.bind(win.history)
      const origReplace = win.history.replaceState.bind(win.history)
      win.history.pushState = (s, t, url) => { origPush(s, t, url); guard() }
      win.history.replaceState = (s, t, url) => { origReplace(s, t, url); guard() }
      win.addEventListener('popstate', guard)
    } catch (_) {}
  }

  if (!iframeSrc) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Spin />
      </div>
    )
  }

  return (
    <iframe
      ref={iframeRef}
      src={iframeSrc}
      title="OpenClaw"
      onLoad={onLoad}
      style={{ width: '100%', height: '100%', border: 'none', display: 'block' }}
      allow="clipboard-read; clipboard-write"
    />
  )
}
