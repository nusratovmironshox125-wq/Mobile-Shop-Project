import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, NavLink, Navigate, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, ApiError, mutate } from './api'
import type { Account, Category, Detail, Page, Product, Result, Settings, Variant } from './api'
import { ErrorText, money, statusName, useLocale } from './locale'
import { Empty, Loading } from './ui'
import { Pager } from './storefront'
import { LeadNotice, useLeadUpdates } from './lead-updates'
import { specFields, variantLabel } from './specs'

function usePage(): [number, (page: number) => void] {
  const [params,setParams]=useSearchParams()
  return [Number(params.get('page') ?? 0), page => setParams(previous => { const next=new URLSearchParams(previous); next.set('page',String(page)); return next })]
}

function useResource<T>(path: string) {
  const [value, setValue] = useState<T>()
  const [loadedPath,setLoadedPath]=useState('')
  const [error, setError] = useState<unknown>()
  const [revision, refresh] = useState(0)
  useEffect(() => {
    const abort = new AbortController()
    setError(undefined)
    api<T>(path, { signal: abort.signal }).then(value=>{if(!abort.signal.aborted){setValue(value);setLoadedPath(path)}}).catch(err => {
      if (!abort.signal.aborted) setError(err)
    })
    return () => abort.abort()
  }, [path, revision])
  const reload = useCallback(() => refresh(i => i + 1), [])
  return { value: loadedPath===path ? value : undefined, error, reload, setValue }
}

function Field({ name, title, value, type = 'text', required, max, disabled }: {
  name: string; title: string; value?: string | number | null; type?: string; required?: boolean; max?: number; disabled?: boolean
}) {
  return <label className="field">{title}{type === 'textarea'
    ? <textarea name={name} defaultValue={value ?? ''} required={required} maxLength={max} rows={4}/>
    : <input name={name} type={type} defaultValue={value ?? ''} required={required} maxLength={max} readOnly={disabled}
      step={type === 'number' ? 'any' : undefined}/>}</label>
}

function AdminForm({ children, onSave, title, button }: {
  children: ReactNode; onSave: (data: FormData) => Promise<unknown>; title?: string; button?: string
}) {
  const { t } = useLocale()
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<unknown>()
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const data = new FormData(event.currentTarget)
    setBusy(true); setSaved(false); setError(undefined)
    try { await onSave(data); setSaved(true) } catch (error) { setError(error) } finally { setBusy(false) }
  }
  return <form className="panel admin-form" onSubmit={submit}>
    {title && <h2>{title}</h2>}
    {children}
    {Boolean(error) && <ErrorText error={error}/>}
    {saved && <p className="success" role="status">{t('Сохранено', 'Saqlandi')}</p>}
    <button className="button primary" disabled={busy}>{busy ? t('Подождите…', 'Kuting…') : button ?? t('Сохранить', 'Saqlash')}</button>
  </form>
}
const dataText = (data: FormData, name: string) => String(data.get(name) ?? '')
const dataNumber = (data: FormData, name: string) => dataText(data, name) === '' ? null : Number(data.get(name))
const dateLabel = (value: string, lang: string) => new Intl.DateTimeFormat(lang === 'uz' ? 'uz-UZ' : 'ru-RU', {
  dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Tashkent',
}).format(new Date(value))

export default function Admin({ onShopChange }: { onShopChange: () => void }) {
  const { t } = useLocale()
  const me = useResource<Account>('/auth/me')
  const navigate = useNavigate()
  const owner = me.value?.role === 'OWNER'
  const updates = useLeadUpdates(me.value && !me.value.mustChange ? me.value.id : undefined)
  useEffect(() => { const expired=()=>{ me.setValue(undefined); me.reload() }; window.addEventListener('rms-auth-expired',expired); return ()=>window.removeEventListener('rms-auth-expired',expired) }, [me.reload])
  if (!me.value) {
    if (me.error instanceof ApiError && me.error.status === 401) {
      return <Login onLogin={() => { me.reload(); navigate('/admin') }}/>
    }
    return me.error ? <ErrorText error={me.error}/> : <Loading/>
  }
  async function logout() {
    await mutate('/auth/logout', 'POST')
    try { localStorage.removeItem('rms-notice-'+me.value?.id) } catch { /* optional storage */ }
    me.setValue(undefined); me.reload(); navigate('/admin/login')
  }
  return <section className="subpage admin">
    <div className="section-head"><div><p className="kicker">{me.value.name}</p><h1>{t('Панель магазина', 'Do‘kon paneli')}</h1></div>
      <button className="button" onClick={() => void logout().catch(() => { me.setValue(undefined); me.reload() })}>{t('Выйти', 'Chiqish')}</button>
    </div>
    {me.value.mustChange ? <PasswordForm onChanged={() => { me.setValue(undefined); me.reload(); navigate('/admin/login') }}/> : <>
      <nav className="tabs" aria-label={t('Управление магазином', 'Do‘kon boshqaruvi')}>
        <NavLink to="/admin/products">{t('Товары', 'Mahsulotlar')}</NavLink>
        <NavLink to="/admin/categories">{t('Категории', 'Toifalar')}</NavLink>
        <NavLink to="/admin/leads">{t('Заявки', 'Arizalar')} <span className="badge" data-testid="unread-count">{updates.offline ? '!' : updates.value?.unreadCount ?? '…'}</span></NavLink>
        <NavLink to="/admin/settings">{t('Настройки', 'Sozlamalar')}</NavLink>
        {owner && <><NavLink to="/admin/users">{t('Администраторы', 'Administratorlar')}</NavLink>
          <NavLink to="/admin/audit">{t('Журнал', 'Jurnal')}</NavLink>
          <NavLink to="/admin/notifications">{t('Уведомления', 'Xabarlar')}</NavLink></>}
        <NavLink to="/admin/password">{t('Пароль', 'Parol')}</NavLink>
      </nav>
      <LeadNotice updates={updates}/>
      <Routes>
        <Route index element={<Navigate replace to="products"/>}/>
        <Route path="login" element={<Navigate replace to="/admin/products"/>}/>
        <Route path="products" element={<Products/>}/>
        <Route path="products/new" element={<EditProduct/>}/>
        <Route path="products/:id" element={<EditProduct/>}/>
        <Route path="categories" element={<Categories onChanged={onShopChange}/>}/>
        <Route path="settings" element={<EditSettings onChanged={onShopChange}/>}/>
        <Route path="leads" element={<LeadList/>}/>
        <Route path="leads/:id" element={<LeadDetail owner={owner}/>}/>
        {owner && <><Route path="users" element={<Users/>}/><Route path="audit" element={<AuditLog/>}/>
          <Route path="notifications" element={<Notifications/>}/></>}
        <Route path="password" element={<PasswordForm onChanged={() => { me.setValue(undefined); me.reload(); navigate('/admin/login') }}/>}/>
        <Route path="*" element={<Empty title={t('Раздел недоступен', 'Bo‘lim mavjud emas')}/>}/>
      </Routes>
    </>}
  </section>
}

