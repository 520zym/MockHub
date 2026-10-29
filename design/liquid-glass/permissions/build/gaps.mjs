import fs from 'node:fs';import path from 'node:path';
import {renderRolePage,root,esc,icon,btn,badge} from './ui.mjs';
const styles=['gaps.css'];
const banner=(copy,tag='权限方案设计稿')=>'<div class="pr-banner">'+icon('info')+'<span class="p-grow"><b>新权限方案设计稿</b> · '+copy+'</span>'+badge(tag,'blue')+'</div>';
const client=(fn,data)=>'<script>('+fn.toString()+')('+JSON.stringify(data).replaceAll('<','\u003c')+')</script>';
const note=t=>'<p class="p-note">'+t+'</p>';

/* ---------- p15 回收站 ---------- */
const recycleRows=[
 {id:'r1',m:'GET',p:'/api/pay/callback',n:'支付回调',w:'李哲',t:'10-28 16:20',d:26},
 {id:'r2',m:'POST',p:'/api/pay/refund',n:'退款申请',w:'王琳',t:'10-28 09:41',d:27},
 {id:'r3',m:'GET',p:'/api/pay/channel',n:'渠道路由',w:'张伟',t:'10-27 18:05',d:28},
 {id:'r4',m:'PATCH',p:'/api/pay/order/{id}',n:'订单状态',w:'陈晨',t:'10-26 14:32',d:29},
 {id:'r5',m:'DELETE',p:'/api/pay/coupon',n:'优惠券核销',w:'王琳',t:'10-25 10:18',d:30},
 {id:'r6',m:'GET',p:'/api/pay/health',n:'健康检查',w:'刘洋',t:'10-24 20:44',d:30}
];
const recycleRow=r=>'<tr data-id="'+r.id+'"><td><div class="pg-api"><span class="pw-method pw-method--'+r.m.toLowerCase()+'">'+r.m+'</span><span class="pg-api-name">'+r.n+'</span></div><small class="mono pg-api-path">'+r.p+'</small></td><td>'+r.w+'</td><td class="mono">'+r.t+'</td><td><span class="pg-left">'+r.d+'<small>天</small></span></td><td class="pg-ops"><button type="button" class="pt-link" data-restore="'+r.id+'">恢复</button><button type="button" class="pt-link pg-danger" data-purge="'+r.id+'">彻底删除</button></td></tr>';
const recycleBody=banner('删除改为软删除，进入回收站保留 30 天；恢复后回到原分组，路径冲突时由本人在恢复弹窗里选择新路径。','回收站')+
 '<div class="pr-split"><section class="p-card pg-main"><div class="pw-toolbar"><label class="pw-search">'+icon('search')+'<input id="pg-search" aria-label="搜索回收站接口" placeholder="搜索接口名称或路径"></label><div class="pg-chips"><button type="button" class="pg-chip is-on" data-scope="all">全部</button><button type="button" class="pg-chip" data-scope="mine">我删除的</button></div><span class="p-grow"></span><span class="p-meta">保留 30 天 · 到期自动清理</span></div>'+
 '<div class="p-table-wrap"><table class="p-table pg-table"><colgroup><col style="width:38%"><col style="width:12%"><col style="width:16%"><col style="width:10%"><col style="width:24%"></colgroup><thead><tr><th>接口</th><th>删除人</th><th>删除时间</th><th>剩余</th><th>操作</th></tr></thead><tbody id="pg-recycle-rows">'+recycleRows.map(recycleRow).join('')+'</tbody></table></div>'+
 '<div class="p-footer"><span id="pg-recycle-count">共 6 条</span><span class="p-grow"></span><span>30 天后自动清理，清理后不可恢复</span></div></section>'+
 '<aside class="pr-panel"><section class="p-card"><h2 class="p-title">回收站规则</h2><ul class="pg-list"><li><b>谁能看：</b>团队内所有成员都能查看本团队回收站。</li><li><b>谁能操作：</b>只有<strong>可编辑成员</strong>和<strong>团队管理员</strong>可以恢复或彻底删除；只读成员只读。</li><li><b>恢复位置：</b>回到删除前的分组；若路径已被新接口占用，恢复时需改路径。</li><li><b>彻底删除：</b>仅团队管理员可用，二次确认后不可撤销。</li></ul></section>'+
 '<section class="p-card pg-fill"><h2 class="p-title">和「接口归属」的关系</h2>'+note('接口归团队所有，不随创建者离开而消失。成员被移出团队后，他创建的接口仍在团队里，只是创建人显示为空。')+'<div class="pg-kv-list"><div><span>接口归属</span><b>团队</b></div><div><span>删除后去向</span><b>本团队回收站</b></div><div><span>保留时长</span><b>30 天</b></div></div></section></aside></div>'+
 '<dialog class="pr-dialog" id="pg-purge-dialog"><h2>彻底删除这个接口？</h2><p id="pg-purge-copy">删除后立即从回收站移除，路径释放，无法恢复。</p><div class="pr-dialog-actions"><button type="button" class="btn" data-close="pg-purge-dialog">取消</button><button type="button" class="btn btn--danger" id="pg-purge-confirm">彻底删除</button></div></dialog>';
