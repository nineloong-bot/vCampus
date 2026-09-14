(function(root){
 function select(state,id,order,event){
  let ids=[...state.ids],anchor=state.anchor;
  if(event.shiftKey && order.includes(anchor)){
   const a=order.indexOf(anchor),b=order.indexOf(id); ids=order.slice(Math.min(a,b),Math.max(a,b)+1);
  }else if(event.ctrlKey||event.metaKey){ids=ids.includes(id)?ids.filter(x=>x!==id):[...ids,id];anchor=id;}
  else {ids=[id];anchor=id;}
  return {ids,anchor};
 }
 function addToCart(cart,products,quantity=1){
  if(!Number.isSafeInteger(quantity)||quantity<1)throw new RangeError('Quantity must be a positive integer');
  cart=cart.map(x=>({...x}));const skipped=[];
  products.forEach(p=>{const variant=p.defaultVariant,v=p.variants[variant],row=cart.find(x=>x.id===p.id&&x.variant===variant);
   if(!v||v.stock<quantity+(row?row.quantity:0)){skipped.push(p.id);return;}
   if(row)row.quantity+=quantity;else cart.push({id:p.id,variant,quantity});
  });return {cart,skipped};
 }
 function checkoutSummary(cart,products){return cart.filter(r=>r.checked!==false).reduce((s,r)=>({count:s.count+r.quantity,totalCents:s.totalCents+Math.round(products.find(p=>p.id===r.id).variants[r.variant].price*100)*r.quantity}),{count:0,totalCents:0});}
 const api={select,addToCart,checkoutSummary};if(typeof module!=='undefined')module.exports=api;else root.ShopModel=api;
})(globalThis);