function Login({ onLogin }: { onLogin: () => void }) {
  const { t } = useLocale()
  return <div className="narrow subpage">
    <h1>{t('Вход для сотрудников', 'Xodimlar uchun kirish')}</h1>
    <AdminForm button={t('Войти', 'Kirish')} onSave={async data => {
      await mutate('/auth/login', 'POST', new URLSearchParams({ username: dataText(data, 'username'), password: dataText(data, 'password') }))
      onLogin()
    }}>
      <label className="field">{t('Логин', 'Login')}<input name="username" autoComplete="username" required maxLength={60}/></label>
      <label className="field">{t('Пароль', 'Parol')}<input name="password" autoComplete="current-password" type="password" required maxLength={128}/></label>
    </AdminForm>
  </div>
}

function PasswordForm({ onChanged }: { onChanged: () => void }) {
  const { t } = useLocale()
  return <div className="narrow"><AdminForm title={t('Сменить пароль', 'Parolni o‘zgartirish')} onSave={async data => {
    await mutate('/auth/change-password', 'POST', { currentPassword: dataText(data, 'currentPassword'), newPassword: dataText(data, 'newPassword') })
    onChanged()
  }}>
    <label className="field">{t('Текущий пароль', 'Joriy parol')}<input name="currentPassword" autoComplete="current-password"
      type="password" required maxLength={128}/></label>
    <label className="field">{t('Новый пароль, от 12 символов', 'Yangi parol, kamida 12 belgi')}<input name="newPassword" autoComplete="new-password"
      type="password" required minLength={12} maxLength={128}/></label>
  </AdminForm></div>
}

function Products() {
  const { t } = useLocale()
  const [page, setPage] = usePage()
  const [params,setParams]=useSearchParams()
  const { value, error } = useResource<Page<Product>>('/admin/products?' + params)
  return <>
    <div className="section-head"><h2>{t('Товары', 'Mahsulotlar')}</h2>
      <Link className="button primary" to="/admin/products/new">{t('Добавить товар', 'Mahsulot qo‘shish')}</Link></div>
    <form className="filters" onSubmit={event=>{event.preventDefault(); const q=String(new FormData(event.currentTarget).get('q')??''); setParams(q?{q}:{})}}>
      <Field name="q" title={t('Модель, бренд или артикул','Model, brend yoki artikul')} value={params.get('q')} max={100}/><button className="button">{t('Найти','Topish')}</button>
    </form>
    {error ? <ErrorText error={error}/> : value ? <>
      <div className="admin-list">{value.content.map(p => <Link to={'/admin/products/' + p.id} className="admin-row" key={p.id}>
        <strong>{p.brand} {p.name}</strong><span className="badge quiet">{statusName(p.status, t)}</span>
      </Link>)}</div>
      {!value.content.length && <Empty title={t('Товаров пока нет', 'Hozircha mahsulotlar yo‘q')}/>}
      <Pager total={value.totalElements} page={page} next={page + 1 < value.totalPages} change={setPage}/>
    </> : <Loading/>}
  </>
}

function EditProduct() {
  const { id } = useParams()
  const { t } = useLocale()
  const cats = useResource<Category[]>('/admin/categories')
  return cats.error ? <ErrorText error={cats.error}/> : cats.value ? id
    ? <ExistingProduct key={id} id={id} categories={cats.value}/>
    : <ProductForm key="new" categories={cats.value}/>
    : <Loading/>
}