function recycleDemo(rows){
 const byId=id=>document.getElementById(id);const notify=t=>window.permissionToast?.(t);const esc2=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
 let people=rows.map(r=>({...r}));let pending=null;
 const body=byId('pg-recycle-rows');
 function paint(){
  body.innerHTML=people.length?people.map(r=>'<tr data-id="'+r.id+'"'+(r.gone?' class="pg-gone"':'')+'><td><div class="pg-api"><span class="pw-method pw-method--'+r.m.toLowerCase()+'">'+r.m+'</span><span class="pg-api-name">'+esc2(r.n)+'</span></div><small class="mono pg-api-path">'+esc2(r.p)+'</small></td><td>'+esc2(r.w)+'</td><td class="mono">'+r.t+'</td><td><span class="pg-left">'+(r.gone?'已恢复':r.d+'<small>天</small>')+'</span></td><td class="pg-ops">'+(r.gone?'<span class="p-meta">已恢复，可在接口管理中查看</span>':'<button type="button" class="pt-link" data-restore="'+r.id+'">恢复</button><button type="button" class="pt-link pg-danger" data-purge="'+r.id+'">彻底删除</button>')+'</td></tr>').join(''):'<tr><td colspan="5"><div class="pg-empty">回收站是空的</div></td></tr>';
  byId('pg-recycle-count').textContent='共 '+people.filter(p=>!p.gone).length+' 条';
 }
 document.querySelectorAll('[data-close]').forEach(b=>b.addEventListener('click',()=>byId(b.dataset.close).close()));
 body.addEventListener('click',e=>{
  const r=e.target.closest('[data-restore]');if(r){const item=people.find(p=>p.id===r.dataset.restore);if(item){item.gone=true;paint();notify('本地演示：已恢复「'+item.n+'」，回到原分组');}return;}
  const p=e.target.closest('[data-purge]');if(p){pending=p.dataset.purge;byId('pg-purge-copy').textContent='将彻底删除「'+(people.find(x=>x.id===pending)||{}).n+'」，删除后不可恢复。';byId('pg-purge-dialog').showModal();}
 });
 byId('pg-purge-confirm').addEventListener('click',()=>{people=people.filter(p=>p.id!==pending);pending=null;paint();byId('pg-purge-dialog').close();notify('本地演示：已彻底删除，路径已释放');});
 document.querySelectorAll('[data-scope]').forEach(c=>c.addEventListener('click',()=>{document.querySelectorAll('[data-scope]').forEach(x=>x.classList.toggle('is-on',x===c));notify('本地演示：'+(c.dataset.scope==='mine'?'只看我删除的':'查看全部删除记录'));}));
 byId('pg-search').addEventListener('input',e=>{const q=e.target.value.trim().toLowerCase();const keep=people.filter(r=>!q||r.n.toLowerCase().includes(q)||r.p.toLowerCase().includes(q));body.querySelectorAll('tr[data-id]').forEach(tr=>{tr.hidden=!keep.some(k=>k.id===tr.dataset.id);});});
 paint();
}
renderRolePage({file:'p15-recycle-bin.html',title:'回收站',role:'editor',team:'USER',section:'workspace',active:'接口管理',subtitle:'用户中心 · 已删除接口保留 30 天',count:'6 条记录',actions:badge('软删除'),body:recycleBody+client(recycleDemo,recycleRows),styles});
console.log('Generated p15 recycle bin.');
/* ---------- p16 接口变更记录 ---------- */
const versions=[
 {v:'v3',when:'今天 09:12',who:'王琳',sum:'修改响应体 · 新增 1 条匹配规则',lines:[
  {t:'ctx',n:'11',s:'{'},{t:'ctx',n:'12',s:'  "code": 0,'},{t:'del',n:'13',s:'  "message": "success",'},{t:'add',n:'13',s:'  "message": "支付成功",'},{t:'ctx',n:'14',s:'  "data": {'},{t:'del',n:'15',s:'    "orderNo": "{{orderNo}}"'},{t:'add',n:'15',s:'    "orderNo": "{{orderNo}}",'},{t:'add',n:'16',s:'    "channel": "{{channel}}"'},{t:'ctx',n:'17',s:'  }'},{t:'ctx',n:'18',s:'}'}],rule:'新增条件：amount &gt; 1000 → 返回 升级通道 响应'},
 {v:'v2',when:'10-28 16:20',who:'李哲',sum:'新增条件分支 · 调整延迟',lines:[
  {t:'ctx',n:'6',s:'"rules": ['},{t:'add',n:'7',s:'  { "when": "amount &gt; 500", "return": "待确认" },'},{t:'ctx',n:'8',s:'  { "when": "status == \'PAID\'", "return": "成功" }'},{t:'ctx',n:'9',s:'],'},{t:'del',n:'10',s:'"delay": 0'},{t:'add',n:'10',s:'"delay": 120'}],rule:'匹配规则从 1 条增加到 2 条'},
 {v:'v1',when:'10-27 11:02',who:'陈晨',sum:'创建接口',lines:[
  {t:'add',n:'1',s:'{'},{t:'add',n:'2',s:'  "code": 0,'},{t:'add',n:'3',s:'  "message": "success",'},{t:'add',n:'4',s:'  "data": {}'},{t:'add',n:'5',s:'}'}],rule:'初始创建，无匹配规则'}];
