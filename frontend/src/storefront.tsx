import { createContext, lazy, Suspense, useContext, useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, NavLink, Route, Routes, useLocation, useParams, useSearchParams } from 'react-router-dom'
import { api, ApiError, label, mutate } from './api'
import type { Card, Category, Detail, Result, Settings, Variant } from './api'
import { ErrorText, Locale, money, statusName, useLocale } from './locale'
import type { Lang } from './locale'
import { Brand, Empty, Icon, Loading } from './ui'

const Admin = lazy(() => import('./admin'))
const searchMarker = globalThis.crypto?.randomUUID?.()
const fallback: Settings = { name: 'Real Mobil Shop', phone: null, telegram: null, address: null, directions: null,
  mapUrl: null, hours: null, version: 0, privacyVersion: '', privacyRu: '', privacyUz: '' }
const Shop = createContext({ settings: fallback, categories: [] as Category[], reload: () => {}, favorites: [] as number[],
  toggle: (_id: number) => {}, storage: true })

function loadFavorites(): number[] {
  try {
    const values: unknown = JSON.parse(localStorage.getItem('rms-favorites') ?? '[]')
    return Array.isArray(values) ? [...new Set(values.filter(v => Number.isSafeInteger(v) && v > 0))].slice(0, 100) : []
  } catch { return [] }
}