function ProductForm({ product, categories, reload }: { product?: Product; categories: Category[]; reload?: () => void }) {
  const { t, lang } = useLocale()
  const navigate = useNavigate()
  return <AdminForm title={product ? product.brand + ' ' + product.name : t('Новый товар', 'Yangi mahsulot')} onSave={async data => {
    const result = await mutate<Product>(product ? '/admin/products/' + product.id : '/admin/products', product ? 'PATCH' : 'POST', {
      categoryId: Number(data.get('categoryId')), brand: dataText(data, 'brand'), name: dataText(data, 'name'),
      description: dataText(data, 'description'),
      status: product ? dataText(data, 'status') : 'DRAFT', version: product?.version,
    })
    if (product) reload?.(); else navigate('/admin/products/' + result.id)
  }}>
    <div className="form-grid">
      <label className="field">{t('Категория', 'Toifa')}<select name="categoryId" defaultValue={product?.categoryId} required>
        {categories.map(c => <option key={c.id} value={c.id}>{lang === 'uz' ? c.nameUz : c.nameRu}</option>)}
      </select></label>
      <Field name="brand" title={t('Бренд', 'Brend')} value={product?.brand} required max={100}/>
      <Field name="name" title={t('Модель', 'Model')} value={product?.name} required max={150}/>
      {product && <label className="field">{t('Ссылка на товар','Mahsulot havolasi')}<Link to={'/products/'+product.slug}>{'/products/'+product.slug}</Link><button className="button" type="button" onClick={()=>void navigator.clipboard.writeText(location.origin+'/products/'+product.slug)}>{t('Копировать','Nusxalash')}</button></label>}
    </div>
    <Field name="description" title={t('Описание', 'Tavsif')} value={product?.description} type="textarea" max={5000}/>
    {product && <label className="field">{t('Публикация', 'Nashr')}<select name="status" defaultValue={product.status}>
      {(product.status === 'DRAFT' ? ['DRAFT','PUBLISHED','ARCHIVED'] : ['PUBLISHED','ARCHIVED']).map(s =>
        <option value={s} key={s}>{statusName(s, t)}</option>)}
    </select></label>}
  </AdminForm>
}

function ExistingProduct({ id, categories }: { id: string; categories: Category[] }) {
  const { t } = useLocale()
  const data = useResource<Detail>('/admin/products/' + id)
  const [addVariant, setAddVariant] = useState(false)
  if (data.error) return <ErrorText error={data.error}/>
  if (!data.value) return <Loading/>
  const { product, variants, images, kind } = data.value
  return <div className="admin-stack">
    <ProductForm product={product} categories={categories} reload={data.reload}/>
    <h2>{t('Комплектации', 'Variantlar')}</h2>
    {variants.map(v => <details key={v.id}><summary>{variantLabel(v,kind,t)}</summary>
      <VariantForm kind={kind} productId={id} variant={v} onSaved={data.reload}/>
    </details>)}
    {addVariant ? <VariantForm kind={kind} productId={id} onSaved={() => { data.reload(); setAddVariant(false) }}/>
      : <button className="button" onClick={() => setAddVariant(true)}>{t('Добавить комплектацию', 'Variant qo‘shish')}</button>}
    <AdminForm title={t('Фото товара', 'Mahsulot surati')} button={t('Загрузить фото', 'Surat yuklash')} onSave={async form => {
      if (form.get('variantId') === '') form.delete('variantId')
      await mutate('/admin/products/' + id + '/images', 'POST', form); data.reload()
    }}>
      <div className="form-grid">
        <label className="field">{t('Файл JPEG, PNG, WebP до 5 МБ', '5 MB gacha JPEG, PNG, WebP fayli')}
          <input name="file" type="file" required accept="image/jpeg,image/png,image/webp"/></label>
        <Field name="alt" title={t('Описание фото', 'Surat tavsifi')} max={200}/>
        <label className="field">{t('Комплектация на фото', 'Suratdagi variant')}<select name="variantId">
          <option value="">{t('Все комплектации', 'Barcha variantlar')}</option>
          {variants.map(v => <option key={v.id} value={v.id}>{variantLabel(v,kind,t)}</option>)}
        </select></label>
      </div>
    </AdminForm>
    <div className="admin-images">{images.map(i => <AdminForm key={i.id}
      button={t('Удалить фото', 'Suratni o‘chirish')} onSave={async () => {
        await mutate('/admin/products/' + id + '/images/' + i.id, 'DELETE'); data.reload()
      }}><img src={i.url.replace('/media/', '/api/v1/admin/media/')} alt={i.alt ?? ''} width={i.width} height={i.height}/></AdminForm>)}</div>
  </div>
}

