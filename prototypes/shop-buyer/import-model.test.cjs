const {test}=require('node:test'),a=require('node:assert/strict'),M=require('./import-model.js');
const row=(id,def='是')=>[id,'记事本','课堂记录','普通白名单商品','book','标准款','19','20',def];
test('complete valid group creates one product with multiple specs',()=>{let r=row('A','否');r[5]='加厚款';const x=M.validate([row('A'),r],[]);a.equal(x.valid.length,1);a.equal(x.valid[0].variants.length,2);});
test('one bad row excludes whole group and preserves line numbers',()=>{let r=row('A','否');r[6]='-1';const x=M.validate([row('A'),r,row('B')],[]);a.equal(x.valid.length,1);a.equal(x.rows[0].status,'同组受影响');a.equal(x.rows[1].line,3);a.equal(x.rows[1].status,'错误');});
test('licensed categories inconsistent fields and defaults block imports',()=>{let r=row('A');r[3]='专项许可类目';a.equal(M.validate([r],[]).valid.length,0);a.equal(M.validate([row('A'),row('A')],[]).valid.length,0);let t=row('A','否');t[1]='另一名称';a.equal(M.validate([row('A'),t],[]).valid.length,0);});
test('empty image allowed, unknown image rejected; duplicates warn only',()=>{let r=row('A');r[4]='';let x=M.validate([r],[{name:'记事本'}]);a.equal(x.valid.length,1);a.ok(x.rows[0].warning);r[4]='invalid';a.equal(M.validate([r],[]).valid.length,0);});
