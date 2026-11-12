// Node 22+, run on a generator machine separate from the application.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { readFileSync, createWriteStream, writeFileSync } from 'node:fs'
import { setTimeout as pause } from 'node:timers/promises'
import os from 'node:os'

assert.equal(process.env.RMS_LOAD_ALLOW, 'owned-test-environment')
const origin = new URL(process.env.LOAD_ORIGIN).origin
const mode = process.env.LOAD_MODE ?? 'closed'
assert(['closed','open'].includes(mode))
const clients = Number(process.env.LOAD_CLIENTS ?? 1000)
const rate = Number(process.env.LOAD_RPS ?? 100)
const warmup = Number(process.env.LOAD_WARMUP_SECONDS ?? 600)
const seconds = Number(process.env.LOAD_SECONDS ?? 1800)
assert([clients,rate,seconds].every(n=>Number.isInteger(n)&&n>0) && warmup>=0)
assert(clients<=10000 && rate<=1000)
const output=process.env.LOAD_OUTPUT ?? 'load-results'
const raw=createWriteStream(output+'.ndjson')
const measurements=new Map()
let requests=0, errors=0, refused=0, dropped=0, offered=0, active=0
const receipts=[]
const settings=await fetch(origin+'/api/v1/settings/public').then(r=>r.json())
assert.equal(settings.name,'RMS LOAD ONLY','Seed a separate rms_load database; never run on the shop')
const adminCredentials=JSON.parse(readFileSync(process.env.LOAD_ADMINS_FILE,'utf8'))
assert(adminCredentials.length===10,'Provide ten dedicated test accounts')
let measuredFrom=Infinity, end=Infinity

