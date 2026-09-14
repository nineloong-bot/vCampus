const {test}=require('node:test');
const assert=require('node:assert/strict');
const {select,addToCart}=require('./model.js');
test('detail quantity adds exact units and respects stock and integer validation',()=>{
 const p={id:7,defaultVariant:0,variants:[{stock:5}]};
 assert.deepEqual(addToCart([], [p],3).cart,[{id:7,variant:0,quantity:3}]);
 assert.deepEqual(addToCart([{id:7,variant:0,quantity:3}],[p],3).skipped,[7]);
 for(const q of [0,-1,1.5,NaN])assert.throws(()=>addToCart([],[p],q));
});
test('checkout totals include only checked rows, in cents',()=>{
 const {checkoutSummary}=require('./model.js');
 const products=[{id:1,variants:[{price:0.1}]},{id:2,variants:[{price:20}]}];
 assert.deepEqual(checkoutSummary([{id:1,variant:0,quantity:3},{id:2,variant:0,quantity:2,checked:false}],products),{count:3,totalCents:30});
 assert.deepEqual(checkoutSummary([{id:1,variant:0,quantity:3,checked:false}],products),{count:0,totalCents:0});
});
test('Shift selects range; Ctrl toggles discrete rows',()=>{
 let s=select({ids:[],anchor:null},2,[1,2,3,4,5],{});
 s=select(s,4,[1,2,3,4,5],{shiftKey:true});
 assert.deepEqual(s.ids,[2,3,4]);
 s=select(s,5,[1,2,3,4,5],{ctrlKey:true});
 s=select(s,3,[1,2,3,4,5],{ctrlKey:true});
 assert.deepEqual(s.ids,[2,4,5]);
});
test('cart uses default variant and skips unavailable items',()=>{
 const products=[{id:1,defaultVariant:1,variants:[{stock:2},{stock:3}]},{id:2,defaultVariant:0,variants:[{stock:0}]}];
 const result=addToCart([],products);
 assert.deepEqual(result.cart,[{id:1,variant:1,quantity:1}]);
 assert.deepEqual(result.skipped,[2]);
});
