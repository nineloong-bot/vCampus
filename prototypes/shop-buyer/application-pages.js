// Independent reviewable application pages; previewing does not approve an application.
const applicationSample={name:'青禾校园小店',subject:'校园示例主体',license:'DEMO-LICENSE-001',submittedAt:'2026-09-14 10:30',reference:'DEMO-20260914-001'};
const applicationPage=document.createElement('section');
applicationPage.id='applicationPage';applicationPage.hidden=true;document.body.append(applicationPage);
function applicationData(){return shopApplication||applicationSample;}
function applicationSummary(data){return `<dl class="applicationFacts"><div><dt>店铺名称</dt><dd>${escapeHTML(data.name)}</dd></div><div><dt>申请编号</dt><dd>${escapeHTML(data.reference||'本次演示申请')}</dd></div><div><dt>提交时间</dt><dd>${escapeHTML(data.submittedAt||'本次会话')}</dd></div><div><dt>主体名称</dt><dd>${escapeHTML(data.subject||'校园示例主体')}</dd></div><div><dt>营业执照编号</dt><dd> ${escapeHTML(data.license)}</dd></div><div><dt>经营规则</dt><dd>已阅读并同意</dd></div></dl>`;}
function showApplicationPage(kind){
 const pending=kind==='pending',data=applicationData();if($('dialog').open)$('dialog').close();
 document.querySelector('.app').hidden=true;applicationPage.hidden=false;
 applicationPage.innerHTML=`<aside class="previewRail"><div><b>页面预览</b><span>开店流程</span></div><nav><button data-application="submitted" class="${pending?'':'active'}">APP-01 提交成功</button><button data-application="pending" class="${pending?'active':''}">APP-02 等待审核</button></nav><span class="reviewState">设计已确认 · ${shopApplication?'本次申请':'示例资料'}</span><button id="exitApplication">退出预览</button></aside>
 <header class="applicationHeader"><button id="applicationBack">← 返回我的</button><strong>校园集 <span>/ 开店申请</span></strong><span>校园体验账号</span></header>
 <div class="applicationScroll"><div class="applicationContainer"><ol class="applicationSteps"><li class="done"><b>✓</b>提交申请</li><li class="${pending?'current':''}"><b>2</b>平台审核</li><li><b>3</b>开店完成</li></ol>
 ${pending?`<section class="applicationHero pending"><div class="applicationSymbol">◷</div><div><span class="stateLabel">等待审核</span><h1>你的开店申请正在等待审核</h1><p>资料已提交，审核结果将显示在本页面。</p></div></section><div class="pendingLayout"><section class="applicationCard"><h2>申请资料</h2>${applicationSummary(data)}</section><section class="applicationCard progressCard"><h2>申请进度</h2><div class="timeline"><section><b>申请已提交</b><p>${escapeHTML(data.submittedAt||'本次会话')}</p></section><section class="current"><b>等待平台审核</b><p>管理员根据文字资料进行模拟审核。</p></section><section><b>审核结果</b><p>审核完成后可在这里查看结果。</p></section></div><div class="applicationNote">你可以继续逛店，稍后从“我的 → 申请开店”查看进度。</div></section></div><div class="applicationActions"><button id="applicationMine">返回我的</button><button class="primary" id="continueShopping">继续逛店 →</button></div>`:
 `<section class="submittedHero"><div class="applicationSymbol">✓</div><span class="stateLabel">提交成功</span><h1>开店申请已提交</h1><p>你的资料已成功提交，接下来将由平台进行审核。</p></section><section class="applicationCard submittedCard"><h2>本次申请</h2>${applicationSummary(data)}</section><div class="applicationActions centered"><button id="continueShopping">继续逛店</button><button class="primary" id="viewApplicationProgress">查看申请进度 →</button></div>`}
 <p class="applicationFootnote">${pending?'APP-02 · 等待审核页':'APP-01 · 提交成功页'} · 模拟审核 · 本地交互原型</p></div></div>`;
 applicationPage.querySelectorAll('[data-application]').forEach(b=>b.onclick=()=>openApplicationPage(b.dataset.application));
 $('exitApplication').onclick=exitApplication; $('applicationBack').onclick=()=>{exitApplication();showMine();};
 if($('applicationMine'))$('applicationMine').onclick=()=>{exitApplication();showMine();};
 $('continueShopping').onclick=()=>{exitApplication();$('home').click();};
 if($('viewApplicationProgress'))$('viewApplicationProgress').onclick=()=>openApplicationPage('pending');
}
function openApplicationPage(kind){location.hash='application-'+kind;showApplicationPage(kind);}
function exitApplication(){applicationPage.hidden=true;document.querySelector('.app').hidden=false;if(location.hash.startsWith('#application-'))window.history.replaceState(null,'',location.pathname+location.search);}
window.addEventListener('hashchange',()=>{const kind=location.hash.slice(13);if(['submitted','pending'].includes(kind))showApplicationPage(kind);else {applicationPage.hidden=true;document.querySelector('.app').hidden=false;}});
const applicationMine=showMine;
showMine=function(){applicationMine();$('dialogBody').insertAdjacentHTML('beforeend','<div class="applicationPreviewEntry"><span>设计预览</span><button id="previewSubmitted">提交成功页</button><button id="previewPending">等待审核页</button></div>');$('previewSubmitted').onclick=()=>openApplicationPage('submitted');$('previewPending').onclick=()=>openApplicationPage('pending');};
$('mine').onclick=showMine;
const previewButton=document.createElement('button');previewButton.textContent='页面预览';previewButton.id='applicationPreview';previewButton.onclick=()=>openApplicationPage('submitted');document.querySelector('header .nav').append(previewButton);
if(['#application-submitted','#application-pending'].includes(location.hash))showApplicationPage(location.hash.slice(13));
