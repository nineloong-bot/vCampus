function showProductDetail(p){
 const v=p.variants[p.defaultVariant];
 modal(p.name,`<div class="art" style="--bg:${p.bg};height:200px"><span class="object">${p.icon}</span></div><p>${escapeHTML(shops[p.shop])} · 已售 ${p.sales}</p><p>默认规格：${escapeHTML(v.name)} · ${money(v.price)}</p><form id="detailPurchase"><label for="detailQuantity">购买数量</label><div class="detailQuantityRow"><button type="button" id="quantityMinus" aria-label="减少购买数量">−</button><input id="detailQuantity" type="number" min="1" step="1" value="1" required><button type="button" id="quantityPlus" aria-label="增加购买数量">＋</button></div><p id="detailStock" class="muted"></p><p>本次金额 <strong id="detailAmount"></strong></p><button class="primary" id="detailAdd" type="submit">加入购物车</button></form>`);
 function refresh(){const inCart=cart.find(r=>r.id===p.id&&r.variant===p.defaultVariant)?.quantity||0,available=Math.max(0,v.stock-inCart),input=$('detailQuantity'),q=input.valueAsNumber,valid=Number.isSafeInteger(q)&&q>=1&&q<=available;
  input.max=String(available);input.disabled=available===0;$('quantityMinus').disabled=available===0||!Number.isFinite(q)||q<=1;$('quantityPlus').disabled=available===0||!Number.isFinite(q)||q>=available;
  $('detailAdd').disabled=!valid;$('detailStock').textContent=`库存 ${v.stock} 件 · 购物车已有 ${inCart} 件 · 还可加入 ${available} 件`;$('detailAmount').textContent=valid?money(Math.round(v.price*100)*q/100):'—';
 }
 $('quantityMinus').onclick=()=>{$('detailQuantity').stepDown();refresh();};$('quantityPlus').onclick=()=>{$('detailQuantity').stepUp();refresh();};$('detailQuantity').oninput=refresh;
 $('detailPurchase').onsubmit=e=>{e.preventDefault();const q=$('detailQuantity').valueAsNumber;if(!Number.isSafeInteger(q)||q<1){refresh();return;}const result=ShopModel.addToCart(cart,[p],q);if(result.skipped.length){toast('库存不足，请调整数量');refresh();return;}cart=result.cart;updateCartIndicator();toast(`已加入 ${q} 件${p.name}`);refresh();};refresh();
}
$('list').ondblclick=e=>{if(e.target.closest('[data-store]'))return;const row=e.target.closest('[data-id]');if(row)showProductDetail(products.find(p=>p.id===+row.dataset.id));};
