import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { deflateSync } from 'node:zlib'

// The test launcher fixes both the port and the database to a separate local fixture.
const origin = 'http://127.0.0.1:18080'
const prefix = '/api/v1'
const ownerLogin = process.env.OWNER_LOGIN
const ownerPassword = process.env.OWNER_PASSWORD
assert(ownerLogin && ownerPassword && process.env.RMS_TEST_ONLY === 'yes', 'Use the isolated test launcher')
const suffix = randomUUID().slice(0, 8)
let checks = 0

class Session {
  cookie = ''
  async call(path, options = {}) {
    const { status = 200, method = 'GET', body, headers = {}, csrf = false, raw = false } = options
    const requestHeaders = new Headers(headers)
    if (csrf) {
      const { token, headerName } = await this.call('/auth/csrf')
      requestHeaders.set(headerName, token)
    }
    if (this.cookie) requestHeaders.set('Cookie', this.cookie)
    const encoded = body && !(body instanceof FormData || body instanceof URLSearchParams)
    if (encoded) requestHeaders.set('Content-Type', 'application/json')
    const response = await fetch(origin + (raw ? '' : prefix) + path, {
      method, headers: requestHeaders, body: encoded ? JSON.stringify(body) : body, redirect: 'manual',
    })
    const cookie = response.headers.getSetCookie().find(it => it.startsWith('JSESSIONID='))
    if (cookie) this.cookie = cookie.split(';')[0]
    const result = raw ? response : await response.text().then(text => text ? JSON.parse(text) : null)
    assert.equal(response.status, status, method + ' ' + path + ' ' + (result?.code ?? ''))
    checks++
    return result
  }
  write(path, method, body, status = 200, headers) {
    return this.call(path, { method, body, status, csrf: true, headers })
  }
  login(username, password) {
    return this.write('/auth/login', 'POST', new URLSearchParams({ username, password }), 204)
  }
}

function image(kind) {
  const width = 600
  const height = 430
  const rows = Buffer.alloc((width * 3 + 1) * height)
  const palette = kind === 'laptop' ? [[37, 50, 48], [64, 119, 119]] : [[24, 53, 45], [55, 148, 113]]
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = y * (width * 3 + 1) + 1 + 3 * x
      const laptop = x >= 114 && x <= 486 && y >= 78 && y <= 322
      const base = x >= 79 && x <= 522 && y >= 320 && y <= 339
      const phone = x >= 208 && x <= 392 && y >= 42 && y <= 390
      let color = [250, 250, 250]
      if (kind === 'laptop' ? laptop || base : phone) {
        const border = kind === 'laptop' ? base || x < 126 || x > 474 || y < 90 || y > 309
          : x < 219 || x > 381 || y < 56 || y > 378
        const wave = Math.sin(x / 52 + y / 49) * 15 + Math.sin(y / 53) * 10
        color = border ? palette[0] : palette[1].map(channel => channel + wave + (height - y) / 11)
      }
      color.forEach((channel, c) => { rows[i + c] = channel })
    }
  }
  function chunk(type, data) {
    const key = Buffer.from(type)
    let crc = -1
    for (const byte of Buffer.concat([key, data])) {
      crc ^= byte
      for (let n = 0; n < 8; n++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
    }
    const size = Buffer.alloc(4)
    size.writeUInt32BE(data.length)
    const checksum = Buffer.alloc(4)
    checksum.writeUInt32BE((crc ^ -1) >>> 0)
    return Buffer.concat([size, key, data, checksum])
  }
  const header = Buffer.alloc(13)
  header.writeUInt32BE(width)
  header.writeUInt32BE(height, 4)
  header[8] = 8; header[9] = 2
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header),
    chunk('IDAT', deflateSync(rows)), chunk('IEND', Buffer.alloc(0))])
}
function upload(file, type) {
  const form = new FormData()
  form.set('file', new Blob([file], { type }), 'test-file')
  return form
}

const visitor = new Session()
const owner = new Session()
const employee = new Session()

const page = await visitor.call('/', { raw: true })
assert.match(await page.text(), /Real Mobil Shop/)
const anonymous = await visitor.call('/api/v1/products', { raw: true })
assert.equal(anonymous.headers.get('Set-Cookie'), null)
await visitor.call('/admin/leads', { status: 401 })
await visitor.call('/leads', { method: 'POST', body: {}, status: 403 })
assert.equal((await visitor.call('/products?size=49')).size, 10)
await visitor.call('/products?minPrice=10&maxPrice=5', { status: 400 })

