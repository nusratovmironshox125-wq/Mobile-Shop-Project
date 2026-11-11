import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from './api'
import { useLocale } from './locale'

type Update = { unreadCount: number; latestId: number; recent: { id: number; number: string; productName: string }[] }
export function useLeadUpdates(account?: number) {
  const [value, setValue] = useState<Update>()
  const [offline, setOffline] = useState(false)
  const [toast, setToast] = useState<Update['recent'][number]>()
  useEffect(() => {
    setValue(undefined); setOffline(false); setToast(undefined)
    if (!account) return
    let stopped = false, baseline: number | undefined, failures = 0
    let timer: ReturnType<typeof setTimeout>
    let abort: AbortController | undefined
    const key = 'rms-notice-' + account
    async function poll() {
      clearTimeout(timer)
      abort?.abort()
      if (stopped || document.visibilityState !== 'visible') return
      const current = new AbortController()
      abort = current
      try {
        const data = await api<Update>('/admin/leads/updates', { signal: current.signal })
        if (stopped || current.signal.aborted) return
        setValue(data); setOffline(false); failures = 0
        const previous = baseline
        // IDs are only a notification hint. The count and rows always come from the server.
        baseline = data.latestId
        if (previous !== undefined && data.latestId > previous) {
          const candidate = data.recent.find(row => row.id > previous)
          if (candidate) {
            const show = () => {
              if (stopped || current.signal.aborted) return
              let seen = 0
              try { seen = Number(localStorage.getItem(key) ?? 0) } catch { /* Memory-only fallback. */ }
              if (candidate.id > seen) {
                try { localStorage.setItem(key, String(candidate.id)) } catch { /* Storage is optional. */ }
                setToast(candidate)
              }
            }
            if (navigator.locks) await navigator.locks.request(key, show)
            else show()
          }
        }
        window.dispatchEvent(new CustomEvent('rms-lead-refresh', { detail: data.latestId }))
      } catch (error) {
        if (current.signal.aborted || stopped) return
        failures++; setOffline(true)
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          window.dispatchEvent(new Event('rms-auth-expired'))
          return
        }
      } finally {
        if (!stopped && !current.signal.aborted) timer = setTimeout(poll, Math.min(60000, 10000 * 2 ** failures))
      }
    }
    const resume = () => { void poll() }
    document.addEventListener('visibilitychange', resume)
    window.addEventListener('online', resume)
    window.addEventListener('rms-lead-read', resume)
    void poll()
    return () => {
      stopped = true; clearTimeout(timer); abort?.abort()
      document.removeEventListener('visibilitychange', resume)
      window.removeEventListener('online', resume)
      window.removeEventListener('rms-lead-read', resume)
    }
  }, [account])
  return { value, offline, toast, dismiss: () => setToast(undefined) }
}
export function LeadNotice({ updates }: { updates: ReturnType<typeof useLeadUpdates> }) {
  const { t } = useLocale()
  return <>
    {updates.offline && <p className="alert" role="status">{t('Нет обновлений связи. Счётчик может быть устаревшим. Повторяем подключение…', 'Aloqa uzildi. Hisob eskirgan bo‘lishi mumkin. Qayta ulanmoqda…')}</p>}
    {updates.toast && <aside className="lead-toast panel" role="status">
      <strong>{t('Новая заявка', 'Yangi ariza')} · {updates.toast.number}</strong>
      <p>{updates.toast.productName}</p>
      <Link className="button primary" to={'/admin/leads/' + updates.toast.id} onClick={updates.dismiss}>{t('Открыть', 'Ochish')}</Link>
      <button className="button" onClick={updates.dismiss}>{t('Закрыть', 'Yopish')}</button>
    </aside>}
  </>
}
