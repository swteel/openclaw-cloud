import React, { useRef, useState, useEffect } from 'react'
import { Spin } from 'antd'

const HIDE_NAV_CSS = `
  /* Hide openclaw's own top navigation bar */
  .navbar, nav.navbar, header.navbar,
  [class*="navbar"], [class*="topbar"],
  [class*="header-bar"], [id*="navbar"],
  [id*="topbar"] {
    display: none !important;
  }
  /* Remove top padding that compensates for navbar */
  body { padding-top: 0 !important; margin-top: 0 !important; }
  #app, #root, .app-container { padding-top: 0 !important; }
`

export default function OpenClawFrame() {
  const iframeRef = useRef(null)
  const [iframeSrc, setIframeSrc] = useState(null)

  useEffect(() => {
    fetch('/api/containers/my', { credentials: 'include' })
      .then(r => r.json())
      .then(data => {
        const token = data?.data?.gatewayToken
        setIframeSrc(token ? `/app/?token=${encodeURIComponent(token)}` : '/app/')
      })
      .catch(() => setIframeSrc('/app/'))
  }, [])

  const injectCss = () => {
    try {
      const iframe = iframeRef.current
      if (!iframe) return
      const doc = iframe.contentDocument || iframe.contentWindow?.document
      if (!doc) return
      const style = doc.createElement('style')
      style.textContent = HIDE_NAV_CSS
      doc.head.appendChild(style)
    } catch (e) {
      console.warn('Could not inject CSS into iframe:', e)
    }
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
      onLoad={injectCss}
      style={{
        width: '100%',
        height: '100%',
        border: 'none',
        display: 'block',
      }}
      allow="clipboard-read; clipboard-write"
    />
  )
}