function VariantForm({ productId, variant, onSaved, kind }: { productId: string; variant?: Variant; onSaved: () => void; kind: string }) {
  const { t } = useLocale()
  const fields = specFields.filter(([key,,,only])=> !['ramGb','storageGb','notes'].includes(key) && (!only || only === kind))
  const local = (value?: string | null) => value ? new Date(Date.parse(value) + 5 * 3600_000).toISOString().slice(0, 16) : ''
  const instant = (value: string) => value ? new Date(value + '+05:00').toISOString() : null
  return <AdminForm onSave={async data => {
    const path = '/admin/products/' + productId + '/variants' + (variant ? '/' + variant.id : '')
    await mutate(path, variant ? 'PATCH' : 'POST', {
      ...Object.fromEntries(fields.map(([key]) => [key, dataText(data, key)])), sku: dataText(data, 'sku'),
      stock: dataText(data, 'stock'), price: dataNumber(data, 'price'), ramGb: dataNumber(data, 'ramGb'),
      storageGb: dataNumber(data, 'storageGb'), installment: data.has('installment'), installmentTerms: dataText(data, 'installmentTerms'),
      notes: dataText(data, 'notes'), salePrice: dataNumber(data, 'salePrice'), saleFrom: instant(dataText(data, 'saleFrom')),
      saleUntil: instant(dataText(data, 'saleUntil')), version: variant?.version, confirmSpecCleanup: data.has('confirmSpecCleanup'),
    })
    onSaved()
  }}>
    <div className="form-grid">
      <Field name="sku" title={t('Артикул товара (SKU)', 'Mahsulot artikuli (SKU)')} value={variant?.sku} required max={80} disabled={!!variant}/>
      <Field name="price" title={t('Обычная цена, UZS', 'Odatiy narx, UZS')} value={variant?.originalPrice ?? variant?.price} type="number" required/>
      <label className="field">{t('Наличие', 'Mavjudlik')}<select name="stock" defaultValue={variant?.stock ?? 'IN_STOCK'}>
        {['IN_STOCK','OUT_OF_STOCK'].map(s => <option key={s} value={s}>{statusName(s, t)}</option>)}
      </select></label>
      <Field name="ramGb" title={t('Оперативная память, ГБ','Operativ xotira, GB')} type="number" value={variant?.ramGb}/>
      <Field name="storageGb" title={kind === 'LAPTOP' ? t('Накопитель, ГБ','Disk, GB') : t('Встроенная память, ГБ', 'Ichki xotira, GB')} type="number" value={variant?.storageGb}/>
      {fields.map(([key, ru, uz]) => <Field key={key} name={key} title={t(ru, uz)} value={String(variant?.[key] ?? '')} max={key === 'cameras' ? 150 : 100}/>)}
      <Field name="salePrice" title={t('Цена со скидкой, UZS', 'Chegirmali narx, UZS')} value={variant?.salePrice} type="number"/>
      <Field name="saleFrom" title={t('Начало акции, Ташкент', 'Aksiya boshlanishi, Toshkent')} value={local(variant?.saleFrom)} type="datetime-local"/>
      <Field name="saleUntil" title={t('Конец акции, Ташкент', 'Aksiya yakuni, Toshkent')} value={local(variant?.saleUntil)} type="datetime-local"/>
    </div>
    <p className="muted">{t('Артикул — уникальный код комплектации для поиска. После сохранения не меняется.','Artikul — variantni qidirish uchun noyob kod. Saqlangach o‘zgarmaydi.')}</p>
    {variant?.specsReviewRequired && <div className="alert"><p>{t('Старая запись содержит несовместимые характеристики. Проверьте их перед очисткой.','Eski yozuvda mos kelmaydigan xususiyatlar bor. Tozalashdan oldin tekshiring.')}</p>
      {specFields.filter(([, , ,only])=>only && only!==kind).map(([key,ru,uz])=> variant[key] ? <p key={key}>{t(ru,uz)}: {String(variant[key])}</p> : null)}
      <label className="check"><input type="checkbox" name="confirmSpecCleanup" required/>{t('Подтверждаю удаление перечисленных чужих полей при сохранении','Saqlashda ko‘rsatilgan begona maydonlarni o‘chirishni tasdiqlayman')}</label></div>}
    <label className="check"><input name="installment" type="checkbox" defaultChecked={variant?.installment}/>{t('Доступна рассрочка', 'Bo‘lib to‘lash mavjud')}</label>
    <Field name="installmentTerms" title={t('Условия рассрочки', 'Bo‘lib to‘lash shartlari')} type="textarea" value={variant?.installmentTerms} max={1000}/>
    <Field name="notes" title={t('Дополнительные характеристики', 'Qo‘shimcha xususiyatlar')} type="textarea" value={variant?.notes} max={1000}/>
  </AdminForm>
}

function Categories({ onChanged }: { onChanged: () => void }) {
  const { t } = useLocale()
  const [page,setPage]=usePage()
  const data = useResource<Page<Category>>('/admin/categories/page?page='+page)
  const [adding, setAdding] = useState(false)
  const save = () => { data.reload(); onChanged() }
  return <div className="admin-stack"><h2>{t('Категории', 'Toifalar')}</h2>
    {data.error ? <ErrorText error={data.error}/> : data.value ? data.value.content.map(c =>
      <details key={c.id}><summary>{c.nameRu} / {c.nameUz} · {c.enabled ? t('Видна', 'Ko‘rinadi') : t('Скрыта', 'Yashirilgan')}</summary>
        <CategoryForm category={c} onSaved={save}/></details>) : <Loading/>}
    {data.value && <Pager total={data.value.totalElements} page={page} next={page+1<data.value.totalPages} change={setPage}/>}
    {adding ? <CategoryForm onSaved={() => { save(); setAdding(false) }}/> : <button className="button" onClick={() => setAdding(true)}>
      {t('Добавить категорию', 'Toifa qo‘shish')}</button>}
  </div>
}