const diffLine=l=>'<div class="pg-diff-line pg-diff-'+l.t+'"><span class="pg-diff-n">'+l.n+'</span><span class="pg-diff-sign">'+(l.t==='add'?'+':l.t==='del'?'-':'')+'</span><code>'+l.s+'</code></div>';
const versionPanel=v=>'<section class="p-card pg-diff-panel'+(v.v==='v3'?'':' pg-hidden')+'" data-panel="'+v.v+'"><div class="pg-diff-head"><div><h2 class="p-title">返回体 · 200 application/json</h2><p class="p-note">'+v.when+' · '+v.who+' · '+v.sum+'</p></div>'+badge(v.v,'gray')+'</div><div class="pg-diff-body">'+v.lines.map(diffLine).join('')+'</div><div class="pg-diff-rule">'+icon('info')+'<span>'+v.rule+'</span></div><div class="pg-diff-foot"><span class="p-meta">回滚会生成一条新的变更记录，不会抹掉历史。</span><span class="p-grow"></span><button type="button" class="btn" data-rollback="'+v.v+'">'+icon('arrow')+'回滚到 '+v.v+'</button></div></section>';
const timelineItem=v=>'<button type="button" class="pg-tl'+(v.v==='v3'?' is-on':'')+'" data-version="'+v.v+'"><span class="pg-tl-v">'+v.v+'</span><span class="pg-tl-main"><b>'+v.sum+'</b><small>'+v.when+' · '+v.who+'</small></span></button>';
const diffBody=banner('每次保存都留一条记录，记录修改人、修改时间和改前改后的内容；可回滚，但回滚本身也是一条新记录。','变更记录')+
 '<div class="pg-diff-layout"><section class="p-card pg-timeline"><h2 class="p-title">版本历史</h2><p class="p-note">共 3 条 · 仅本接口</p><div class="pg-tl-list">'+versions.map(timelineItem).join('')+'</div></section><div class="pg-diff-stack">'+versions.map(versionPanel).join('')+'</div></div>'+
 '<dialog class="pr-dialog" id="pg-rollback-dialog"><h2>回滚到 <span id="pg-rollback-v">v3</span>？</h2><p>回滚会把接口内容恢复为该版本，并生成一条新的变更记录。正在联调的同事会立即看到旧内容。</p><div class="pr-dialog-actions"><button type="button" class="btn" data-close="pg-rollback-dialog">取消</button><button type="button" class="btn btn--primary" id="pg-rollback-confirm">确认回滚</button></div></dialog>';
