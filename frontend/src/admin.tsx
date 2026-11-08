import { useCallback, useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, NavLink, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError, label, mutate } from './api'
import type { Account, Category, Detail, Page, Product, Result, Settings, Variant } from './api'
import { ErrorText, money, statusName, useLocale } from './locale'
import { Empty, Loading } from './ui'
import { Pager } from './storefront'

function useResource<T>(path: string) {
  const [value, setValue] = useState<T>()
  const [error, setError] = useState<unknown>()
  const [revision, refresh] = useState(0)
  useEffect(() => {
    const abort = new AbortController()
    setError(undefined)
    api<T>(path, { signal: abort.signal }).then(setValue).catch(err => {
      if (!abort.signal.aborted) setError(err)
    })
    return () => abort.abort()
  }, [path, revision])
  const reload = useCallback(() => refresh(i => i + 1), [])
  return { value, error, reload, setValue }
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
  if (!me.value) {
    if (me.error instanceof ApiError && me.error.status === 401) {
      return <Login onLogin={() => { me.reload(); navigate('/admin') }}/>
    }
    return me.error ? <ErrorText error={me.error}/> : <Loading/>
  }
  async function logout() {
    await mutate('/auth/logout', 'POST')
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
        <NavLink to="/admin/leads">{t('Заявки', 'Arizalar')}</NavLink>
        <NavLink to="/admin/settings">{t('Настройки', 'Sozlamalar')}</NavLink>
        {owner && <><NavLink to="/admin/users">{t('Администраторы', 'Administratorlar')}</NavLink>
          <NavLink to="/admin/audit">{t('Журнал', 'Jurnal')}</NavLink>
          <NavLink to="/admin/notifications">{t('Уведомления', 'Xabarlar')}</NavLink></>}
        <NavLink to="/admin/password">{t('Пароль', 'Parol')}</NavLink>
      </nav>
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
  const [page, setPage] = useState(0)
  const { value, error } = useResource<Page<Product>>('/admin/products?page=' + page)
  return <>
    <div className="section-head"><h2>{t('Товары', 'Mahsulotlar')}</h2>
      <Link className="button primary" to="/admin/products/new">{t('Добавить товар', 'Mahsulot qo‘shish')}</Link></div>
    {error ? <ErrorText error={error}/> : value ? <>
      <div className="admin-list">{value.content.map(p => <Link to={'/admin/products/' + p.id} className="admin-row" key={p.id}>
        <strong>{p.brand} {p.name}</strong><span className="badge quiet">{statusName(p.status, t)}</span>
      </Link>)}</div>
      {!value.content.length && <Empty title={t('Товаров пока нет', 'Hozircha mahsulotlar yo‘q')}/>}
      <Pager page={page} next={page + 1 < value.totalPages} change={setPage}/>
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
      slug: dataText(data, 'slug'), description: dataText(data, 'description'),
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
      <Field name="slug" title={t('Адрес страницы (например, model-x)', 'Sahifa manzili (masalan, model-x)')} value={product?.slug} required max={150}/>
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
  const { product, variants, images } = data.value
  return <div className="admin-stack">
    <ProductForm product={product} categories={categories} reload={data.reload}/>
    <h2>{t('Комплектации', 'Variantlar')}</h2>
    {variants.map(v => <details key={v.id}><summary>{label(v)}</summary>
      <VariantForm productId={id} variant={v} onSaved={data.reload}/>
    </details>)}
    {addVariant ? <VariantForm productId={id} onSaved={() => { data.reload(); setAddVariant(false) }}/>
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
          {variants.map(v => <option key={v.id} value={v.id}>{label(v)}</option>)}
        </select></label>
      </div>
    </AdminForm>
    <div className="admin-images">{images.map(i => <AdminForm key={i.id}
      button={t('Удалить фото', 'Suratni o‘chirish')} onSave={async () => {
        await mutate('/admin/products/' + id + '/images/' + i.id, 'DELETE'); data.reload()
      }}><img src={i.url.replace('/media/', '/api/v1/admin/media/')} alt={i.alt ?? ''} width={i.width} height={i.height}/></AdminForm>)}</div>
  </div>
}

function VariantForm({ productId, variant, onSaved }: { productId: string; variant?: Variant; onSaved: () => void }) {
  const { t } = useLocale()
  const fields: [keyof Variant, string, string][] = [
    ['color','Цвет','Rang'], ['screen','Экран','Ekran'], ['cpu','Процессор','Protsessor'], ['gpu','GPU','GPU'],
    ['cameras','Камеры','Kameralar'], ['battery','Аккумулятор','Batareya'], ['sim','SIM / eSIM','SIM / eSIM'],
    ['os','ОС','OT'], ['weight','Вес','Vazn'],
  ]
  const local = (value?: string | null) => value ? new Date(Date.parse(value) + 5 * 3600_000).toISOString().slice(0, 16) : ''
  const instant = (value: string) => value ? new Date(value + '+05:00').toISOString() : null
  return <AdminForm onSave={async data => {
    const path = '/admin/products/' + productId + '/variants' + (variant ? '/' + variant.id : '')
    await mutate(path, variant ? 'PATCH' : 'POST', {
      ...Object.fromEntries(fields.map(([key]) => [key, dataText(data, key)])), sku: dataText(data, 'sku'),
      stock: dataText(data, 'stock'), price: dataNumber(data, 'price'), ramGb: dataNumber(data, 'ramGb'),
      storageGb: dataNumber(data, 'storageGb'), installment: data.has('installment'), installmentTerms: dataText(data, 'installmentTerms'),
      notes: dataText(data, 'notes'), salePrice: dataNumber(data, 'salePrice'), saleFrom: instant(dataText(data, 'saleFrom')),
      saleUntil: instant(dataText(data, 'saleUntil')), version: variant?.version,
    })
    onSaved()
  }}>
    <div className="form-grid">
      <Field name="sku" title={t('Артикул SKU', 'SKU artikuli')} value={variant?.sku} required max={80} disabled={!!variant}/>
      <Field name="price" title={t('Обычная цена, UZS', 'Odatiy narx, UZS')} value={variant?.originalPrice ?? variant?.price} type="number" required/>
      <label className="field">{t('Наличие', 'Mavjudlik')}<select name="stock" defaultValue={variant?.stock ?? 'IN_STOCK'}>
        {['IN_STOCK','OUT_OF_STOCK'].map(s => <option key={s} value={s}>{statusName(s, t)}</option>)}
      </select></label>
      <Field name="ramGb" title="RAM, GB" type="number" value={variant?.ramGb}/>
      <Field name="storageGb" title={t('Память, GB', 'Xotira, GB')} type="number" value={variant?.storageGb}/>
      {fields.map(([key, ru, uz]) => <Field key={key} name={key} title={t(ru, uz)} value={String(variant?.[key] ?? '')} max={key === 'cameras' ? 150 : 100}/>)}
      <Field name="salePrice" title={t('Цена со скидкой, UZS', 'Chegirmali narx, UZS')} value={variant?.salePrice} type="number"/>
      <Field name="saleFrom" title={t('Начало акции, Ташкент', 'Aksiya boshlanishi, Toshkent')} value={local(variant?.saleFrom)} type="datetime-local"/>
      <Field name="saleUntil" title={t('Конец акции, Ташкент', 'Aksiya yakuni, Toshkent')} value={local(variant?.saleUntil)} type="datetime-local"/>
    </div>
    <label className="check"><input name="installment" type="checkbox" defaultChecked={variant?.installment}/>{t('Доступна рассрочка', 'Bo‘lib to‘lash mavjud')}</label>
    <Field name="installmentTerms" title={t('Условия рассрочки', 'Bo‘lib to‘lash shartlari')} type="textarea" value={variant?.installmentTerms} max={1000}/>
    <Field name="notes" title={t('Дополнительные характеристики', 'Qo‘shimcha xususiyatlar')} type="textarea" value={variant?.notes} max={1000}/>
  </AdminForm>
}

function Categories({ onChanged }: { onChanged: () => void }) {
  const { t } = useLocale()
  const data = useResource<Category[]>('/admin/categories')
  const [adding, setAdding] = useState(false)
  const save = () => { data.reload(); onChanged() }
  return <div className="admin-stack"><h2>{t('Категории', 'Toifalar')}</h2>
    {data.error ? <ErrorText error={data.error}/> : data.value ? data.value.map(c =>
      <details key={c.id}><summary>{c.nameRu} / {c.nameUz} · {c.enabled ? t('Видна', 'Ko‘rinadi') : t('Скрыта', 'Yashirilgan')}</summary>
        <CategoryForm category={c} onSaved={save}/></details>) : <Loading/>}
    {adding ? <CategoryForm onSaved={() => { save(); setAdding(false) }}/> : <button className="button" onClick={() => setAdding(true)}>
      {t('Добавить категорию', 'Toifa qo‘shish')}</button>}
  </div>
}

function CategoryForm({ category, onSaved }: { category?: Category; onSaved: () => void }) {
  const { t } = useLocale()
  return <AdminForm onSave={async data => {
    await mutate('/admin/categories' + (category ? '/' + category.id : ''), category ? 'PATCH' : 'POST', {
      slug: dataText(data, 'slug'), nameRu: dataText(data, 'nameRu'), nameUz: dataText(data, 'nameUz'),
      sortOrder: dataNumber(data, 'sortOrder'), enabled: data.has('enabled'), version: category?.version,
    })
    onSaved()
  }}>
    <div className="form-grid">
      <Field name="slug" title={t('Адрес категории', 'Toifa manzili')} value={category?.slug}
        disabled={!!category && category.kind !== 'GENERAL'} required max={100}/>
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
  retentionReview: boolean; version: number
}
type History = { actor_id: number; previous_status: string; new_status: string; reason: string | null; created_at: string }
type LeadRecord = { lead: Lead; history: History[] }
const leadStates = ['NEW','CONTACTED','COMPLETED','CANCELLED']

function LeadList() {
  const { t, lang } = useLocale()
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const data = useResource<Result<Lead>>('/admin/leads?page=' + page + query)
  const reload = data.reload
  useEffect(() => {
    const timer = setInterval(() => { if (document.visibilityState === 'visible') reload() }, 12000)
    return () => clearInterval(timer)
  }, [reload])
  return <>
    <div className="section-head"><h2>{t('Заявки', 'Arizalar')}</h2></div>
    <form className="panel filters" onSubmit={event => {
      event.preventDefault()
      const fields = new FormData(event.currentTarget)
      const params = new URLSearchParams()
      fields.forEach((value, key) => { if (typeof value === 'string' && value.trim()) params.set(key, value.trim()) })
      setPage(0); setQuery('&' + params)
    }}>
      <Field name="q" title={t('Номер заявки, SKU или телефон', 'Ariza raqami, SKU yoki telefon')} max={80}/>
      <label className="field">{t('Статус', 'Holat')}<select name="status">
        <option value="">{t('Все', 'Barchasi')}</option>
        {leadStates.map(s => <option key={s} value={s}>{statusName(s, t)}</option>)}
      </select></label>
      <Field name="from" title={t('Дата от', 'Boshlanish sanasi')} type="date"/>
      <Field name="until" title={t('Дата до', 'Yakun sanasi')} type="date"/>
      <button className="button primary">{t('Найти', 'Topish')}</button>
    </form>
    {data.error ? <ErrorText error={data.error}/> : data.value ? <>
      <div className="admin-list">{data.value.items.map(l => <Link className="admin-row" to={'/admin/leads/' + l.id} key={l.id}>
        <div><strong>{l.productName} · {l.sku}</strong><p className="muted">{l.variantLabel} · {money(l.price, lang)}</p>
          <p className="muted">{l.name ?? '—'} · {l.phone ?? '—'}</p></div>
        <div className="row-meta"><span className="badge">{statusName(l.status, t)}</span>
          <small>{dateLabel(l.createdAt, lang)}</small><small>{l.number}</small></div>
      </Link>)}</div>
      {!data.value.items.length && <Empty title={t('Заявок не найдено', 'Arizalar topilmadi')}/>}
      <Pager page={page} next={data.value.hasNext} change={setPage}/>
    </> : <Loading/>}
  </>
}

function LeadDetail({ owner }: { owner: boolean }) {
  const { id } = useParams()
  const { t, lang } = useLocale()
  const data = useResource<LeadRecord>('/admin/leads/' + id)
  const lead = data.value?.lead
  return data.error ? <ErrorText error={data.error}/> : lead ? <div className="admin-stack">
    <Link className="back" to="/admin/leads">← {t('К заявкам', 'Arizalarga qaytish')}</Link>
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
  const [page, setPage] = useState(0)
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
      <Pager page={page} next={page + 1 < data.value.totalPages} change={setPage}/>
    </> : <Loading/>}
  </div>
}

function AuditLog() {
  const { t, lang } = useLocale()
  const [page, setPage] = useState(0)
  const data = useResource<{ id: number; action: string; actor_id: number | null; target: string | null; created_at: string }[]>('/admin/audit?page=' + page)
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
    {data.error ? <ErrorText error={data.error}/> : data.value ? <>
      {data.value.map(row => <div className="admin-row" key={row.id}><div>
        <strong>{row.action.startsWith('LEAD_STATUS_') ? statusName(row.action.slice(12), t) : actionNames[row.action] ? t(...actionNames[row.action]) : row.action}</strong>
        <p className="muted">{row.target ?? '—'} · {row.actor_id ?? '—'}</p></div><span>{dateLabel(row.created_at, lang)}</span></div>)}
      <Pager page={page} next={data.value.length === 24} change={setPage}/>
    </> : <Loading/>}
  </div>
}

function Notifications() {
  const { t } = useLocale()
  const [page, setPage] = useState(0)
  const data = useResource<{ enabled: boolean; items: { id: number; lead_id: number; status: string; attempts: number }[] }>('/admin/notifications?page=' + page)
  return <div className="admin-stack"><h2>{t('Уведомления Telegram', 'Telegram xabarlari')}</h2>
    {data.error ? <ErrorText error={data.error}/> : data.value ? <>
      {!data.value.enabled && <p className="alert">{t('Уведомления не подключены', 'Xabarlar ulanmagan')}</p>}
      {data.value.items.map(job => <div className="admin-row" key={job.id}>
        <div><Link to={'/admin/leads/' + job.lead_id}>{t('Заявка', 'Ariza')} #{job.lead_id}</Link>
          <p>{statusName(job.status, t)} · {t('Попытки', 'Urinishlar')}: {job.attempts}</p></div>
        {data.value?.enabled && job.status === 'FAILED' && <AdminForm button={t('Повторить', 'Takrorlash')} onSave={async () => {
          await mutate('/admin/notifications/' + job.id + '/retry', 'POST'); data.reload()
        }}>{null}</AdminForm>}
      </div>)}
      <Pager page={page} next={data.value.items.length === 24} change={setPage}/>
    </> : <Loading/>}
  </div>
}