export function App() {
  const [lang, setLang] = useState<Lang>(document.documentElement.lang === 'uz' ? 'uz' : 'ru')
  const [theme, setTheme] = useState(document.documentElement.dataset.theme ?? 'light')
  const [settings, setSettings] = useState(fallback)
  const [categories, setCategories] = useState<Category[]>([])
  const [favorites, setFavorites] = useState(loadFavorites)
  const [storage, setStorage] = useState(() => {
    try { localStorage.getItem('rms-favorites'); return true } catch { return false }
  })
  const [limit, setLimit] = useState(false)
  const location = useLocation()
  const t = (ru: string, uz: string) => lang === 'uz' ? uz : ru

  function reload() {
    api<Settings>('/settings/public').then(setSettings).catch(() => {})
    api<Category[]>('/categories').then(setCategories).catch(() => {})
  }
  useEffect(() => { reload() }, [])
  useEffect(() => {
    const listener = (event: StorageEvent) => {
      if (event.key === 'rms-favorites' || event.key === null) setFavorites(loadFavorites())
    }
    window.addEventListener('storage', listener)
    return () => window.removeEventListener('storage', listener)
  }, [])
  useEffect(() => {
    document.documentElement.lang = lang
    document.documentElement.dataset.theme = theme
  }, [lang, theme])
  useEffect(() => {
    document.title = settings.name
    const previous = document.head.querySelector('link[rel="canonical"]')
    previous?.remove()
    const canonical = document.createElement('link')
    canonical.rel = 'canonical'
    canonical.href = new URL(location.pathname, window.location.origin).href
    document.head.append(canonical)
  }, [settings.name, location.pathname])
  useEffect(() => { window.scrollTo(0, 0) }, [location.pathname])
  function persist(key: string, value: string) {
    try { localStorage.setItem(key, value) } catch { setStorage(false) }
  }
  function toggle(id: number) {
    if (!favorites.includes(id) && favorites.length >= 100) { setLimit(true); return }
    const next = favorites.includes(id) ? favorites.filter(it => it !== id) : [...favorites, id]
    setFavorites(next)
    setLimit(false)
    persist('rms-favorites', JSON.stringify(next))
  }
  const isAdmin = /^\/admin(?:\/|$)/.test(location.pathname)
  return <Locale.Provider value={{ lang, t }}><Shop.Provider value={{ settings, categories, reload, favorites, toggle, storage }}>
    <a className="skip" href="#content">{t('К содержанию', 'Kontentga o‘tish')}</a>
    <header className="top"><div className="container header-inner">
      <Brand name={settings.name}/>
      <nav className="top-links" aria-label={t('Навигация магазина', 'Do‘kon navigatsiyasi')}>
        <NavLink to="/" end>{t('Каталог', 'Katalog')}</NavLink>
        <NavLink to="/discounts">{t('Скидки', 'Chegirmalar')}</NavLink>
        <NavLink to="/contacts">{t('Контакты', 'Aloqa')}</NavLink>
      </nav>
      <div className="top-actions">
        <NavLink className="icon-btn favorite-nav" to="/favorites" aria-label={t('Избранное', 'Saralanganlar')}>
          <Icon name="heart"/>{favorites.length > 0 && <span>{favorites.length}</span>}
        </NavLink>
        <button className="icon-btn language" aria-label={t('Выбрать узбекский', 'Rus tilini tanlash')} onClick={() => {
          const next = lang === 'ru' ? 'uz' : 'ru'; setLang(next); persist('rms-language', next)
        }}>{lang.toUpperCase()}</button>
        <button className="icon-btn" aria-label={t('Переключить тему', 'Mavzuni almashtirish')} aria-pressed={theme === 'dark'}
          onClick={() => { const next = theme === 'dark' ? 'light' : 'dark'; setTheme(next); persist('rms-theme', next) }}>
          <Icon name={theme === 'dark' ? 'sun' : 'moon'}/>
        </button>
      </div>
    </div></header>
    <main className="container main" id="content">
      {limit && <p className="alert" role="status">{t('В избранном можно сохранить до 100 моделей.', '100 tagacha modelni saqlash mumkin.')}</p>}
      <Suspense fallback={<Loading/>}><Routes>
        <Route path="/" element={<CatalogPage/>}/>
        <Route path="/catalog/:category" element={<CatalogPage/>}/>
        <Route path="/discounts" element={<CatalogPage discount/>}/>
        <Route path="/products/:slug" element={<ProductPage/>}/>
        <Route path="/favorites" element={<Favorites/>}/>
        <Route path="/contacts" element={<ContactsPage/>}/>
        <Route path="/privacy" element={<Privacy/>}/>
        <Route path="/admin/*" element={<Admin onShopChange={reload}/>}/>
        <Route path="*" element={<Missing/>}/>
      </Routes></Suspense>
    </main>
    <footer className="footer"><div className="container footer-inner">
      <div><Brand name={settings.name}/><p>{t('Телефоны и ноутбуки', 'Telefonlar va noutbuklar')}</p></div>
      <ContactLinks/>
      <nav aria-label={t('Ссылки в подвале', 'Quyi havolalar')}>
        <Link to="/contacts">{t('Контакты', 'Aloqa')}</Link>
        <Link to="/privacy">{t('Обработка данных', 'Ma’lumotlarga ishlov berish')}</Link>
        <Link to={isAdmin ? '/admin' : '/admin/login'}>{t('Вход для сотрудников', 'Xodimlar uchun kirish')}</Link>
      </nav>
    </div></footer>
  </Shop.Provider></Locale.Provider>
}

function CatalogTabs() {
  const { t, lang } = useLocale()
  const { categories } = useContext(Shop)
  return <nav className="tabs" aria-label={t('Категории каталога', 'Katalog toifalari')}>
    <NavLink to="/" end><Icon name="grid"/>{t('Все товары', 'Barcha mahsulotlar')}</NavLink>
    {categories.map(c => <NavLink key={c.id} to={'/catalog/' + c.slug}><Icon name={c.kind === 'PHONE' ? 'phone' : c.kind === 'LAPTOP' ? 'laptop' : 'grid'}/>
      {lang === 'uz' ? c.nameUz : c.nameRu}</NavLink>)}
    <NavLink to="/discounts"><Icon name="tag"/>{t('Скидки', 'Chegirmalar')}</NavLink>
  </nav>
}

