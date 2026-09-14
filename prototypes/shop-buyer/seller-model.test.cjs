const {test}=require('node:test');
const assert=require('node:assert/strict');
test('approved owner gets store entry; pending applicant keeps application entry',()=>{
 const {accountEntry}=require('./seller-model.js');
 assert.equal(accountEntry('APPROVED'),'我的店铺');
 assert.equal(accountEntry('PENDING'),'申请开店');
 assert.equal(accountEntry(null),'申请开店');
});