function diffDemo(){
 const byId=id=>document.getElementById(id);const notify=t=>window.permissionToast?.(t);
 document.querySelectorAll('[data-version]').forEach(b=>b.addEventListener('click',()=>{const v=b.dataset.version;document.querySelectorAll('[data-version]').forEach(x=>x.classList.toggle('is-on',x===b));document.querySelectorAll('[data-panel]').forEach(p=>p.classList.toggle('pg-hidden',p.dataset.panel!==v));}));
 document.querySelectorAll('[data-close]').forEach(b=>b.addEventListener('click',()=>byId(b.dataset.close).close()));
 document.querySelectorAll('[data-rollback]').forEach(b=>b.addEventListener('click',()=>{byId('pg-rollback-v').textContent=b.dataset.rollback;byId('pg-rollback-dialog').showModal();}));
 byId('pg-rollback-confirm').addEventListener('click',()=>{byId('pg-rollback-dialog').close();notify('本地演示：已回滚，并生成一条新的变更记录');});
}
renderRolePage({file:'p16-api-history.html',title:'变更记录',role:'editor',team:'USER',section:'workspace',active:'接口管理',subtitle:'用户中心 · /api/user/{id} · 可回滚',count:'3 条记录',actions:badge('审计留痕'),body:diffBody+client(diffDemo,{}),styles});
console.log('Generated p16 api history.');

/* ---------- p17 新建团队 ---------- */
const taken=['PAY','USER','ORDER','OPS'];
const teamBody=banner('系统管理员创建团队并指派首位管理员；团队标识创建后不可修改，它决定了 Mock 地址前缀。','系统管理')+
 '<div class="pr-split"><section class="p-card pg-form-card"><h2 class="p-title">团队信息</h2><p class="p-note">创建后立即生效，可随时归档。</p><div class="pg-form-grid">'+
 '<label class="pg-field"><span>团队名称 <em>*</em></span><input class="pg-input" id="pg-team-name" value="结算中心" maxlength="16" placeholder="2~16 个字符"></label>'+
 '<label class="pg-field"><span>团队标识 <em>*</em></span><span class="pg-input-wrap"><input class="pg-input mono" id="pg-team-id" value="SETTLE" maxlength="8" placeholder="2~8 位大写字母或数字"><span class="pg-check" id="pg-team-id-state">可用</span></span><small class="pg-hint" id="pg-team-id-hint">用于 Mock 路径前缀，创建后不可修改。</small></label>'+
 '<label class="pg-field pg-span2"><span>团队描述</span><input class="pg-input" id="pg-team-desc" value="结算、对账与资金流水场景" maxlength="40" placeholder="一句话说明这个团队负责什么"></label>'+
 '<div class="pg-field pg-span2"><span>首位团队管理员 <em>*</em></span><div class="pg-search-row"><input class="pg-input" id="pg-admin-input" placeholder="输入完整用户名精确查找"><button type="button" class="btn btn--sm" id="pg-admin-find">查找</button></div><small class="pg-hint" id="pg-admin-hint">只能指派已有账号；管理员可在团队内继续添加成员。</small></div>'+
 '<div class="pg-field pg-span2"><span>创建后状态</span><label class="pg-inline"><button type="button" class="sw sw--on" role="switch" aria-checked="true" aria-label="创建后立即启用"></button><span>立即启用，成员可访问</span></label></div>'+
 '</div><div class="pg-form-foot"><span class="p-meta">创建人：张一鸣 · 超级管理员</span><span class="p-grow"></span><button type="button" class="btn" id="pg-team-cancel">取消</button><button type="button" class="btn btn--primary" id="pg-team-submit">创建团队</button></div></section>'+
 '<aside class="pr-panel"><section class="p-card"><h2 class="p-title">创建规则</h2><ul class="pg-list"><li><b>标识唯一：</b>全局唯一，2~8 位大写字母或数字，创建后不可修改。</li><li><b>管理员必填：</b>必须指派一位已有账号，避免出现无人管理的团队。</li><li><b>权限边界：</b>团队管理员只能管理本团队成员与资源，看不到其他团队。</li><li><b>删除策略：</b>团队只能归档，归档后不占用 Mock 路径，但保留数据。</li></ul></section><section class="p-card pg-fill"><h2 class="p-title">Mock 路径前缀</h2><p class="p-note">标识决定成员看到的接口地址。</p><div class="pg-prefix"><code id="pg-prefix">/mock/SETTLE</code></div><div class="pg-kv-list"><div><span>团队标识</span><b class="mono" id="pg-prefix-id">SETTLE</b></div><div><span>示例地址</span><b class="mono">/mock/SETTLE/api/refund</b></div></div></section></aside></div>';