function CatalogPage({ discount = false }: { discount?: boolean }) {
  const { category } = useParams()
  const { categories, settings } = useContext(Shop)
  const { t, lang } = useLocale()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const min = params.get('minPrice') ?? ''
  const max = params.get('maxPrice') ?? ''
  const sort = params.get('sort') ?? 'new'
  const page = Number(params.get('page') ?? 0)
  const [search, setSearch] = useState(q)
  const [minimum, setMinimum] = useState(min)
  const [maximum, setMaximum] = useState(max)
  const [response, setResponse] = useState<Result<Card>>()
  const [error, setError] = useState<unknown>()
  const initial = !category && !discount

  useEffect(() => { setSearch(q); setMinimum(min); setMaximum(max) }, [q, min, max, location.pathname])
  useEffect(() => {
    if (search === q) return
    const timer = setTimeout(() => setParams(previous => {
      const next = new URLSearchParams(previous)
      const value = search.trim()
      if (value) next.set('q', value); else next.delete('q')
      next.delete('page')
      return next
    }, { replace: true }), 300)
    return () => clearTimeout(timer)
  }, [search, q, setParams])
  useEffect(() => {
    const abort = new AbortController()
    const query = new URLSearchParams()
    for (const key of ['q','minPrice','maxPrice','sort','page']) {
      const value = params.get(key)
      if (value) query.set(key, value)
    }
    if (category) query.set('category', category)
    if (discount) query.set('discounted', 'true')
    setResponse(undefined); setError(undefined)
    api<Result<Card>>('/products?' + query, {
      signal: abort.signal, headers: searchMarker ? { 'X-Client-Marker': searchMarker } : undefined,
    }).then(setResponse).catch(error => {
      if (!abort.signal.aborted) setError(error)
    })
    return () => abort.abort()
  }, [params, category, discount])
  function change(values: Record<string, string>) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      next.delete('page')
      for (const [key, value] of Object.entries(values)) {
        if (value) next.set(key, value); else next.delete(key)
      }
      return next
    })
  }
  function reset() { setSearch(''); setMinimum(''); setMaximum(''); setParams({}) }
  const found = categories.find(c => c.slug === category)
  const title = discount ? t('Скидки', 'Chegirmalar') : found ? (lang === 'uz' ? found.nameUz : found.nameRu)
    : t('Каталог товаров', 'Mahsulotlar katalogi')
  if (error instanceof ApiError && error.status === 404) return <Missing/>

  return <>
    {initial && <section className="hero" aria-labelledby="hero-title">
      <div className="hero-content">
        <p className="eyebrow"><span/> {settings.name}</p>
        <h1 id="hero-title">{t('Техника на каждый день', 'Har kun uchun texnika')}</h1>
        <p>{t('Найдите нужную модель, выберите комплектацию и оставьте заявку.', 'Kerakli model va variantni tanlang, so‘ng ariza qoldiring.')}</p>
        <a className="hero-action" href="#catalog">{t('Смотреть каталог', 'Katalogni ko‘rish')} <Icon name="arrow"/></a>
      </div>
      <img className="hero-art" src="/catalog-hero.svg" width="520" height="320" alt=""/>
    </section>}
    <section className="catalog" id="catalog">
      <CatalogTabs/>
      <div className="section-head">
        <div><p className="kicker">{t('Витрина магазина', 'Do‘kon vitrini')}</p>{initial ? <h2>{title}</h2> : <h1>{title}</h1>}</div>
        <label className="sort"><span className="sr-only">{t('Сортировка', 'Saralash')}</span>
          <select value={sort} onChange={e => change({ sort: e.target.value })}>
            <option value="new">{t('Сначала новые', 'Avval yangilari')}</option>
            <option value="price-asc">{t('Сначала дешевле', 'Avval arzonlari')}</option>
            <option value="price-desc">{t('Сначала дороже', 'Avval qimmatlari')}</option>
          </select>
        </label>
      </div>
      <form className="filter" onSubmit={e => { e.preventDefault(); change({ q: search.trim(), minPrice: minimum, maxPrice: maximum }) }}>
        <label className="search"><Icon name="search"/><span className="sr-only">{t('Поиск по модели или бренду', 'Model yoki brend bo‘yicha qidiruv')}</span>
          <input type="search" value={search} maxLength={100} onChange={e => setSearch(e.target.value)}
            placeholder={t('Поиск по модели или бренду', 'Model yoki brend bo‘yicha qidiruv')}/>
        </label>
        <label className="price-field"><span>{t('Цена от', 'Narx dan')}</span><input type="number" min="0" max="99999999999999" step=".01"
          value={minimum} placeholder="0" onChange={e => setMinimum(e.target.value)}/></label>
        <label className="price-field"><span>{t('Цена до', 'Narx gacha')}</span><input type="number" min="0" max="99999999999999" step=".01"
          value={maximum} placeholder="—" onChange={e => setMaximum(e.target.value)}/></label>
        <button className="button primary">{t('Найти', 'Topish')}</button>
        {(q || min || max || sort !== 'new') && <button className="button text" type="button" onClick={reset}>{t('Сбросить', 'Tozalash')}</button>}
      </form>
      {error ? <ErrorText error={error}/> : response ? response.items.length > 0 ? <>
        <div className="grid">{response.items.map(item => <ProductCard item={item} key={item.id}/>)}</div>
        <Pager page={page} next={response.hasNext} change={next => change({ page: String(next) })}/>
      </> : <Empty title={t('Товары не найдены', 'Mahsulotlar topilmadi')}>
        <p>{q || min || max ? t('Попробуйте изменить поиск или диапазон цен.', 'Qidiruv yoki narx oralig‘ini o‘zgartirib ko‘ring.')
          : t('Здесь появятся товары после публикации.', 'Nashr qilingan mahsulotlar shu yerda ko‘rinadi.')}</p>
        {(q || min || max) && <button className="button" onClick={reset}>{t('Сбросить фильтры', 'Filtrlarni tozalash')}</button>}
      </Empty> : <Loading/>}
    </section>
  </>
}