await owner.login(ownerLogin, ownerPassword)
assert.equal((await owner.call('/auth/me')).role, 'OWNER')
const settings = await owner.call('/admin/settings')
const { privacyVersion } = settings
const { name, phone, telegram, address, directions, mapUrl, hours, version } = settings
const changed = { name: 'Real Mobil Shop · тестовый каталог', phone, telegram, address, directions, mapUrl, hours, version }
await owner.write('/admin/settings', 'PATCH', { ...changed, mapUrl: 'javascript:alert(1)' }, 400)
await owner.write('/admin/settings', 'PATCH', changed)
assert.equal((await visitor.call('/settings/public')).name, changed.name)

const categories = await visitor.call('/categories')
const phones = categories.find(c => c.slug === 'phones')
const laptops = categories.find(c => c.slug === 'laptops')
assert(phones && laptops)
const makeProduct = async (category, slug, title, kind, prices) => {
  const product = { categoryId: category.id, brand: 'DEMO', name: title, slug,
    description: 'Синтетический товар для локальной проверки. <script>throw new Error("XSS")</script>', status: 'DRAFT' }
  const draft = await owner.write('/admin/products', 'POST', product, 201)
  const path = '/admin/products/' + draft.id
  await owner.write(path, 'PATCH', { ...product, version: draft.version, status: 'PUBLISHED' }, 400)
  const variants = []
  for (const [i, price] of prices.entries()) {
    const input = { sku: slug + '-' + i, stock: 'IN_STOCK', price, ramGb: i ? 8 : 4, storageGb: i ? 256 : 128,
      color: 'Graphite', installment: !i, salePrice: i ? null : price - 100000 }
    const variant = await owner.write(path + '/variants', 'POST', input, 201)
    variants.push({ variant, input })
  }
  await owner.write(path + '/images', 'POST', upload('<script>test</script>', 'image/png'), 400)
  const photo = await owner.write(path + '/images', 'POST', upload(image(kind), 'image/png'), 201)
  await visitor.call(photo.url, { raw: true, status: 404 })
  const published = await owner.write(path, 'PATCH', { ...product, version: draft.version, status: 'PUBLISHED' })
  await owner.write(path, 'PATCH', { ...product, version: draft.version, status: 'ARCHIVED' }, 409)
  await owner.write(path + '/images/' + photo.id, 'DELETE', undefined, 400)
  const file = await visitor.call(photo.url, { raw: true })
  assert.match(file.headers.get('content-type'), /image\/jpeg/)
  await file.arrayBuffer()
  return { published, variants, path, slug }
}
const phoneProduct = await makeProduct(phones, 'demo-phone-' + suffix, 'Смартфон тестовый', 'phone', [2800000, 3800000])
const laptopProduct = await makeProduct(laptops, 'demo-laptop-' + suffix, 'Ноутбук тестовый', 'laptop', [5900000])
const first = phoneProduct.variants[0]
const second = phoneProduct.variants[1]
const query = await visitor.call('/products?category=phones&q=DEMO&minPrice=3700000&maxPrice=3900000&sort=price-asc')
assert.equal(query.items.find(it => it.id === phoneProduct.published.id)?.variantId, second.variant.id)
const unicodeSearch = await visitor.call('/products?q=' + encodeURIComponent('смартфон'))
assert(unicodeSearch.items.some(it => it.id === phoneProduct.published.id))
const injection = await visitor.call('/products?q=' + encodeURIComponent("' OR 1=1 --"))
assert.equal(injection.items.length, 0)
const discounted = await visitor.call('/products?discounted=true')
assert.equal(discounted.items.find(it => it.id === phoneProduct.published.id)?.originalPrice, 2800000)
const favorites = await visitor.call('/products/by-ids?ids=' + phoneProduct.published.id + ',' + laptopProduct.published.id)
assert.equal(favorites.items.length, 2)
await visitor.call('/products/' + phoneProduct.slug)
const direct = await visitor.call('/products/' + phoneProduct.slug, { raw: true })
assert.match(await direct.text(), /Real Mobil Shop/)

const key = randomUUID()
const input = { variantId: first.variant.id, expectedPrice: 2700000, name: 'O‘tkir Testov'.replace('‘', '’'),
  phone: '+998 (90) 123-45-67', informed: true, privacyVersion }