function CategoryForm({ category, onSaved }: { category?: Category; onSaved: () => void }) {
  const { t } = useLocale()
  return <AdminForm onSave={async data => {
    await mutate('/admin/categories' + (category ? '/' + category.id : ''), category ? 'PATCH' : 'POST', {
      slug: dataText(data, 'slug'), nameRu: dataText(data, 'nameRu'), nameUz: dataText(data, 'nameUz'),
      kind: dataText(data, 'kind'), sortOrder: dataNumber(data, 'sortOrder'), enabled: data.has('enabled'), version: category?.version,
    })
    onSaved()
  }}>
    <div className="form-grid">
      <Field name="slug" title={t('Адрес категории', 'Toifa manzili')} value={category?.slug}
        disabled={!!category && category.kind !== 'GENERAL'} required max={100}/>
      <label className="field">{t('Тип техники','Texnika turi')}<select name="kind" defaultValue={category?.kind ?? 'PHONE'} disabled={!!category}><option value="PHONE">{t('Телефон','Telefon')}</option><option value="LAPTOP">{t('Ноутбук','Noutbuk')}</option></select>{category && <input type="hidden" name="kind" value={category.kind}/>}</label>
      <Field name="nameRu" title={t('Название по-русски', 'Ruscha nom')} value={category?.nameRu} required max={100}/>
      <Field name="nameUz" title={t('Название по-узбекски', 'O‘zbekcha nom')} value={category?.nameUz} required max={100}/>
      <Field name="sortOrder" title={t('Порядок', 'Tartib')} value={category?.sortOrder ?? 0} type="number" required/>
    </div>
    <label className="check"><input name="enabled" type="checkbox" defaultChecked={category?.enabled ?? true}/>{t('Показывать в каталоге', 'Katalogda ko‘rsatish')}</label>
  </AdminForm>
}

function EditSettings({ onChanged }: { onChanged: () => void }) {
  const { t } = useLocale()
  const data = useResource<Settings>('/admin/settings')
  const fields: [keyof Settings, string, string, number][] = [
    ['name','Название магазина','Do‘kon nomi',100], ['phone','Телефон с кодом страны','Mamlakat kodli telefon',30],
    ['telegram','Публичный Telegram','Ochiq Telegram',200], ['address','Адрес','Manzil',250],
    ['mapUrl','HTTPS-ссылка на карту','Xaritaga HTTPS havolasi',1000], ['hours','Часы работы','Ish vaqti',200],
    ['directions','Как проехать','Mo‘ljal',1000],
  ]
  const settings = data.value
  return data.error ? <ErrorText error={data.error}/> : settings ? <AdminForm title={t('Настройки магазина', 'Do‘kon sozlamalari')} onSave={async form => {
    const payload = { ...Object.fromEntries(fields.map(([key]) => [key, dataText(form, key)])), version: settings.version }
    const result = await mutate<Settings>('/admin/settings', 'PATCH', payload)
    data.setValue(result); onChanged()
  }}>
    <div className="form-grid">{fields.map(([key, ru, uz, max]) => <Field name={key} key={key}
      title={t(ru, uz)} value={String(settings[key] ?? '')} max={max} required={key === 'name'} type={key === 'directions' ? 'textarea' : undefined}/>)}</div>
  </AdminForm> : <Loading/>
}


type Lead = {
  id: number; number: string; name: string | null; phone: string | null; productName: string; sku: string; variantLabel: string
  price: number; originalPrice: number | null; status: string; comment: string | null; createdAt: string; closedAt: string | null
  privacyVersion: string; installment: boolean; installmentTerms: string | null; anonymizedAt: string | null
  retentionReview: boolean; version: number; readAt: string | null
}
type History = { actor_id: number; previous_status: string; new_status: string; reason: string | null; created_at: string }
type LeadRecord = { lead: Lead; history: History[] }
const leadStates = ['NEW','CONTACTED','COMPLETED','CANCELLED']

function LeadList() {
  const { t, lang } = useLocale()
  const [page, setPage] = usePage()
  const [params,setParams]=useSearchParams()
  const [newLeads,setNewLeads]=useState(false)
  const data = useResource<Result<Lead>>('/admin/leads?' + params)
  const reload = data.reload
  const latest=useRef<number | undefined>(undefined)
  useEffect(() => {
    const update=(event:Event)=>{ const id=(event as CustomEvent<number>).detail; if(page===0) reload(); else if(latest.current!==undefined && id>latest.current) setNewLeads(true); latest.current=id }
    window.addEventListener('rms-lead-refresh',update)
    return ()=>window.removeEventListener('rms-lead-refresh',update)
  },[page,reload])
  return <>
    <div className="section-head"><h2>{t('Заявки', 'Arizalar')}</h2></div>
    {newLeads && <button className="button" onClick={()=>{setPage(0);setNewLeads(false)}}>{t('Есть обновления заявок — открыть первую страницу','Arizalar yangilandi — birinchi sahifani ochish')}</button>}
    <form className="panel filters" key={params.toString()} onSubmit={event => {
      event.preventDefault()
      const fields = new FormData(event.currentTarget)
      const params = new URLSearchParams()
      fields.forEach((value, key) => { if (typeof value === 'string' && value.trim()) params.set(key, value.trim()) })
      setParams(params); setNewLeads(false)
    }}>
      <Field name="q" title={t('Номер заявки, артикул или телефон', 'Ariza raqami, artikul yoki telefon')} value={params.get('q')} max={80}/>
      <label className="field">{t('Статус', 'Holat')}<select name="status" defaultValue={params.get('status')??''}>
        <option value="">{t('Все', 'Barchasi')}</option>
        {leadStates.map(s => <option key={s} value={s}>{statusName(s, t)}</option>)}
      </select></label>
      <Field name="from" title={t('Дата от', 'Boshlanish sanasi')} type="date" value={params.get('from')}/>
      <Field name="until" title={t('Дата до', 'Yakun sanasi')} type="date" value={params.get('until')}/>
      <button className="button primary">{t('Найти', 'Topish')}</button>
    </form>
    {data.error ? <ErrorText error={data.error}/> : data.value ? <>
      <div className="admin-list">{data.value.items.map(l => <Link className={'admin-row' + (!l.readAt ? ' unread' : '')} to={'/admin/leads/' + l.id} key={l.id}>
        <div><strong>{l.productName} · {l.sku}</strong><p className="muted">{l.variantLabel} · {money(l.price, lang)}</p>
          <p className="muted">{l.name ?? '—'} · {l.phone ?? '—'}</p></div>
        <div className="row-meta">{!l.readAt && <span className="badge">{t('Новая','Yangi')}</span>}<span className="badge">{statusName(l.status, t)}</span>
          <small>{dateLabel(l.createdAt, lang)}</small><small>{l.number}</small></div>
      </Link>)}</div>
      {!data.value.items.length && <Empty title={t('Заявок не найдено', 'Arizalar topilmadi')}/>}
      <Pager total={data.value.totalElements} page={page} next={data.value.hasNext} change={setPage}/>
    </> : <Loading/>}
  </>
}

