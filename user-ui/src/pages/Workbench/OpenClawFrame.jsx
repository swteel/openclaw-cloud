import React, { useRef, useState, useEffect } from 'react'
import { Spin } from 'antd'

// Use openclaw's own CSS modifier classes to hide topbar and nav:
// shell--onboarding → grid-template-rows: 0 1fr  +  topbar { display:none }
// shell--chat-focus → grid-template-columns: 0px 1fr  +  nav collapsed
const HIDE_CLASSES = ['shell--onboarding', 'shell--chat-focus']

// Sensitive routes that users should not access
const BLOCKED_ROUTES = /\/(config|settings|providers|nodes|cron|debug|usage)(\/|$|\?)/

export default function OpenClawFrame() {
  const iframeRef = useRef(null)
  const [iframeSrc, setIframeSrc] = useState(null)

  useEffect(() => {
    fetch('/api/containers/my', { credentials: 'include' })
      .then(r => r.json())
      .then(data => {
        const token = data?.data?.gatewayToken
        // Navigate directly to chat route; token is injected server-side by portal proxy
        setIframeSrc(token ? `/app/chat?token=${encodeURIComponent(token)}` : '/app/chat')
      })
      .catch(() => setIframeSrc('/app/chat'))
  }, [])

  const setupIframe = () => {
    try {
      const iframe = iframeRef.current
      if (!iframe) return
      const doc = iframe.contentDocument || iframe.contentWindow?.document
      if (!doc) return

      // Add openclaw's own modifier classes to .shell inside its Shadow DOM
      const applyHide = () => {
        const appEl = doc.querySelector('openclaw-app')
        if (!appEl || !appEl.shadowRoot) return false
        const shell = appEl.shadowRoot.querySelector('.shell')
        if (!shell) return false
        HIDE_CLASSES.forEach(cls => shell.classList.add(cls))
        return true
      }

      // Try immediately, then poll until shadow root + shell are ready
      if (!applyHide()) {
        const timer = setInterval(() => {
          if (applyHide()) clearInterval(timer)
        }, 100)
        setTimeout(() => clearInterval(timer), 10000)
      }

      // Re-apply if openclaw removes the classes during re-render
      const watchForShell = setInterval(() => {
        const appEl = doc.querySelector('openclaw-app')
        if (!appEl || !appEl.shadowRoot) return
        const shell = appEl.shadowRoot.querySelector('.shell')
        if (!shell) return
        clearInterval(watchForShell)
        new MutationObserver(() => {
          HIDE_CLASSES.forEach(cls => {
            if (!shell.classList.contains(cls)) shell.classList.add(cls)
          })
        }).observe(shell, { attributes: true, attributeFilter: ['class'] })
      }, 200)
      setTimeout(() => clearInterval(watchForShell), 10000)

      // Block navigation to sensitive routes
      const win = iframe.contentWindow
      if (win && win.history) {
        const guardNav = () => {
          try {
            if (BLOCKED_ROUTES.test(win.location.pathname)) win.history.back()
          } catch (_) { /* cross-origin */ }
        }
        const origPush = win.history.pushState.bind(win.history)
        const origReplace = win.history.replaceState.bind(win.history)
        win.history.pushState = (s, t, url) => { origPush(s, t, url); guardNav() }
        win.history.replaceState = (s, t, url) => { origReplace(s, t, url); guardNav() }
        win.addEventListener('popstate', guardNav)
      }
    } catch (e) {
      console.warn('Could not set up iframe:', e)
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
      onLoad={setupIframe}
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
