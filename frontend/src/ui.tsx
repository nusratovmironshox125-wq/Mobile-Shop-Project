import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useLocale } from './locale'

const paths: Record<string, ReactNode> = {
  search: <><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/></>,
  heart: <path d="M20.8 4.7a5.2 5.2 0 0 0-7.4 0L12 6.1l-1.4-1.4a5.2 5.2 0 0 0-7.4 7.4l1.4 1.4L12 21l7.4-7.5 1.4-1.4a5.2 5.2 0 0 0 0-7.4Z"/>,
  moon: <path d="M20.8 15A9 9 0 0 1 9 3.2 9.1 9.1 0 1 0 20.8 15Z"/>,
  sun: <><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4m0-14.2-1.4 1.4M6.3 17.7l-1.4 1.4"/></>,
  phone: <><rect x="7" y="2" width="10" height="20" rx="2"/><path d="M10 18h4"/></>,
  laptop: <><rect x="4" y="4" width="16" height="12" rx="2"/><path d="M2 20h20M4 16l-2 4m18-4 2 4"/></>,
  tag: <path d="M3 4h8l10 10-7 7L3 10V4Zm4 4h.01"/>,
  grid: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
  arrow: <path d="M4 12h15m-6-6 6 6-6 6"/>,
  back: <path d="M20 12H5m6 6-6-6 6-6"/>,
  pin: <><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2"/></>,
  check: <path d="m5 12 5 5 9-9"/>,
  picture: <><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8" cy="9" r="1"/><path d="m3 17 5-4 4 3 4-5 5 5"/></>,
}
export function Icon({ name }: { name: string }) {
  return <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" strokeWidth="1.7"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] ?? paths.grid}</svg>
}
export function Brand({ name }: { name: string }) {
  return <Link className="brand" to="/"><span className="brand-icon"><Icon name="phone"/></span><span>{name}</span></Link>
}
export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="empty"><span className="empty-icon"><Icon name="grid"/></span><h2>{title}</h2>{children}</div>
}
export function Loading() {
  const { t } = useLocale()
  return <p className="loading" role="status">{t('Загрузка…', 'Yuklanmoqda…')}</p>
}