async function request(kind,path,options={},cookie='',expected=200) {
  const started=performance.now()
  let response, data
  try {
    response=await fetch(origin+'/api/v1'+path,{...options,headers:{...options.headers,...(cookie?{Cookie:cookie}:{})},signal:AbortSignal.timeout(8000)})
    const text=await response.text()
    try { data=text?JSON.parse(text):null } catch { data=null }
  } catch { /* Recorded as timeout/transport error below. */ }
  const elapsed=performance.now()-started
  if(Date.now()>=measuredFrom && started>=measurementClock) {
    requests++
    const status=response?.status??0
    if(status!==expected)errors++
    if(status===429)refused++
    const values=measurements.get(kind)??[]
    values.push(elapsed);measurements.set(kind,values)
    raw.write(JSON.stringify({kind,status,ms:elapsed,at:Date.now()})+'\n')
  }
  return {response,data}
}
let measurementClock=Infinity
async function csrf(session) {
  const {response,data}=await request('csrf','/auth/csrf',{},session.cookie)
  assert.equal(response?.status,200)
  const cookie=response.headers.getSetCookie().find(x=>x.startsWith('JSESSIONID='))
  if(cookie)session.cookie=cookie.split(';')[0]
  session.lastCsrfAt=Date.now()
  return {[data.headerName]:data.token}
}
const adminSessions=[]
for(const credential of adminCredentials) {
  const session={cookie:'',headers:{}}
  const headers=await csrf(session)
  const {response}=await request('login','/auth/login',{method:'POST',headers,body:new URLSearchParams({username:credential.login,password:credential.password})},session.cookie,204)
  assert.equal(response?.status,204)
  const cookie=response.headers.getSetCookie().find(x=>x.startsWith('JSESSIONID='))
  if(cookie)session.cookie=cookie.split(';')[0]
  session.headers=await csrf(session)
  const me=await request('admin','/auth/me',{},session.cookie)
  session.owner=me.data.role==='OWNER'
  const queue= session.owner ? await request('admin','/admin/notifications',{},session.cookie) : null
  assert(!queue?.data.enabled,'Real Telegram must be disabled for this workload')
  adminSessions.push(session)
}
assert(adminSessions.some(s=>s.owner),'At least one test OWNER is required')
// A small set of form sessions. Catalog visitors do not acquire sessions.
const forms=[]
for(let n=0;n<clients;n++) {
  const session={cookie:''}
  session.headers=await csrf(session)
  const {data}=await request('detail','/products/load-model-'+(1+n))
  const v=data.variants[0]
  session.variant=v.id;session.price=v.price
  forms.push(session)
  await pause(180) // Keep preparation below the shared-source CSRF limit.
}
let sequence=0
async function action(index) {
  const roll=Math.random()
  if(roll<.45) return request('catalog','/products?page='+Math.floor(Math.random()*1000)+(index%3===0?'&discounted=true':'')+'&sort='+['new','price-asc','price-desc'][index%3])
  if(roll<.70) return request('detail','/products/load-model-'+(1+Math.floor(Math.random()*10000)))
  if(roll<.90) return request('search','/products?q='+encodeURIComponent('Load model '+(1+Math.floor(Math.random()*10000)))+'&minPrice=1900000&maxPrice=5000000',{headers:{'X-Client-Marker':markers[index%clients]}})
  if(roll<.98) return request('settings',index%2?'/settings/public':'/categories')
  if(roll<.99) { const form=forms[index%forms.length];form.headers=await csrf(form);return }
  const session=forms[index%forms.length]
  // Anonymous form sessions can expire during a long run or its preparation.
  if(Date.now()-session.lastCsrfAt>=15*60*1000)session.headers=await csrf(session)
  const key=randomUUID()
  const payload={name:'Load Test',phone:'+99890'+String(1000000+(++sequence)%8000000),informed:true,privacyVersion:settings.privacyVersion,variantId:session.variant,expectedPrice:session.price}
  const result=await request('lead','/leads',{method:'POST',headers:{...session.headers,'Content-Type':'application/json','Idempotency-Key':key},body:JSON.stringify(payload)},session.cookie,201)
  if(result.response?.status===201) receipts.push({number:result.data.number,key})
}
const markers=Array.from({length:clients},()=>randomUUID())
const start=Date.now()
measuredFrom=start+warmup*1000;measurementClock=performance.now()+warmup*1000
end=measuredFrom+seconds*1000
async function adminLoop(session,index) {
  let tick=0
  while(Date.now()<end) {
    await request('admin-updates','/admin/leads/updates',{},session.cookie)
    const list=await request('admin-list','/admin/leads?page='+(tick%3),{},session.cookie)
    const lead=list.data?.items?.[0]
    if(lead && tick%3===0) {
      await request('admin-detail','/admin/leads/'+lead.id,{},session.cookie)
      await request('admin-read','/admin/leads/'+lead.id+'/read',{method:'POST',headers:session.headers},session.cookie,204)
    }
    if(session.owner && tick%3===0)await request('audit','/admin/audit?page='+(tick%10),{},session.cookie)
    if(tick%6===0) {
      const p=await request('admin-product','/admin/products?q='+encodeURIComponent('Load model '+(9900+index)),{},session.cookie)
      const product=p.data?.content?.[0]
      if(product) {
        const {id,categoryId,brand,name,slug,status,version}=product
        await request('admin-edit','/admin/products/'+id,{method:'PATCH',headers:{...session.headers,'Content-Type':'application/json'},body:JSON.stringify({categoryId,brand,name,slug,status,version,description:'Load edit '+tick})},session.cookie)
      }
    }
    tick++;await pause(10000)
  }
}
const admins=adminSessions.map(adminLoop)
if(mode==='closed') {
  await Promise.all(Array.from({length:clients},async(_,i)=>{
    await pause(Math.random()*10000)
    while(Date.now()<end) {
      if(Date.now()>=measuredFrom)offered++
      await action(i)
      await pause(8000+Math.random()*4000)
    }
  }))
} else {
  const pending=new Set()
  let next=performance.now(),i=0
  while(Date.now()<end) {
    const wait=next-performance.now()
    if(wait>0)await pause(wait)
    next+=1000/rate
    if(Date.now()>=measuredFrom)offered++
    if(active>=Math.max(200,rate*8)) { if(Date.now()>=measuredFrom)dropped++;continue }
    active++
    const job=action(i++).finally(()=>{active--;pending.delete(job)})
    pending.add(job)
  }
  await Promise.all(pending)
}
await Promise.all(admins)
raw.end()
const percentiles=Object.fromEntries([...measurements].map(([kind,values])=>{
  values.sort((a,b)=>a-b)
  const p=q=>values[Math.min(values.length-1,Math.floor(values.length*q))]
  return [kind,{count:values.length,p50:p(.5),p95:p(.95),p99:p(.99)}]
}))
writeFileSync(output+'.receipts.json',JSON.stringify(receipts))
const summary={mode,clients,rate,warmup,seconds,offered,requests,achievedRps:requests/seconds,errors,refused429:refused,dropped,percentiles,
 generator:{node:process.version,platform:process.platform,cpus:os.cpus().length,ram:os.totalmem()},finished:new Date().toISOString(),
 note:'No SLO verdict without server telemetry, committed receipt reconciliation, data sizes, commit and network specification.'}
writeFileSync(output+'.json',JSON.stringify(summary,null,2))
console.log(JSON.stringify(summary,null,2))
process.exitCode=errors/Math.max(1,requests)>=.001||dropped>0?1:0