function teamDemo(takenIds){
 const byId=id=>document.getElementById(id);const notify=t=>window.permissionToast?.(t);
 const set=(state,text,tone)=>{const el=byId('pg-team-id-state');el.textContent=text;el.className='pg-check pg-check--'+tone;byId('pg-team-submit').disabled=state==='bad';};
 function check(){
  const raw=byId('pg-team-id').value.toUpperCase().replace(/[^A-Z0-9]/g,'').slice(0,8);byId('pg-team-id').value=raw;
  byId('pg-prefix').textContent='/mock/'+(raw||'·');byId('pg-prefix-id').textContent=raw||'·';
  if(!raw){set('bad','待填写','idle');byId('pg-team-id-hint').textContent='2~8 位大写字母或数字。';return;}
  if(raw.length<2){set('bad','太短','bad');byId('pg-team-id-hint').textContent='至少 2 位。';return;}
  if(takenIds.includes(raw)){set('bad','已被占用','bad');byId('pg-team-id-hint').textContent='该标识已存在，请换一个。';return;}
  set('ok','可用','ok');byId('pg-team-id-hint').textContent='用于 Mock 路径前缀，创建后不可修改。';
 }
 byId('pg-team-id').addEventListener('input',check);
 byId('pg-admin-find').addEventListener('click',()=>{const v=byId('pg-admin-input').value.trim();const ok=['li.ming','wanglin','chen.xi'].includes(v);byId('pg-admin-hint').textContent=ok?'已选择：'+v+' · 普通账号 USER · 正常':'未找到此完整用户名；不提供模糊匹配或全平台目录。';byId('pg-admin-hint').className='pg-hint'+(ok?' pg-hint--ok':'');});
 byId('pg-team-submit').addEventListener('click',()=>notify('本地演示：团队已创建，首位管理员已指派'));
 byId('pg-team-cancel').addEventListener('click',()=>notify('本地演示：已取消'));
 check();
}
renderRolePage({file:'p17-team-create.html',title:'新建团队',role:'super',team:'GLOBAL',section:'workspace',active:'团队管理',subtitle:'系统管理 · 团队标识与首位管理员',count:'系统级操作',actions:badge('仅超级管理员','blue'),body:teamBody+client(teamDemo,taken),styles});
console.log('Generated p17 team create.');
/* ---------- p18 新建账号 ---------- */
const teamOpts=[['USER','用户中心'],['PAY','支付中心'],['ORDER','订单中心'],['OPS','运维平台']];
const roleOpts=[['editor','可编辑成员'],['viewer','只读成员'],['admin','团队管理员']];
const assignRow=r=>'<tr><td><select class="pg-select" data-team aria-label="团队">'+teamOpts.map(o=>'<option value="'+o[0]+'"'+(r.t===o[0]?' selected':'')+'>'+o[0]+' · '+o[1]+'</option>').join('')+'</select></td><td><select class="pg-select" data-trole aria-label="团队角色">'+roleOpts.map(o=>'<option value="'+o[0]+'"'+(r.r===o[0]?' selected':'')+'>'+o[1]+'</option>').join('')+'</select></td><td><button type="button" class="pt-link pg-danger" data-del>移除</button></td></tr>';
const accountBody=banner('账号分两层：全局角色决定能否进入系统管理，团队角色决定在每个团队里能做什么，两者互不影响。','系统管理')+
 '<div class="pr-split"><section class="p-card pg-form-card"><h2 class="p-title">账号信息</h2><p class="p-note">创建后用户名不可修改。</p><div class="pg-form-grid">'+
 '<label class="pg-field"><span>用户名 <em>*</em></span><input class="pg-input mono" id="pg-user-name" placeholder="登录用，3~20 位字母数字或点" value="chen.xi"></label>'+
 '<label class="pg-field"><span>显示名 <em>*</em></span><input class="pg-input" id="pg-user-display" value="陈曦" placeholder="界面里展示的名字"></label>'+
 '<label class="pg-field"><span>初始密码 <em>*</em></span><input class="pg-input mono" id="pg-user-pass" type="password" value="Init@2026"></label>'+
 '<label class="pg-field"><span>全局角色 <em>*</em></span><select class="pg-select" id="pg-user-global" aria-label="全局角色"><option value="USER" selected>普通账号 USER</option><option value="SUPER_ADMIN">超级管理员 SUPER_ADMIN</option></select><small class="pg-hint" id="pg-global-hint">普通账号只能通过团队访问资源。</small></label>'+
 '</div><div class="pg-divider"></div><div class="pg-assign-head"><div><h3 class="p-title">团队分配</h3><p class="p-note">一个人可以同时属于多个团队，每个团队角色独立。</p></div><button type="button" class="btn btn--sm" id="pg-assign-add">'+icon('add')+'添加团队</button></div>'+
 '<div class="p-table-wrap"><table class="p-table pg-assign-table"><colgroup><col style="width:42%"><col style="width:34%"><col style="width:24%"></colgroup><thead><tr><th>团队</th><th>在该团队的角色</th><th>操作</th></tr></thead><tbody id="pg-assign-rows">'+[{t:'USER',r:'editor'},{t:'PAY',r:'viewer'}].map(assignRow).join('')+'</tbody></table></div>'+
 '<p class="pg-hint pg-hint--inline">不选团队也可以创建：账号会成为「尚未加入团队」状态，登录后只看到引导页。</p>'+
 '<div class="pg-form-foot"><span class="p-meta">创建人：张一鸣 · 超级管理员</span><span class="p-grow"></span><button type="button" class="btn" id="pg-user-cancel">取消</button><button type="button" class="btn btn--primary" id="pg-user-submit">创建账号</button></div></section>'+
 '<aside class="pr-panel"><section class="p-card"><h2 class="p-title">两层角色</h2><div class="pg-layer"><div class="pg-layer-row"><span class="pg-layer-tag">全局</span><div><b>USER / SUPER_ADMIN</b><small>决定能否进入系统管理：账号、团队、全局设置、MCP 策略。</small></div></div><div class="pg-layer-row"><span class="pg-layer-tag">团队</span><div><b>可编辑 / 只读 / 团队管理员</b><small>只决定在某个团队内能做什么，换团队就换角色。</small></div></div></div></section>'+
 '<section class="p-card pg-fill"><h2 class="p-title">权限边界</h2><ul class="pg-list"><li>超级管理员<strong>不自动获得</strong>团队内的编辑权，进入团队仍需以该团队身份操作。</li><li>团队管理员只能管理本团队，无法创建账号或新建团队。</li><li>移出团队不影响账号本身，接口资源留在团队。</li></ul></section></aside></div>';
