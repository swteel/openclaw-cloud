import React, { useRef, useState, useEffect } from 'react'
import { Spin } from 'antd'

// CSS injected into openclaw's Shadow DOM to hide topbar and nav sidebar
const SHADOW_CSS = `
  /* Hide topbar and collapse nav */
  .topbar { display: none !important; }
  .nav    { display: none !important; }

  /* Adjust shell grid: remove topbar row and nav column */
  .shell {
    --shell-topbar-height: 0px !important;
    --shell-nav-width: 0px !important;
    grid-template-columns: 1fr !important;
    grid-template-rows: 1fr !important;
    grid-template-areas: "content" !important;
  }

  /* Remove content padding that assumed a topbar */
  .content { padding-top: 0 !important; }
`

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

      // Inject CSS into the Shadow Root of <openclaw-app> (open Shadow DOM)
      const tryInjectShadow = () => {
        const appEl = doc.querySelector('openclaw-app')
        if (!appEl || !appEl.shadowRoot) return false
        // Avoid duplicate injection
        if (appEl.shadowRoot.querySelector('style[data-portal-hide]')) return true
        const style = doc.createElement('style')
        style.setAttribute('data-portal-hide', '1')
        style.textContent = SHADOW_CSS
        appEl.shadowRoot.appendChild(style)
        return true
      }

      // Try immediately after load, then poll until shadow root is available
      if (!tryInjectShadow()) {
        const timer = setInterval(() => {
          if (tryInjectShadow()) clearInterval(timer)
        }, 100)
        setTimeout(() => clearInterval(timer), 8000)
      }

      // Intercept SPA navigation to blocked routes
      const win = iframe.contentWindow
      if (win && win.history) {
        const guardNav = () => {
          try {
            if (BLOCKED_ROUTES.test(win.location.pathname)) {
              win.history.back()
            }
          } catch (_) { /* cross-origin */ }
        }

        // Intercept pushState / replaceState immediately
        const origPush = win.history.pushState.bind(win.history)
        const origReplace = win.history.replaceState.bind(win.history)
        win.history.pushState = (s, t, url) => {
          origPush(s, t, url)
          guardNav()
        }
        win.history.replaceState = (s, t, url) => {
          origReplace(s, t, url)
          guardNav()
        }
        win.addEventListener('popstate', guardNav)
      }
    } catch (e) {
      console.warn('Could not inject into iframe:', e)
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
