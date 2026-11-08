export type Settings = {
  name: string; phone: string | null; telegram: string | null; address: string | null; directions: string | null
  mapUrl: string | null; hours: string | null; version: number; privacyVersion: string; privacyRu: string; privacyUz: string
}
export type Category = {
  id: number; slug: string; nameRu: string; nameUz: string; kind: string; enabled: boolean; sortOrder: number; version: number
}
export type Product = {
  id: number; categoryId: number; brand: string; name: string; slug: string; description: string; status: string; version: number
}
export type Image = { id: number; url: string; width: number; height: number; alt: string | null; variantId: number | null }
export type Card = {
  id: number; slug: string; brand: string; name: string; variantId: number; color: string | null
  storageGb: number | null; price: number; originalPrice: number | null; stock: string
  installment: boolean; image: string | null; variantCount: number
}
export type Variant = {
  id: number; sku: string; color: string | null; ramGb: number | null; storageGb: number | null
  screen: string | null; cpu: string | null; gpu: string | null; cameras: string | null; battery: string | null
  sim: string | null; os: string | null; weight: string | null; notes: string | null
  price: number; originalPrice: number | null; stock: string; installment: boolean
  installmentTerms: string | null; version: number; salePrice: number | null; saleFrom: string | null; saleUntil: string | null
}
export type Detail = { product: Product; images: Image[]; variants: Variant[] }
export type Result<T> = { items: T[]; hasNext: boolean }
export type Page<T> = { content: T[]; totalPages: number }
export type Account = { id: number; name: string; login: string; role: string; enabled: boolean; mustChange: boolean; version: number; createdAt: string; lastLogin: string | null }

export class ApiError extends Error {
  constructor(public status: number, public code: string, public details: Record<string, unknown>) {
    super(code)
  }
}
export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch('/api/v1' + path, { ...options, credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) {
    const details = await response.json().catch(() => ({}))
    throw new ApiError(response.status, details.code ?? 'TEMPORARY_UNAVAILABLE', details)
  }
  return response.status === 204 ? undefined as T : response.json()
}
export async function mutate<T>(path: string, method: string, data?: unknown, key?: string): Promise<T> {
  const csrf = await api<{ headerName: string; token: string }>('/auth/csrf')
  const headers = new Headers({ [csrf.headerName]: csrf.token })
  if (key) headers.set('Idempotency-Key', key)
  if (data instanceof URLSearchParams || data instanceof FormData) return api(path, { method, headers, body: data })
  if (data !== undefined) headers.set('Content-Type', 'application/json')
  return api(path, { method, headers, body: data === undefined ? undefined : JSON.stringify(data) })
}
export const label = (v: Pick<Variant, 'color' | 'ramGb' | 'storageGb' | 'sku'>) =>
  [v.color, v.ramGb && v.ramGb + ' GB RAM', v.storageGb && v.storageGb + ' GB', v.sku].filter(Boolean).join(' · ')