function accountDemo(){
 const byId=id=>document.getElementById(id);const notify=t=>window.permissionToast?.(t);
 const teams=[['USER','用户中心'],['PAY','支付中心'],['ORDER','订单中心'],['OPS','运维平台']];
 const roles=[['editor','可编辑成员'],['viewer','只读成员'],['admin','团队管理员']];
 const rowHtml=t=>'<tr><td><select class="pg-select" data-team aria-label="团队">'+teams.map(o=>'<option value="'+o[0]+'"'+(t===o[0]?' selected':'')+'>'+o[0]+' · '+o[1]+'</option>').join('')+'</select></td><td><select class="pg-select" data-trole aria-label="团队角色">'+roles.map(o=>'<option value="'+o[0]+'">'+o[1]+'</option>').join('')+'</select></td><td><button type="button" class="pt-link pg-danger" data-del>移除</button></td></tr>';
 const body=byId('pg-assign-rows');
 byId('pg-assign-add').addEventListener('click',()=>{const used=[...body.querySelectorAll('[data-team]')].map(s=>s.value);const next=teams.find(t=>!used.includes(t[0]));if(!next){notify('本地演示：四个团队都已分配');return;}body.insertAdjacentHTML('beforeend',rowHtml(next[0]));notify('本地演示：已添加一行团队分配');});
 body.addEventListener('click',e=>{const b=e.target.closest('[data-del]');if(!b)return;b.closest('tr').remove();notify('本地演示：已移除该团队分配');});
 byId('pg-user-global').addEventListener('change',e=>{const s=e.target.value==='SUPER_ADMIN';byId('pg-global-hint').textContent=s?'超级管理员可管理账号与团队，但不自动获得团队内编辑权。':'普通账号只能通过团队访问资源。';});
 byId('pg-user-submit').addEventListener('click',()=>{const n=body.querySelectorAll('tr').length;notify('本地演示：账号已创建，已加入 '+n+' 个团队');});
 byId('pg-user-cancel').addEventListener('click',()=>notify('本地演示：已取消'));
}
renderRolePage({file:'p18-user-create.html',title:'新建账号',role:'super',team:'GLOBAL',section:'workspace',active:'账号管理',subtitle:'系统管理 · 全局角色与团队分配',count:'系统级操作',actions:badge('仅超级管理员','blue'),body:accountBody+client(accountDemo,{}),styles});
console.log('Generated p18 user create.');