function LeadDetail({ owner }: { owner: boolean }) {
  const { id } = useParams()
  const { t, lang } = useLocale()
  const data = useResource<LeadRecord>('/admin/leads/' + id)
  const lead = data.value?.lead
  const [readError,setReadError]=useState<unknown>()
  useEffect(()=>{ if(!lead) return; let stopped=false; mutate('/admin/leads/'+lead.id+'/read','POST').then(()=>{if(!stopped) window.dispatchEvent(new Event('rms-lead-read'))}).catch(error=>{if(!stopped)setReadError(error)}); return ()=>{stopped=true} },[lead?.id])
  return data.error ? <ErrorText error={data.error}/> : lead ? <div className="admin-stack">
    <Link className="back" to="/admin/leads">← {t('К заявкам', 'Arizalarga qaytish')}</Link>
    {Boolean(readError) && <ErrorText error={readError}/>}
    <section className="panel">
      <p className="kicker">{t('Заявка', 'Ariza')} · {dateLabel(lead.createdAt, lang)}</p>
      <h2>{lead.productName} · {lead.sku}</h2><p>{lead.number}</p><p>{lead.variantLabel} · {money(lead.price, lang)}</p>
      {lead.originalPrice && <p>{t('Обычная цена', 'Odatiy narx')}: {money(lead.originalPrice, lang)}</p>}
      {lead.installment && <p>{t('Рассрочка', 'Bo‘lib to‘lash')}{lead.installmentTerms && ' · ' + lead.installmentTerms}</p>}
      <p>{lead.name ?? '—'} · {lead.phone ? <a href={'tel:' + lead.phone}>{lead.phone}</a> : '—'}</p>
      <p className="muted">{t('Версия уведомления об обработке данных', 'Ma’lumotlarga ishlov berish xabarnomasi versiyasi')}: {lead.privacyVersion}</p>
      {owner && lead.retentionReview && <p className="alert">{t('Срок хранения открытой заявки требует проверки.', 'Ochiq arizaning saqlash muddatini tekshiring.')}</p>}
    </section>
    {!lead.anonymizedAt && <AdminForm key={lead.version} onSave={async form => {
      data.setValue(await mutate<LeadRecord>('/admin/leads/' + id, 'PATCH', {
        status: dataText(form, 'status'), comment: dataText(form, 'comment'), reason: dataText(form, 'reason'), version: lead.version,
      }))
    }}>
      <label className="field">{t('Статус', 'Holat')}<select name="status" defaultValue={lead.status}>
        {(lead.status === 'NEW' ? ['NEW','CONTACTED','CANCELLED'] : lead.status === 'CONTACTED'
          ? ['CONTACTED','COMPLETED','CANCELLED'] : owner ? [lead.status,'CONTACTED'] : [lead.status])
          .map(s => <option key={s} value={s}>{statusName(s, t)}</option>)}
      </select></label>
      <Field name="comment" title={t('Внутренний комментарий', 'Ichki izoh')} type="textarea" value={lead.comment} max={2000}/>
      <Field name="reason" title={t('Причина, обязательна при повторном открытии', 'Sabab, qayta ochishda majburiy')} max={2000}/>
    </AdminForm>}
    {owner && !lead.anonymizedAt && <AdminForm button={t('Обезличить заявку', 'Arizani shaxssizlantirish')} onSave={async () => {
      await mutate('/admin/leads/' + id + '/anonymize', 'POST'); data.reload()
    }}>
      <label className="check"><input type="checkbox" required/>{t('Удалить имя, телефон и внутренние комментарии', 'Ism, telefon va ichki izohlarni o‘chirish')}</label>
    </AdminForm>}
    <div className="panel"><h2>{t('История', 'Tarix')}</h2>
      <ul className="activity">{data.value?.history.map((h, i) => <li key={i}>{dateLabel(h.created_at, lang)} · #{h.actor_id} ·
        {' '}{statusName(h.previous_status, t)} → {statusName(h.new_status, t)} {h.reason && ' · ' + h.reason}</li>)}</ul>
    </div>
  </div> : <Loading/>
}