await visitor.write('/leads', 'POST', { ...input, expectedPrice: 123 }, 409, { 'Idempotency-Key': randomUUID() })
await visitor.write('/leads', 'POST', { padding: 'x'.repeat(65536) }, 413, { 'Idempotency-Key': randomUUID() })
const { token, headerName } = await visitor.call('/auth/csrf')
const submit = payload => visitor.call('/leads', {
  method: 'POST', body: payload, status: 201, headers: { [headerName]: token, 'Idempotency-Key': key },
})
const [one, two] = await Promise.all([submit(input), submit(input)])
assert.equal(one.number, two.number)
await visitor.write('/leads', 'POST', { ...input, name: 'Другое Имя' }, 409, { 'Idempotency-Key': key })
await visitor.write('/leads', 'POST', { ...input, phone: '+998901234567' }, 409, { 'Idempotency-Key': key })
const list = await owner.call('/admin/leads?q=' + one.number)
assert.equal(list.items.length, 1)
const lead = list.items[0]
assert.equal(lead.sku, first.input.sku.toUpperCase())
assert.equal(lead.price, 2700000)
assert.equal(lead.phone, '+998901234567')
assert.equal(lead.originalPrice, 2800000)
await owner.write('/admin/leads/' + lead.id, 'PATCH', { status: 'CONTACTED', version: lead.version })
await owner.write('/admin/leads/' + lead.id, 'PATCH', { status: 'CANCELLED', version: lead.version }, 409)

await owner.write(phoneProduct.path + '/variants/' + second.variant.id, 'PATCH',
  { ...second.input, stock: 'OUT_OF_STOCK', version: second.variant.version })
await visitor.write('/leads', 'POST', { ...input, variantId: second.variant.id, expectedPrice: second.input.price },
  404, { 'Idempotency-Key': randomUUID() })
const catPayload = { slug: laptops.slug, nameRu: laptops.nameRu, nameUz: laptops.nameUz, sortOrder: laptops.sortOrder }
const hidden = await owner.write('/admin/categories/' + laptops.id, 'PATCH', { ...catPayload, enabled: false, version: laptops.version })
await visitor.call('/products/' + laptopProduct.slug, { status: 404 })
await visitor.call('/products/' + laptopProduct.slug, { raw: true, status: 404 })
await visitor.call('/products?category=laptops', { status: 404 })
await owner.write('/admin/categories/' + laptops.id, 'PATCH', { ...catPayload, enabled: true, version: hidden.version })
await visitor.call('/products/' + laptopProduct.slug)