export function Pager({ page, next, change }: { page: number; next: boolean; change: (page: number) => void }) {
  const { t } = useLocale()
  if (!page && !next) return null
  return <nav className="pager" aria-label={t('Страницы', 'Sahifalar')}>
    <button className="button" disabled={!page} onClick={() => change(page - 1)}>{t('Назад', 'Orqaga')}</button>
    <span>{page + 1}</span>
    <button className="button" disabled={!next} onClick={() => change(page + 1)}>{t('Дальше', 'Keyingi')}</button>
  </nav>
}

function FavoriteButton({ id }: { id: number }) {
  const { favorites, toggle } = useContext(Shop)
  const { t } = useLocale()
  const saved = favorites.includes(id)
  return <button className={'icon-btn favorite' + (saved ? ' saved' : '')} type="button" aria-pressed={saved}
    aria-label={saved ? t('Убрать из избранного', 'Saralanganlardan olib tashlash') : t('В избранное', 'Saralanganlarga qo‘shish')}
    onClick={() => toggle(id)}><Icon name="heart"/></button>
}

function ProductCard({ item }: { item: Card }) {
  const { t, lang } = useLocale()
  const location = useLocation()
  const productUrl = '/products/' + item.slug + '?variant=' + item.variantId
  const back = location.pathname + location.search
  return <article className="product-card">
    <div className="card-top">
      <Link to={productUrl} state={{ back }} className="card-image" aria-label={item.brand + ' ' + item.name}>
        {item.image ? <img src={item.image} width="440" height="340" alt="" loading="lazy" decoding="async"/> : <Icon name="picture"/>}
      </Link>
      <FavoriteButton id={item.id}/>
      {item.originalPrice && <span className="badge discount">{t('Скидка', 'Chegirma')}</span>}
    </div>
    <div className="card-body">
      <p className="kicker">{item.brand}</p>
      <h3><Link to={productUrl} state={{ back }}>{item.name}</Link></h3>
      {(item.storageGb || item.color) && <p className="muted card-variant">{[item.storageGb && item.storageGb + ' GB', item.color].filter(Boolean).join(' · ')}</p>}
      <div className="card-price"><strong>{item.variantCount > 1 && !item.originalPrice ? t('от ', 'dan ') : ''}{money(item.price, lang)}</strong>
        {item.originalPrice && <del>{money(item.originalPrice, lang)}</del>}</div>
      {item.installment && <span className="badge">{t('Рассрочка', 'Bo‘lib to‘lash')}</span>}
      {item.stock === 'OUT_OF_STOCK' && <span className="badge quiet">{statusName(item.stock, t)}</span>}
    </div>
  </article>
}

