import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
assert.equal(process.env.RMS_TEST_ONLY,'yes')
assert.equal(process.env.SPRING_DATASOURCE_URL,'jdbc:postgresql://127.0.0.1:55432/rms_load')
const source=readFileSync('src/main/java/sayt/repository/CatalogQueryRepository.java','utf8')
const template=source.match(/String sql = """([\s\S]*?)"""\.formatted/)[1]
const sale=source.match(/String ACTIVE_SALE = """([\s\S]*?)"""/)[1].replaceAll(':now','CURRENT_TIMESTAMP')
const results=[]
for(const search of [false,true]) {
  const pieces=[sale,"p.status='PUBLISHED' and c.enabled",'',
    search?"and (p.brand || ' ' || p.name) collate pg_catalog.pg_unicode_fast ilike '%Load model 9999%' escape '!'":'',
    '','','','','','created_at desc, id desc']
  let n=0
  const sql=template.replaceAll('%s',()=>pieces[n++]).replaceAll(':take','10').replaceAll(':skip','0')
  assert.equal(n,pieces.length)
  const count=sql.slice(0,sql.indexOf(', matches as ('))+' select count(distinct id) from priced where true'
  for(const [kind,query] of [['page',sql],['count',count]]) {
    const raw=execFileSync(process.env.RMS_PSQL,['-h','127.0.0.1','-p','55432','-U','rms_test','-d','rms_load','-X','-At','-v','ON_ERROR_STOP=1','-c','EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) '+query],{encoding:'utf8'})
    const plan=JSON.parse(raw)[0]
    results.push({kind,search,executionMs:plan['Execution Time'],planningMs:plan['Planning Time'],plan:plan.Plan})
  }
}
writeFileSync(process.env.LOAD_OUTPUT+'.plans.json',JSON.stringify(results,null,2))
console.log('Measured SQL plans on synthetic 10000/20000 fixture:',results.map(({kind,search,executionMs})=>({kind,search,executionMs})))
