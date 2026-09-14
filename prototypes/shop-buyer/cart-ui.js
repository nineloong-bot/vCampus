const baseModal=modal;
modal=function(title,html){$('dialog').classList.remove('basketDialog');baseModal(title,html);};
// Cart selection and checkout preview. Real payment awaits the order/payment API.
let walletRequest=0;
async function loadBalance(){
 const request=++walletRequest,target=$('walletBalance');if(!target)return;
 target.textContent='加载中…';
 try{const result=await window.WalletPort.getBalance();if(request!==walletRequest||!target.isConnected)return;
 target.textContent=result.status==='available'&&Number.isSafeInteger(result.balanceCents)&&result.balanceCents>=0&&result.currency==='CNY'?money(result.balanceCents/100):'暂未接入';
 }catch{if(target.isConnected&&request===walletRequest)target.textContent='余额暂时无法获取';}
}
showCart=function(){
 const previousScroll=document.querySelector('.cartItems')?.scrollTop||0;
 const summary=ShopModel.checkoutSummary(cart,products);
 modal('购物车',`<div class="cartWorkspace"><div class="cartOverview"><span>购物清单 <b>${cart.length}</b></span><label class="cartSelectAll">全选商品 <input type="checkbox" id="cartAll" ${cart.length&&cart.every(r=>r.checked!==false)?'checked':''} ${cart.length?'':'disabled'}></label></div><div class="cartColumns"><span>商品 / 规格</span><span>小计</span><span>操作</span><span>选择</span></div><div class="cartItems">`+(cart.length?cart.map((r,i)=>{const p=products.find(p=>p.id===r.id);return `<article class="basketItem ${r.checked!==false?'isChecked':''}"><div class="basketProduct"><div class="basketImage" style="background:${p.bg}">${p.icon}</div><div class="basketInfo"><span class="muted">${shops[p.shop]}</span><h3>${p.name}</h3><select data-variant="${i}" aria-label="${p.name}规格">${p.variants.map((v,j)=>`<option value="${j}" ${j===r.variant?'selected':''} ${v.stock<r.quantity?'disabled':''}>${v.name} · ${money(v.price)}</option>`).join('')}</select><span class="muted">数量 ${r.quantity}</span></div></div><strong class="basketSubtotal">${money(p.variants[r.variant].price*r.quantity)}</strong><button class="basketRemove" data-remove="${i}">移除</button><input type="checkbox" data-cart-check="${i}" aria-label="选择${p.name}" ${r.checked!==false?'checked':''}></article>`;}).join(''):'<div class="empty">购物车还是空的，去挑选一些好物吧。</div>')+`</div><div class="basketSummary"><div><span class="muted">已选 <b>${summary.count}</b> 件商品</span><div class="basketTotal">合计 <strong>${money(summary.totalCents/100)}</strong></div></div><button id="checkout" class="primary" ${summary.count?'':'disabled'}>去结算 <span>→</span></button></div></div>`);
 $('dialog').classList.add('basketDialog');
 document.querySelector('.cartItems').scrollTop=previousScroll;
 $('cartAll').indeterminate=cart.some(r=>r.checked!==false)&&cart.some(r=>r.checked===false);
 $('checkout').onclick=showCheckout;
};
function showCheckout(){
 const rows=cart.filter(r=>r.checked!==false);if(!rows.length)return;
 const summary=ShopModel.checkoutSummary(rows,products);
 modal('确认结算',rows.map(r=>{const p=products.find(p=>p.id===r.id);return `<div class="cartRow"><div class="content"><strong>${p.name}</strong><p>${p.variants[r.variant].name} × ${r.quantity}</p></div><strong>${money(p.variants[r.variant].price*r.quantity)}</strong></div>`;}).join('')+`<p>应付金额 <strong>${money(summary.totalCents/100)}</strong></p><p>账户余额：<strong id="walletBalance"></strong></p><p class="muted">支付服务暂未接入，商品仍保留在购物车。</p><div class="cartFooter"><button id="returnCart">返回购物车</button><button disabled>确认支付（待接入）</button></div>`);
 $('returnCart').onclick=showCart;loadBalance();
}
const originalCartChange=$('dialogBody').onchange;
$('dialogBody').onchange=e=>{
 if(e.target.id==='cartAll'){cart.forEach(r=>r.checked=e.target.checked);showCart();return;}
 if(e.target.dataset.cartCheck!==undefined){cart[+e.target.dataset.cartCheck].checked=e.target.checked;showCart();return;}
 originalCartChange(e);
};
$('cart').onclick=showCart;
const originalMine=$('mine').onclick;
$('mine').onclick=()=>{originalMine();$('dialogBody').insertAdjacentHTML('afterbegin','<section class="balanceCard"><span>账户余额</span><h2 id="walletBalance">加载中…</h2><p class="muted">账户余额服务</p></section>');loadBalance();};