const login = 'test-' + suffix
const user = await owner.write('/admin/users', 'POST', { login, name: 'Тестовый сотрудник' }, 201)
assert.equal(user.account.role, 'ADMIN')
assert(!Object.hasOwn(user.account, 'passwordHash'))
await employee.login(login, user.temporaryPassword)
await employee.call('/admin/leads', { status: 403 })
const newPassword = randomUUID()
await employee.write('/auth/change-password', 'POST', { currentPassword: user.temporaryPassword, newPassword }, 204)
await employee.login(login, newPassword)
await employee.call('/admin/leads')
await employee.write('/admin/users', 'POST', { login: 'forbidden', name: 'Тест' }, 403)
// FIX regressions: server read receipts, CSRF, immutable snapshots and DTO rules.
await employee.call('/admin/audit', { status: 403 })
await visitor.call('/admin/leads/updates', { status: 401 })
const ownerUnread = (await owner.call('/admin/leads/updates')).unreadCount
const employeeUnread = (await employee.call('/admin/leads/updates')).unreadCount
assert.equal(ownerUnread, employeeUnread)
await owner.call('/admin/leads/' + lead.id)
assert.equal((await owner.call('/admin/leads/updates')).unreadCount, ownerUnread)
await owner.call('/admin/leads/' + lead.id + '/read', {method:'POST',status:403})
await owner.write('/admin/leads/' + lead.id + '/read', 'POST', undefined, 204)
await owner.write('/admin/leads/' + lead.id + '/read', 'POST', undefined, 204)
assert.equal((await owner.call('/admin/leads/updates')).unreadCount, ownerUnread-1)
const otherUpdates = await employee.call('/admin/leads/updates')
assert.equal(otherUpdates.unreadCount, employeeUnread)
assert(!JSON.stringify(otherUpdates).includes('+998'))
assert.equal((await owner.call('/admin/leads/' + lead.id)).lead.status, 'CONTACTED')
assert((await owner.call('/admin/leads?q=' + lead.number)).items[0].readAt)
assert.equal((await employee.call('/admin/leads?q=' + lead.number)).items[0].readAt, null)
await owner.write(phoneProduct.path+'/variants','POST',{...first.input,sku:'BAD-GPU',gpu:'wrong'},400)
await owner.write(laptopProduct.path+'/variants','POST',{...first.input,sku:'BAD-SIM',sim:'wrong'},400)
await owner.write(phoneProduct.path+'/variants','POST',{...first.input,sku:'  '+first.input.sku.toUpperCase()+'  '},409)
assert.equal((await owner.write(phoneProduct.path,'PATCH',{categoryId:laptops.id,brand:phoneProduct.published.brand,name:phoneProduct.published.name,status:phoneProduct.published.status,version:phoneProduct.published.version},400)).code,'PRODUCT_TYPE_IMMUTABLE')
const match = await owner.call('/admin/products?q='+encodeURIComponent(first.input.sku.toLowerCase()))
assert.equal(match.totalElements,1); assert.equal(match.content[0].id,phoneProduct.published.id)
const initialVariant=(await owner.call(phoneProduct.path)).variants.find(v=>v.id===first.variant.id)
const newVariant=await employee.write(phoneProduct.path+'/variants/'+first.variant.id,'PATCH',{...first.input,price:2900000,version:initialVariant.version})
const auditDiff=await owner.call('/admin/audit?username='+login+'&object='+encodeURIComponent(newVariant.sku))
assert.equal(auditDiff.totalElements,1)
const event=auditDiff.items[0];assert.equal(event.actor_username,login);assert.equal(event.actor_id,user.account.id)
assert.deepEqual(JSON.parse(event.changes).price,{before:2800000,after:2900000})
const uniqueName='Boundary-'+suffix
for(let n=0;n<=21;n++){
 if(n) await owner.write('/admin/products','POST',{categoryId:phones.id,brand:'QA',name:uniqueName+n,status:'DRAFT'},201)
 if([0,1,10,11,20,21].includes(n)){
  for(let p=0;p<4;p++){const rows=await owner.call('/admin/products?q='+uniqueName+'&page='+p+'&size=99');assert.equal(rows.totalElements,n);assert.equal(rows.size,10);assert.equal(rows.content.length,Math.max(0,Math.min(10,n-p*10)));}
 }
}
const noSlug=await owner.write('/admin/products','POST',{categoryId:phones.id,brand:'测试',name:'测试'},201)
assert.equal(noSlug.slug,'product-'+noSlug.id)
const renamed=await owner.write('/admin/products/'+noSlug.id,'PATCH',{categoryId:noSlug.categoryId,brand:noSlug.brand,name:'Rename',version:noSlug.version})
assert.equal(renamed.slug,noSlug.slug)
await visitor.call('/products/'+renamed.slug,{status:404})
const concurrent=await Promise.all([1,2].map(()=>owner.write('/admin/products','POST',{categoryId:phones.id,brand:'Same',name:'Same'},201)))
assert.notEqual(concurrent[0].slug,concurrent[1].slug)
await owner.call('/admin/products?page=-1',{status:400})
await owner.call('/admin/audit?page=no',{status:400})
const catsPage=await owner.call('/admin/categories/page');assert.equal(catsPage.size,10)
const failedAudit=await owner.call('/admin/audit?object='+encodeURIComponent('BAD-GPU'));assert.equal(failedAudit.totalElements,0)

for(let n=0;n<121;n++) await employee.call('/admin/leads/updates',{status:n<118?200:429})
assert.equal((await owner.call('/admin/leads/updates')).unreadCount,ownerUnread-1)
const userList = await owner.call('/admin/users')
const currentUser = userList.content.find(account => account.id === user.account.id)
await owner.write('/admin/users/' + currentUser.id, 'PATCH', { enabled: false, version: currentUser.version })
await employee.call('/admin/leads', { status: 401 })
await owner.call('/admin/audit')
const outbox = await owner.call('/admin/notifications')
assert.equal(outbox.enabled, false)
await owner.write('/admin/leads/' + lead.id + '/anonymize', 'POST', undefined, 204)
const anonymized = await owner.call('/admin/leads/' + lead.id)
assert.equal(anonymized.lead.phone, null)
assert.equal(anonymized.lead.name, null)
assert.equal(anonymized.lead.sku, first.input.sku.toUpperCase())

// Enough leads to exercise page 3 and global unread counts in the browser.
for(let n=0;n<20;n++){
 const person=new Session()
 await person.write('/leads','POST',{...input,name:'Page Test',phone:'+99891'+String(2000000+n)},201,{'Idempotency-Key':randomUUID()})
}
const leadPages=await owner.call('/admin/leads?page=2')
assert.equal(leadPages.size,10);assert.equal(leadPages.totalElements,21);assert.equal(leadPages.items.length,1)
console.log('PASS: ' + checks + ' HTTP checks on isolated PostgreSQL')
