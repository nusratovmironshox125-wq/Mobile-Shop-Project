import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
assert.equal(process.env.RMS_TEST_ONLY,'yes')
assert.equal(process.env.SPRING_DATASOURCE_URL,'jdbc:postgresql://127.0.0.1:55432/rms_load')
const receipts=JSON.parse(readFileSync(process.env.LOAD_OUTPUT+'.receipts.json','utf8'))
const unique=new Map()
for(const receipt of receipts) {
  assert(/^[0-9a-f-]{36}$/.test(receipt.key) && /^[0-9a-f-]{36}$/.test(receipt.number))
  if(unique.has(receipt.key))assert.equal(unique.get(receipt.key),receipt.number)
  unique.set(receipt.key,receipt.number)
}
for(const [key,number] of unique) {
  const count=execFileSync(process.env.RMS_PSQL,['-h','127.0.0.1','-p','55432','-U','rms_test','-d','rms_load','-X','-At','-v','ON_ERROR_STOP=1','-c',
    "select count(*) from lead where idempotency_key='"+key+"' and public_number='"+number+"'"],{encoding:'utf8'}).trim()
  assert.equal(count,'1','Acknowledged request must have exactly one persisted lead')
}
const result={acknowledged:receipts.length,uniqueKeys:unique.size,lost:0,duplicateKeysWithDifferentNumbers:0}
writeFileSync(process.env.LOAD_OUTPUT+'.reconciliation.json',JSON.stringify(result,null,2))
console.log('Receipt reconciliation:',result)