function Users() {
  const { t, lang } = useLocale()
  const [page, setPage] = usePage()
  const [issued, setIssued] = useState('')
  const data = useResource<Page<Account>>('/admin/users?page=' + page)
  return <div className="admin-stack"><h2>{t('Администраторы', 'Administratorlar')}</h2>
    <AdminForm title={t('Добавить администратора', 'Administrator qo‘shish')} onSave={async form => {
      const result = await mutate<{ temporaryPassword: string }>('/admin/users', 'POST', {
        name: dataText(form, 'name'), login: dataText(form, 'login'),
      })
      setIssued(result.temporaryPassword); data.reload()
    }}><div className="form-grid">
      <Field name="name" title={t('Имя', 'Ism')} required max={100}/>
      <Field name="login" title={t('Логин', 'Login')} required max={60}/>
    </div></AdminForm>
    {issued && <div className="panel" role="status"><strong>{t('Одноразовый временный пароль', 'Bir martalik vaqtinchalik parol')}</strong>
      <p><code>{issued}</code></p><button className="button" onClick={() => setIssued('')}>{t('Скрыть', 'Yashirish')}</button></div>}
    {data.error ? <ErrorText error={data.error}/> : data.value ? <>
      {data.value.content.map(account => <section className="panel" key={account.id}>
        <h3>{account.name} · {account.login}</h3>
        <p>{account.role === 'OWNER' ? t('Владелец', 'Egasi') : t('Администратор', 'Administrator')} ·
          {' '}{account.enabled ? t('Активен', 'Faol') : t('Отключён', 'O‘chirilgan')}</p>
        <p className="muted">{t('Создан', 'Yaratilgan')}: {dateLabel(account.createdAt, lang)} ·
          {' '}{t('Последний вход', 'Oxirgi kirish')}: {account.lastLogin ? dateLabel(account.lastLogin, lang) : '—'}</p>
        {account.role === 'ADMIN' && <div className="action-grid">
          <AdminForm button={account.enabled ? t('Отключить', 'O‘chirish') : t('Включить', 'Yoqish')} onSave={async () => {
            await mutate('/admin/users/' + account.id, 'PATCH', { enabled: !account.enabled, version: account.version }); data.reload()
          }}>{null}</AdminForm>
          <AdminForm button={t('Сбросить пароль', 'Parolni tiklash')} onSave={async () => {
            const result = await mutate<{ temporaryPassword: string }>('/admin/users/' + account.id + '/reset-password', 'POST')
            setIssued(result.temporaryPassword); data.reload()
          }}>{null}</AdminForm>
        </div>}
      </section>)}
      <Pager total={data.value.totalElements} page={page} next={page + 1 < data.value.totalPages} change={setPage}/>
    </> : <Loading/>}
  </div>
}

function AuditLog() {
  const { t, lang } = useLocale()
  const [page, setPage] = usePage()
  const [params,setParams]=useSearchParams()
  const data = useResource<Result<{ id: number; action: string; actor_id: number | null; actor_username: string | null; actor_role: string | null; object_label: string | null; changes: string; target: string | null; created_at: string }>>('/admin/audit?' + params)
  const actionNames: Record<string, [string, string]> = {
    LOGIN: ['Вход', 'Kirish'], LOGIN_FAILED: ['Неудачный вход', 'Muvaffaqiyatsiz kirish'],
    ADMIN_CREATED: ['Добавление администратора', 'Administrator qo‘shildi'],
    ADMIN_DISABLED: ['Отключение администратора', 'Administrator o‘chirildi'],
    ADMIN_ENABLED: ['Включение администратора', 'Administrator yoqildi'],
    PASSWORD_RESET: ['Сброс пароля', 'Parol tiklandi'], PASSWORD_CHANGED: ['Смена пароля', 'Parol almashtirildi'],
    OWNER_BOOTSTRAPPED: ['Создание владельца', 'Egasi yaratildi'], OWNER_RECOVERED: ['Восстановление владельца', 'Egasi tiklandi'],
    SETTINGS_CHANGED: ['Настройки обновлены', 'Sozlamalar yangilandi'], CATEGORY_SAVED: ['Категория обновлена', 'Toifa yangilandi'],
    PRODUCT_DRAFT: ['Черновик сохранён', 'Qoralama saqlandi'], PRODUCT_PUBLISHED: ['Товар опубликован', 'Mahsulot nashr qilindi'],
    PRODUCT_ARCHIVED: ['Товар в архиве', 'Mahsulot arxivlandi'], VARIANT_SAVED: ['Комплектация обновлена', 'Variant yangilandi'],
    IMAGE_ADDED: ['Фото добавлено', 'Surat qo‘shildi'], IMAGE_REMOVED: ['Фото удалено', 'Surat o‘chirildi'],
    LEAD_COMMENT_CHANGED: ['Комментарий изменён', 'Izoh o‘zgartirildi'],
    LEAD_ANONYMIZED: ['Заявка обезличена', 'Ariza shaxssizlantirildi'],
    NOTIFICATION_RETRY: ['Повторная отправка', 'Qayta yuborish'],
  }
  return <div className="admin-stack"><h2>{t('Журнал действий', 'Amallar jurnali')}</h2>
    <form className="filters" key={params.toString()} onSubmit={event=>{ event.preventDefault(); const q=new URLSearchParams(); new FormData(event.currentTarget).forEach((v,k)=>{if(String(v).trim())q.set(k,String(v).trim())}); setParams(q) }}>
      <Field name="username" title={t('Логин администратора','Administrator logini')} value={params.get('username')} max={60}/>
      <label className="field">{t('Действие','Amal')}<select name="action" defaultValue={params.get('action')??''}><option value="">{t('Все','Barchasi')}</option>{Object.entries(actionNames).map(([code,names])=><option key={code} value={code}>{t(...names)}</option>)}</select></label>
      <Field name="object" title={t('Объект или артикул','Obyekt yoki artikul')} value={params.get('object')} max={120}/>
      <Field name="from" title={t('Дата от','Boshlanish sanasi')} type="date" value={params.get('from')}/><Field name="until" title={t('Дата до','Yakun sanasi')} type="date" value={params.get('until')}/>
      <button className="button">{t('Найти','Topish')}</button>
    </form>
    {data.error ? <ErrorText error={data.error}/> : data.value ? <>
      {data.value.items.map(row => <div className="admin-row" key={row.id}><div>
        <strong>{row.action.startsWith('LEAD_STATUS_') ? statusName(row.action.slice(12), t) : actionNames[row.action] ? t(...actionNames[row.action]) : row.action}</strong>
        <p><strong>{row.actor_username ?? t('Автор не сохранён','Muallif saqlanmagan')}</strong> · {row.actor_role ?? '—'}</p>
        <p className="muted">{row.object_label ?? row.target ?? '—'} · {row.target}</p>
        {row.target?.startsWith('product:') && <Link to={'/admin/products/'+row.target.split(':')[1]}>{t('Открыть товар','Mahsulotni ochish')}</Link>}
        {row.target?.startsWith('variant:') && <Link to={'/admin/products?q='+encodeURIComponent(row.object_label??'')}>{t('Найти комплектацию','Variantni topish')}</Link>}
        {row.target?.startsWith('lead:') && <Link to={'/admin/leads/'+row.target.split(':')[1]}>{t('Открыть заявку','Arizani ochish')}</Link>}
        {row.changes !== '{}' && <details><summary>{t('Изменения','O‘zgarishlar')}</summary><AuditDiff value={row.changes}/></details>}</div><span>{dateLabel(row.created_at, lang)}</span></div>)}
      {!data.value.items.length && <Empty title={t('Ничего не найдено','Hech narsa topilmadi')}/>}
      <Pager total={data.value.totalElements} page={page} next={data.value.hasNext} change={setPage}/>
    </> : <Loading/>}
  </div>
}

