// Account entry points for the local interaction prototype.
let shopApplication=null;
const balanceMine=$('mine').onclick;
function showMine(){
 balanceMine();
 $('dialogBody').insertAdjacentHTML('beforeend',`<div class="accountEntries"><button id="applyShop"><span><strong>申请开店</strong><small>${shopApplication?'查看申请进度':'提交主体资质，开启你的校园店铺'}</small></span><span>→</span></button><button id="viewOrders"><span><strong>查看订单</strong><small>查看购买记录与订单状态</small></span><span>→</span></button></div>`);
 $('applyShop').onclick=showShopApplication;$('viewOrders').onclick=()=>showOrders('全部');
 const reset=$('resetDemo').onclick;$('resetDemo').onclick=()=>{shopApplication=null;reset();};
}
function showShopApplication(){
 if(shopApplication && shopApplication.status!=='APPROVED'){openApplicationPage('pending');return;}
 if(shopApplication){
  const approved=shopApplication.status==='APPROVED';
  modal('开店申请',`<div class="balanceCard"><span class="tag">${approved?'审核通过 · 模拟状态':'待审核 · 模拟状态'}</span><h2>${escapeHTML(shopApplication.name)}</h2><p>${approved?'开店成功，主体资质已通过模拟审核。':'申请已记录，等待管理员审核。'}</p><p class="muted">营业执照：${escapeHTML(shopApplication.license)}</p></div><div class="cartFooter"><button id="backAccount">返回我的</button>${approved?'':'<button id="simulateApproval" class="primary">模拟审核通过</button>'}</div>`);
  $('backAccount').onclick=showMine;
  if($('simulateApproval'))$('simulateApproval').onclick=()=>{shopApplication.status='APPROVED';showShopApplication();};
  return;
 }
 modal('申请开店',`<p class="muted">填写文字资料，用于课程 Demo 的模拟审核。</p><form id="shopApplyForm" class="accountForm"><label>店铺名称<input name="shopName" required maxlength="40" placeholder="为你的店铺起个名字"></label><label>主体名称<input name="subject" required placeholder="填写主体名称"></label><label>营业执照编号<input name="license" required placeholder="填写证件编号"></label><label class="agreement"><input name="agreement" type="checkbox" required>我已阅读并同意平台经营规则</label><p class="muted">经营规则摘要：店家对商品负责；普通类目完成主体核验后可经营，许可类目须通过专项资质审核，禁售商品禁止发布。正式规则文本待后续完善。</p><div class="cartFooter"><button type="button" id="backAccount">返回我的</button><button class="primary" type="submit">提交申请（演示）</button></div></form>`);
 $('backAccount').onclick=showMine;
 $('shopApplyForm').onsubmit=e=>{e.preventDefault();const form=e.currentTarget,name=form.elements.shopName.value.trim();if(!form.elements.subject.value.trim()||!form.elements.license.value.trim()){toast('请填写主体名称和营业执照编号');return;}if(!name){form.elements.shopName.setCustomValidity('请输入店铺名称');form.elements.shopName.reportValidity();return;}shopApplication={name,subject:form.elements.subject.value.trim(),license:form.elements.license.value.trim(),status:'PENDING',submittedAt:new Date().toLocaleString('zh-CN'),reference:'DEMO-'+Date.now()};openApplicationPage('submitted');};
 $('shopApplyForm').elements.shopName.oninput=e=>e.target.setCustomValidity('');
}
function showOrders(status){
 modal('我的订单',`<div class="tabs orderTabs">${['全部','待付款','已付款','已取消'].map(s=>`<button data-order-tab="${s}" class="${s===status?'active':''}">${s}</button>`).join('')}</div><div class="empty"><h3>${status==='全部'?'暂无订单':'暂无'+status+'订单'}</h3><p>当前演示尚未生成订单。</p></div><div class="cartFooter"><button id="backAccount">返回我的</button><button id="browseProducts" class="primary">去逛逛</button></div>`);
 document.querySelectorAll('[data-order-tab]').forEach(b=>b.onclick=()=>showOrders(b.dataset.orderTab));$('backAccount').onclick=showMine;$('browseProducts').onclick=()=>{$('dialog').close();$('home').click();};
}
$('mine').onclick=showMine;
