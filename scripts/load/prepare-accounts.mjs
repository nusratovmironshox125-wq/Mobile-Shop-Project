import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { writeFileSync } from 'node:fs'
assert.equal(process.env.RMS_TEST_ONLY,'yes')
const origin='http://127.0.0.1:18081'
assert.equal((await fetch(origin+'/api/v1/settings/public').then(r=>r.json())).name,'RMS LOAD ONLY')
class Session {
  cookie=''
  async call(path,method='GET',body) {
    const headers={}
    if(method!=='GET') {
      const csrf=await this.call('/auth/csrf')
      headers[csrf.headerName]=csrf.token
    }
    if(this.cookie)headers.Cookie=this.cookie
    if(body && !(body instanceof URLSearchParams))headers['Content-Type']='application/json'
    const r=await fetch(origin+'/api/v1'+path,{method,headers,body:body instanceof URLSearchParams?body:body?JSON.stringify(body):undefined})
    const cookie=r.headers.getSetCookie().find(x=>x.startsWith('JSESSIONID='))
    if(cookie)this.cookie=cookie.split(';')[0]
    assert(r.ok,method+' '+path+' '+r.status)
    return r.status===204?null:r.json()
  }
  login(login,password) { return this.call('/auth/login','POST',new URLSearchParams({username:login,password})) }
}
const owner=new Session()
await owner.login(process.env.OWNER_LOGIN,process.env.OWNER_PASSWORD)
const credentials=[{login:process.env.OWNER_LOGIN,password:process.env.OWNER_PASSWORD}]
for(let n=1;n<10;n++) {
  const login='load-admin-'+n
  const created=await owner.call('/admin/users','POST',{login,name:'Synthetic admin'})
  const employee=new Session()
  await employee.login(login,created.temporaryPassword)
  const password=randomUUID()
  await employee.call('/auth/change-password','POST',{currentPassword:created.temporaryPassword,newPassword:password})
  credentials.push({login,password})
}
writeFileSync(process.env.LOAD_ADMINS_FILE,JSON.stringify(credentials),{mode:0o600})
console.log('Prepared ten synthetic load accounts; credentials saved only in the private temporary fixture')