function AuditDiff({ value }: { value: string }) {
  const {t}=useLocale()
  const names: Record<string,[string,string]>=Object.fromEntries(specFields.map(([key,ru,uz])=>[key,[ru,uz]]))
  Object.assign(names,{price:['Цена','Narx'],salePrice:['Цена со скидкой','Chegirmali narx'],stock:['Наличие','Mavjudlik'],status:['Статус','Holat'],name:['Название','Nom'],brand:['Бренд','Brend'],categoryId:['Категория','Toifa'],description:['Описание','Tavsif'],enabled:['Включён','Yoqilgan'],installment:['Рассрочка','Bo‘lib to‘lash'],installmentTerms:['Условия рассрочки','Bo‘lib to‘lash shartlari'],sku:['Артикул','Artikul'],saleFrom:['Начало скидки','Chegirma boshlanishi'],saleUntil:['Конец скидки','Chegirma yakuni']})
  let fields: Record<string,{before:unknown;after:unknown}>={}
  try { fields=JSON.parse(value) } catch { return null }
  const text=(v:unknown)=>v===null||v===undefined?'—':typeof v==='boolean'?(v?t('Да','Ha'):t('Нет','Yo‘q')):String(v)
  return <dl className="specs">{Object.entries(fields).map(([key,pair])=><div key={key}><dt>{names[key]?t(...names[key]):key}</dt><dd>{text(pair.before)} → {text(pair.after)}</dd></div>)}</dl>
}

function Notifications() {
  const { t } = useLocale()
  const [page, setPage] = usePage()
  const data = useResource<Result<{ id: number; lead_id: number; status: string; attempts: number; last_error: string | null }> & { enabled: boolean }>('/admin/notifications?page=' + page)
  return <div className="admin-stack"><h2>{t('Уведомления Telegram', 'Telegram xabarlari')}</h2>
    {data.error ? <ErrorText error={data.error}/> : data.value ? <>
      {!data.value.enabled && <p className="alert">{t('Уведомления не подключены', 'Xabarlar ulanmagan')}</p>}
      {data.value.items.map(job => <div className="admin-row" key={job.id}>
        <div><Link to={'/admin/leads/' + job.lead_id}>{t('Заявка', 'Ariza')} #{job.lead_id}</Link>
          <p>{statusName(job.status, t)} · {t('Попытки', 'Urinishlar')}: {job.attempts} {job.last_error && ' · '+job.last_error}</p></div>
        {data.value?.enabled && job.status === 'FAILED' && <AdminForm button={t('Повторить', 'Takrorlash')} onSave={async () => {
          await mutate('/admin/notifications/' + job.id + '/retry', 'POST'); data.reload()
        }}>{null}</AdminForm>}
      </div>)}
      <Pager total={data.value.totalElements} page={page} next={data.value.hasNext} change={setPage}/>
    </> : <Loading/>}
  </div>
}