function ProductPage() {
  const { slug } = useParams()
  const [params, setParams] = useSearchParams()
  const [detail, setDetail] = useState<Detail>()
  const [error, setError] = useState<unknown>()
  const { settings } = useContext(Shop)
  const { lang, t } = useLocale()
  const { state } = useLocation()
  const [selectedPhoto, setSelectedPhoto] = useState<number>()
  useEffect(() => {
    const controller = new AbortController()
    setDetail(undefined); setError(undefined)
    api<Detail>('/products/' + encodeURIComponent(slug ?? ''), { signal: controller.signal }).then(setDetail).catch(error => {
      if (!controller.signal.aborted) setError(error)
    })
    return () => controller.abort()
  }, [slug])
  useEffect(() => { if (detail) document.title = detail.product.brand + ' ' + detail.product.name + ' · ' + settings.name }, [detail, settings.name])
  if (error instanceof ApiError && error.status === 404) return <Missing/>
  if (error) return <ErrorText error={error}/>
  if (!detail) return <Loading/>
  const { product, variants, images } = detail
  const selected = variants.find(v => String(v.id) === params.get('variant')) ??
    variants.find(v => v.stock === 'IN_STOCK') ?? variants[0]
  if (!selected) return <Missing/>
  const gallery = [...images.filter(i => i.variantId === selected.id), ...images.filter(i => i.variantId === null)]
  const visible = gallery.length ? gallery : images
  const photo = visible.find(i => i.id === selectedPhoto) ?? visible[0]
  const back = typeof state?.back === 'string' && /^\/(?:$|\?|catalog\/|discounts|favorites)/.test(state.back) ? state.back : '/'
  const specs: [string, ReactNode][] = [
    [t('Цвет', 'Rang'), selected.color], ['RAM', selected.ramGb && selected.ramGb + ' GB'],
    [t('Память', 'Xotira'), selected.storageGb && selected.storageGb + ' GB'], [t('Экран', 'Ekran'), selected.screen],
    [t('Процессор', 'Protsessor'), selected.cpu], ['GPU', selected.gpu], [t('Камеры', 'Kameralar'), selected.cameras],
    [t('Аккумулятор', 'Batareya'), selected.battery], ['SIM / eSIM', selected.sim], [t('ОС', 'OT'), selected.os],
    [t('Вес', 'Vazn'), selected.weight], [t('Дополнительно', 'Qo‘shimcha'), selected.notes],
  ].filter(entry => entry[1]) as [string, ReactNode][]
  return <div className="product-page">
    <Link className="back" to={back}><Icon name="back"/> {t('Назад в каталог', 'Katalogga qaytish')}</Link>
    <div className="detail-grid">
      <div className="gallery">
        <div className="detail-photo">{photo ? <img src={photo.url} width={photo.width} height={photo.height} alt={photo.alt || product.name}/>
          : <Icon name="picture"/>}</div>
        {visible.length > 1 && <div className="thumbs">{visible.map((i, index) =>
          <button key={i.id} type="button" aria-label={t('Фото ', 'Surat ') + (index + 1)} aria-pressed={i.id === photo?.id}
            onClick={() => setSelectedPhoto(i.id)}><img src={i.url} width="64" height="64" alt=""/></button>)}</div>}
      </div>
      <div className="detail-body">
        <div className="section-head"><div><p className="kicker">{product.brand}</p><h1>{product.name}</h1></div><FavoriteButton id={product.id}/></div>
        <label className="field">{t('Комплектация', 'Variant')}
          <select value={selected.id} onChange={e => { setSelectedPhoto(undefined); setParams({ variant: e.target.value }, { replace: true, state }) }}>
            {variants.map(v => <option value={v.id} key={v.id}>{label(v)}</option>)}
          </select>
        </label>
        <p className="muted sku">SKU <strong>{selected.sku}</strong></p>
        <div className="detail-price"><strong>{money(selected.price, lang)}</strong>
          {selected.originalPrice && <del>{money(selected.originalPrice, lang)}</del>}</div>
        <div className="badges"><span className={'badge ' + (selected.stock === 'OUT_OF_STOCK' ? 'quiet' : '')}>{statusName(selected.stock, t)}</span>
          {selected.installment && <span className="badge">{t('Рассрочка', 'Bo‘lib to‘lash')}</span>}</div>
        {selected.installmentTerms && <p className="copy muted">{selected.installmentTerms}</p>}
        {product.description && <p className="copy">{product.description}</p>}
        {specs.length > 0 && <dl className="specs">{specs.map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>}
        {selected.stock === 'IN_STOCK' ? <LeadForm variant={selected} onPriceChanged={(price, originalPrice) =>
          setDetail(previous => previous && { ...previous, variants: previous.variants.map(v =>
            v.id === selected.id ? { ...v, price, originalPrice } : v) })}/> :
          <p className="muted">{t('Заявка появится, когда комплектация будет в наличии.', 'Variant mavjud bo‘lganda ariza qoldirish mumkin.')}</p>}
        <div className="contact-under"><ContactLinks/></div>
      </div>
    </div>
  </div>
}

function LeadForm({ variant, onPriceChanged }: { variant: Variant; onPriceChanged: (price: number, originalPrice: number | null) => void }) {
  const { t, lang } = useLocale()
  const { settings } = useContext(Shop)
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [informed, setInformed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [number, setNumber] = useState('')
  const retry = useRef<{ input: string; key: string } | null>(null)
  const price = variant.price
  async function submit(e: FormEvent) {
    e.preventDefault()
    if (busy) return
    setBusy(true); setError(undefined)
    const payload = { name, phone, informed, expectedPrice: price, variantId: variant.id, privacyVersion: settings.privacyVersion }
    const input = JSON.stringify(payload)
    if (retry.current?.input !== input) retry.current = { input, key: crypto.randomUUID() }
    try {
      const result = await mutate<{ number: string }>('/leads', 'POST', payload, retry.current.key)
      setNumber(result.number); setName(''); setPhone(''); setInformed(false); retry.current = null
    } catch (error) {
      setError(error)
      if (error instanceof ApiError && error.code === 'PRICE_CHANGED' && typeof error.details.currentPrice === 'number') {
        onPriceChanged(error.details.currentPrice,
          typeof error.details.originalPrice === 'number' ? error.details.originalPrice : null)
      }
    } finally { setBusy(false) }
  }
  if (number) return <div className="success" role="status"><Icon name="check"/><div>
    <strong>{t('Магазин свяжется с вами', 'Do‘kon siz bilan bog‘lanadi')}</strong>
    <p>{t('Номер заявки', 'Ariza raqami')}: <code>{number}</code></p>
  </div></div>
  if (!open) return <button className="button primary lead-button" onClick={() => setOpen(true)}>{t('Оставить заявку', 'Ariza qoldirish')} <Icon name="arrow"/></button>
  return <form className="lead-form panel" onSubmit={submit}>
    <h2>{t('Оставить заявку', 'Ariza qoldirish')}</h2>
    <p className="muted">{label(variant)} · {money(price, lang)}</p>
    {Boolean(error) && <ErrorText error={error}/>}
    <label className="field">{t('Ваше имя', 'Ismingiz')}<input name="name" autoComplete="name" value={name} required minLength={2} maxLength={80}
      onChange={e => setName(e.target.value)}/></label>
    <label className="field">{t('Телефон', 'Telefon')}<input name="phone" type="tel" autoComplete="tel" value={phone} required maxLength={45}
      placeholder="+998" onChange={e => setPhone(e.target.value)}/></label>
    <label className="check"><input type="checkbox" required checked={informed} onChange={e => setInformed(e.target.checked)}/>
      <span>{t('Я ознакомился с информацией об обработке данных.', 'Ma’lumotlarga ishlov berish tartibi bilan tanishdim.')} {' '}
        <Link target="_blank" to="/privacy">{t('Подробнее', 'Batafsil')}</Link></span></label>
    <button className="button primary" disabled={busy || !settings.privacyVersion}>{busy ? t('Отправляем…', 'Yuborilmoqda…')
      : t('Отправить заявку', 'Ariza yuborish')}</button>
  </form>
}

function Favorites() {
  const { favorites, toggle, storage } = useContext(Shop)
  const { t } = useLocale()
  const [result, setResult] = useState<Result<Card>>()
  const [error, setError] = useState<unknown>()
  const ids = favorites.join(',')
  useEffect(() => {
    setResult(undefined); setError(undefined)
    if (!ids) return
    const abort = new AbortController()
    api<Result<Card>>('/products/by-ids?ids=' + ids, { signal: abort.signal }).then(setResult).catch(error => {
      if (!abort.signal.aborted) setError(error)
    })
    return () => abort.abort()
  }, [ids])
  return <section className="subpage">
    <div className="section-head"><h1>{t('Избранное', 'Saralanganlar')}</h1></div>
    {!storage && <p className="alert">{t('Браузер не сохраняет избранное после закрытия страницы.', 'Brauzer sahifa yopilgach saralanganlarni saqlamaydi.')}</p>}
    {!ids ? <Empty title={t('В избранном пока пусто', 'Saralanganlar hozircha bo‘sh')}>
      <p>{t('Нажмите на сердце в карточке, чтобы сохранить модель.', 'Modelni saqlash uchun yurakchani bosing.')}</p>
      <Link className="button primary" to="/">{t('Открыть каталог', 'Katalogni ochish')}</Link>
    </Empty> : error ? <ErrorText error={error}/> : result ? <div className="grid">
      {favorites.map(id => {
        const item = result.items.find(it => it.id === id)
        return item ? <ProductCard key={id} item={item}/> : <article className="panel" key={id}>
          <p>{t('Товар недоступен', 'Mahsulot mavjud emas')}</p><button className="button" onClick={() => toggle(id)}>{t('Удалить', 'Olib tashlash')}</button>
        </article>
      })}
    </div> : <Loading/>}
  </section>
}

function ContactLinks() {
  const { settings } = useContext(Shop)
  const { t } = useLocale()
  return <div className="contact-links">
    {settings.phone && <a href={'tel:' + settings.phone.replace(/[^+0-9]/g, '')}>{settings.phone}</a>}
    {settings.address && <span>{settings.address}</span>}
    {settings.telegram && <a href={settings.telegram} target="_blank" rel="noopener noreferrer">Telegram</a>}
    {settings.mapUrl && <a href={settings.mapUrl} target="_blank" rel="noopener noreferrer">{t('Открыть на карте', 'Xaritada ochish')}</a>}
  </div>
}

function ContactsPage() {
  const { settings } = useContext(Shop)
  const { t } = useLocale()
  return <section className="subpage">
    <p className="kicker">{settings.name}</p><h1>{t('Контакты', 'Aloqa')}</h1>
    <div className="panel contact-panel"><span className="empty-icon"><Icon name="pin"/></span>
      <ContactLinks/>
      {settings.hours && <p>{t('Часы работы', 'Ish vaqti')}: {settings.hours}</p>}
      {settings.directions && <p className="copy">{settings.directions}</p>}
      {!settings.phone && !settings.address && !settings.telegram && !settings.mapUrl && <p className="muted">{t('Контакты ещё не добавлены.', 'Aloqa ma’lumotlari hali kiritilmagan.')}</p>}
    </div>
  </section>
}

function Privacy() {
  const { settings } = useContext(Shop)
  const { lang, t } = useLocale()
  return <section className="subpage narrow"><h1>{t('Обработка данных', 'Ma’lumotlarga ishlov berish')}</h1>
    <p className="copy">{lang === 'uz' ? settings.privacyUz : settings.privacyRu}</p><ContactLinks/>
  </section>
}

function Missing() {
  const { t } = useLocale()
  return <Empty title={t('Страница недоступна', 'Sahifa mavjud emas')}>
    <Link className="button primary" to="/">{t('Вернуться в каталог', 'Katalogga qaytish')}</Link>
  </Empty>
}