/* ---------- p19 无权限访问 ---------- */
const denyBody='<div class="pg-403"><section class="p-card pg-403-card"><div class="pg-403-mark">'+icon('lock')+'</div><h1>你没有「运维平台」的访问权限</h1><p class="pg-403-lead">当前账号 wanglin（王琳）不在该团队的成员列表中。Mock 接口按团队隔离，跨团队的链接不会自动授权。</p><div class="pg-403-actions"><a class="btn btn--primary" href="p02-workspace-viewer.html">返回我的团队</a><a class="btn" href="p04-account.html#teams">查看我的团队</a></div><div class="pg-403-facts"><div><span>当前团队</span><b>订单中心 · 只读成员</b></div><div><span>请求的团队</span><b>运维平台</b></div><div><span>当前账号</span><b class="mono">wanglin</b></div></div><div class="pg-403-why"><h2 class="p-title">可能的原因</h2><ul class="pg-list"><li>你<strong>被移出</strong>了该团队，或者从未加入。</li><li>该团队已被<strong>归档</strong>，成员暂时无法访问。</li><li>链接来自其他团队或外部同事，属于<strong>跨团队访问</strong>。</li></ul><p class="pg-hint pg-hint--inline">需要访问？联系该团队的团队管理员把你加入成员列表；超级管理员可在团队管理里指派。</p></div></section></div>';
renderRolePage({file:'p19-no-access.html',title:'无权访问',role:'viewer',team:'ORDER',section:'workspace',active:'',subtitle:'订单中心 · 只读成员 · 跨团队访问被拒绝',count:'403',actions:badge('已拦截','red'),body:denyBody,styles});
console.log('Generated p19 no access.');
/* ---------- 注册到 pages.json（幂等） ---------- */
const meta=[
 {id:'p15-recycle-bin',file:'p15-recycle-bin.html',title:'回收站 · 可编辑成员',description:'软删除保留 30 天，恢复回到原分组，彻底删除仅管理员',group:'团队与系统管理',role:'editor',team:'USER',section:'workspace',route:'USER · 可编辑',width:1440,height:900},
 {id:'p16-api-history',file:'p16-api-history.html',title:'变更记录 · 可编辑成员',description:'每次保存留痕，可回滚，回滚本身也是一条记录',group:'团队与系统管理',role:'editor',team:'USER',section:'workspace',route:'USER · 可编辑',width:1440,height:900},
 {id:'p17-team-create',file:'p17-team-create.html',title:'新建团队 · 超级管理员',description:'标识唯一且不可修改，必须指派首位管理员',group:'团队与系统管理',role:'super',team:'GLOBAL',section:'workspace',route:'系统管理',width:1440,height:900},
 {id:'p18-user-create',file:'p18-user-create.html',title:'新建账号 · 超级管理员',description:'全局角色与团队角色分离，一次可分配多个团队',group:'团队与系统管理',role:'super',team:'GLOBAL',section:'workspace',route:'系统管理',width:1440,height:900},
 {id:'p19-no-access',file:'p19-no-access.html',title:'无权限访问 · 403',description:'跨团队访问被拦截，说明原因与求助路径',group:'团队工作区与权限',role:'viewer',team:'ORDER',section:'workspace',route:'ORDER · 只读',width:1440,height:900}
];
const pf=path.join(root,'pages.json');const pages=JSON.parse(fs.readFileSync(pf,'utf8'));
for(const m of meta){const i=pages.findIndex(p=>p.id===m.id);if(i>=0)pages[i]=Object.assign({},pages[i],m);else pages.push(m);}
fs.writeFileSync(pf,JSON.stringify(pages,null,2)+String.fromCharCode(10));
console.log('Registered scenes:',pages.length);
