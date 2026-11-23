import { renderPage, btn, icon, field, badge, footer, esc } from './ui.mjs';

// Static demonstration data only. Feature structure follows the Vue page templates.
const css = ['workspace.css'];
const search = (placeholder, cls='') => '<div class="wh-search '+cls+'">'+icon('search')+'<span>'+placeholder+'</span></div>';
const select = (label, value) => '<button type="button" class="wh-select"><span>'+label+'</span><b>'+value+'</b>'+icon('down')+'</button>';
const title = (text, note='') => '<div><h2 class="wh-title">'+text+'</h2>'+(note?'<p class="wh-note">'+note+'</p>':'')+'</div>';
const textAction = (text, name='', cls='') => '<button type="button" class="wh-text-action '+cls+'">'+(name?icon(name):'')+text+'</button>';
const check = (on=false) => '<span class="wh-check'+(on?' is-on':'')+'">'+(on?icon('check'):'')+'</span>';

const files = [
  ['支付回调示例','payment-callback.json','f_8e4c1a2d','回调','2.4 KB','10-28','14:20',182,'436.8 KB','JSON'],
  ['对账测试数据','reconciliation.csv','f_b91d7e3a','测试','84 KB','10-28','11:06',64,'5.25 MB','CSV'],
  ['退款结果示例','refund-result.json','f_26c9d401','退款','1.8 KB','10-27','17:42',96,'172.8 KB','JSON'],
  ['支付流程说明','payment-guide.pdf','f_79a1eb52','文档','1.2 MB','10-27','10:15',38,'45.6 MB','PDF'],
  ['订单导入模板','orders-template.xlsx','f_33f06c18','模板','42 KB','10-26','16:38',72,'2.95 MB','XLSX'],
  ['回调页面','callback-preview.html','f_04d83b6e','回调','6.1 KB','10-25','09:30',24,'146.4 KB','HTML'],
  ['商品占位图','product-placeholder.png','f_e6aa1039','图片','128 KB','10-24','15:12',216,'27 MB','PNG'],
  ['错误码说明','error-codes.json','f_a120c7b4','文档','4.8 KB','10-23','13:45',53,'254.4 KB','JSON']
];
const fileRows = files.map((f,i) => '<tr'+(i===0?' class="is-selected"':'')+'><td><div class="wh-file-cell"><span class="wh-file-kind">'+f[9]+'</span><div><b>'+f[0]+'</b><small>'+f[1]+'</small></div></div></td><td class="mono wh-id">'+f[2]+'</td><td>'+badge(f[3],i===0?'blue':'gray')+'</td><td class="wh-number">'+f[4]+'</td><td class="wh-date"><span>2026-'+f[5]+'</span><small>'+f[6]+'</small></td><td class="wh-number">'+f[7]+'</td><td class="wh-number">'+f[8]+'</td><td><button class="wh-more" aria-label="'+esc(f[0])+'更多操作">'+icon('more')+'</button></td></tr>').join('');
const filesBody = '<div class="wh-root wh-files">'+
  '<div class="wh-toolbar wh-card">'+search('搜索 fileId、文件名或别名')+select('团队','全部团队')+select('标签','全部标签')+'<span class="wh-spacer"></span><span class="wh-muted">28 个文件 · 演示数据</span></div>'+
  '<div class="wh-file-layout"><section class="wh-card wh-file-list"><div class="wh-section-head">'+title('文件列表','公开下载链接支持 Range 请求')+'<span class="wh-muted">总大小 86.4 MB</span></div><div class="wh-table-wrap"><table class="wh-file-table"><colgroup><col><col style="width:98px"><col style="width:60px"><col style="width:63px"><col style="width:85px"><col style="width:52px"><col style="width:77px"><col style="width:30px"></colgroup><thead><tr><th>文件 / 别名</th><th>fileId</th><th>标签</th><th class="wh-right">大小</th><th>上传时间</th><th class="wh-right">下载</th><th class="wh-right">传输量</th><th></th></tr></thead><tbody>'+fileRows+'</tbody></table></div><div class="wh-list-bottom">'+icon('info')+'<span>成员可上传与查看；修改别名、标签和删除需团队管理员或超管。</span></div>'+footer('共 28 个文件 · 当前展示 8 条')+'</section>'+
  '<aside class="wh-card wh-file-detail"><div class="wh-detail-title"><span class="wh-detail-file">'+icon('file')+'</span><div><h2 class="wh-title">支付回调示例</h2><p class="wh-note">payment-callback.json · 支付中心</p></div>'+badge('已选中','blue')+'</div>'+
  '<div class="wh-file-metrics"><div><span>下载次数</span><strong>182<small>次</small></strong></div><div><span>累计传输量</span><strong>436.8<small>KB</small></strong></div></div>'+
  '<div><div class="wh-label-row"><span>公开下载链接</span>'+textAction('复制','copy')+'</div><div class="wh-url mono">https://mockhub.example/files/f_8e4c1a2d</div><p class="wh-micro">最近下载 2026-10-28 14:28 · 删除后链接返回 404</p></div>'+
  '<div class="wh-file-edit">'+field('别名','支付回调示例')+field('标签',badge('回调','blue')+badge('支付','gray')+'<span class="wh-add-tag">＋ 添加</span>')+'</div>'+
  '<div class="wh-preview"><div class="wh-label-row"><b>安全预览</b>'+badge('JSON · 2.4 KB')+'</div><pre class="wh-code">'+esc(JSON.stringify({orderNo:"PAY202610280001",status:"SUCCESS",amount:19900,currency:"CNY"},null,2))+'</pre><p class="wh-micro">以纯文本展示，不执行文件中的内容。</p></div>'+
  '<div class="wh-detail-actions">'+btn('保存信息',{primary:true,small:true,icon:'check'})+textAction('删除文件','trash','wh-danger')+'</div></aside></div></div>';
renderPage({file:'07-files.html',title:'文件服务器',active:'文件服务器',subtitle:'<span>4 个团队</span><span class="dot"></span><span>统一管理模拟文件与公开下载链接</span>',badge:'28 个文件',actions:btn('刷新',{icon:'refresh'})+btn('上传文件',{primary:true,icon:'upload'}),body:filesBody,styles:css});

const variables = [
  ['channel','客户端渠道',12],['pay_method','支付方式',6],['order_status','订单状态',8],['currency','结算币种',5],['region','业务区域',7],['refund_reason','退款原因',6],['device_type','设备类型',4],['card_type','银行卡类型',3],['trade_type','交易类型',5],['risk_level','风控等级',3],['settle_cycle','结算周期',4],['notify_type','通知类型',3]
];
const values = [['ios','iOS 客户端'],['android','Android 客户端'],['harmony','HarmonyOS 客户端'],['wechat_mini','微信小程序'],['alipay_mini','支付宝小程序'],['douyin_mini','抖音小程序'],['unionpay_app','云闪付客户端'],['partner_app','合作方客户端'],['web_pc','桌面网页'],['web_h5','移动网页'],['web_admin','管理后台'],['web_partner','合作方门户']];
const variableItems = variables.map((v,i)=>'<button class="wh-variable'+(i===0?' is-active':'')+'"><span><b class="mono">'+v[0]+'</b><small>'+v[1]+'</small></span><em>'+v[2]+'</em></button>').join('');
const valueRows = values.map((v,i)=>'<tr'+(i===0?' class="is-selected"':'')+'><td class="mono">'+v[0]+'</td><td>'+v[1]+'</td><td class="wh-row-actions">'+textAction('编辑')+textAction('删除','','wh-danger')+'</td></tr>').join('');
const memberItems = values.map((v,i)=>'<label class="wh-member">'+check(i<8)+'<span class="mono">'+v[0]+'</span></label>').join('');
const variablesBody = '<div class="wh-root wh-variables"><div class="wh-toolbar wh-card">'+select('当前团队','支付中心')+badge('可编辑','blue')+'<span class="wh-permission">'+icon('lock')+'团队管理员或超级管理员可编辑，普通成员只读</span><span class="wh-spacer"></span><span class="wh-muted">12 个变量 · 演示数据</span></div>'+
  '<div class="wh-variable-layout"><section class="wh-card wh-variable-list"><div class="wh-section-head">'+title('变量','名称 / 描述')+textAction('新建','add')+'</div><div class="wh-variable-items">'+variableItems+'</div><div class="wh-variable-foot">'+textAction('编辑变量','edit')+textAction('删除','','wh-danger')+'</div></section>'+
  '<section class="wh-card wh-value-panel"><div class="wh-section-head">'+title('候选值 '+badge('channel','blue'),'客户端渠道 · 12 个候选值')+'</div><div class="wh-value-toolbar">'+search('搜索值或描述')+btn('批量粘贴',{small:true})+btn('增加值',{small:true,primary:true,icon:'add'})+'</div><table class="wh-value-table"><colgroup><col style="width:36%"><col><col style="width:88px"></colgroup><thead><tr><th>值</th><th>描述</th><th class="wh-right">操作</th></tr></thead><tbody>'+valueRows+'</tbody></table>'+
  '<div class="wh-inline-edit"><div class="wh-label-row"><b>编辑候选值</b><span class="mono wh-muted">ios</span></div><div class="wh-value-fields">'+field('值','ios',{mono:true})+field('描述','iOS 客户端')+'</div><div class="wh-edit-bottom"><span>描述仅展示，不参与替换输出</span>'+textAction('取消')+btn('保存',{small:true,primary:true})+'</div></div></section>'+
  '<section class="wh-card wh-group-panel"><div class="wh-section-head">'+title('分组','按渠道复用候选值集合')+textAction('新建','add')+'</div><div class="wh-group is-active"><div class="wh-group-name"><b class="mono">APP</b>'+badge('8 个成员','blue')+textAction('编辑','edit')+'</div><p>原生客户端与小程序渠道</p></div><div class="wh-group"><div class="wh-group-name"><b class="mono">WEB</b>'+badge('4 个成员')+textAction('编辑','edit')+'</div><p>桌面网页、移动端与业务门户</p></div>'+
  '<div class="wh-group-editor"><div class="wh-label-row"><b>编辑分组</b><span class="mono wh-blue">APP</span></div>'+field('分组名','APP',{mono:true})+field('描述','原生客户端与小程序渠道')+'<div class="wh-label-row"><span>成员</span><span class="wh-muted">已选 8 / 12</span></div>'+search('搜索候选值')+'<div class="wh-members">'+memberItems+'</div><div class="wh-group-save">'+textAction('取消')+btn('保存分组',{primary:true,small:true})+'</div></div><div class="wh-batch-hint">'+icon('info')+'<p>批量粘贴：每行一条，以英文逗号分隔值和描述；重复值将跳过。</p></div></section></div></div>';
renderPage({file:'08-variables.html',title:'动态变量',active:'动态变量',subtitle:'<span>支付中心</span><span class="dot"></span><span>维护团队变量、候选值与分组</span>',badge:'12 个变量',actions:btn('新建变量',{primary:true,icon:'add'}),body:variablesBody,styles:css});

const daily = [6200,7800,8400,7100,9800,10400,9200,10800,12400,11700,13800,14200,15600,12840];
const total = daily.reduce((a,b)=>a+b,0).toLocaleString('en-US');
const trendPoints = daily.map((v,i)=>[47+i*42.8,174-v/16000*140]);
const trend = '<svg class="wh-trend-svg" viewBox="0 0 640 212" role="img" aria-label="10月15日至28日每日调用次数；总计150240次，今日12840次">'+
  [0,4000,8000,12000,16000].map(v=>'<text x="32" y="'+(178-v/16000*140)+'" text-anchor="end">'+(v?v/1000+'k':'0')+'</text>').join('')+
  daily.map((v,i)=>'<rect x="'+(trendPoints[i][0]-8)+'" y="'+trendPoints[i][1]+'" width="16" height="'+(174-trendPoints[i][1])+'" rx="3" fill="'+(i===13?'#dbe5fa':'#edf2fc')+'"/>').join('')+
  '<polyline points="'+trendPoints.map(p=>p.join(',')).join(' ')+'" fill="none" stroke="#3568d4" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>'+
  trendPoints.map((p,i)=>'<circle cx="'+p[0]+'" cy="'+p[1]+'" r="'+(i===13?'4':'2.5')+'" fill="#3568d4"/>').join('')+
  '<text class="wh-chart-value" x="'+(trendPoints[13][0]-7)+'" y="'+(trendPoints[13][1]-12)+'" text-anchor="end">12,840</text>'+
  [0,2,4,6,8,10,13].map(i=>'<text x="'+trendPoints[i][0]+'" y="202" text-anchor="middle">10/'+(15+i)+'</text>').join('')+'</svg>';
const top = [['创建支付单',28410],['查询订单',21680],['支付回调',17820],['查询用户',14260],['商品列表',11740],['退款申请',9960],['订单列表',8420],['获取 Token',7340],['库存查询',6180],['对账单下载',5120]];
const topChart = '<svg class="wh-top-svg" viewBox="0 0 420 216" role="img" aria-label="接口热度TOP10，创建支付单28410次排名第一">'+top.map((t,i)=>'<text x="0" y="'+(15+i*21)+'" class="wh-rank">'+String(i+1).padStart(2,'0')+'</text><text x="26" y="'+(15+i*21)+'">'+t[0]+'</text><rect x="112" y="'+(5+i*21)+'" width="'+(Number(t[1])/28410*230)+'" height="10" rx="3" fill="'+(i===0?'#3568d4':'#c9d7f4')+'"/><text x="414" y="'+(15+i*21)+'" text-anchor="end" class="wh-top-number">'+Number(t[1]).toLocaleString('en-US')+'</text>').join('')+'</svg>';
const dormant = [
  ['支付中心','POST','/legacy/pay/notify','旧版支付通知',126,'2026-09-16','42 天前'],
  ['用户中心','GET','/v1/user/level','会员等级查询',318,'2026-09-22','36 天前'],
  ['订单中心','GET','/v1/order/export','旧版订单导出',84,'2026-09-24','34 天前'],
  ['基础服务','GET','/v1/region/list','历史行政区划',0,'—','从未调用']
];
const dormantRows = dormant.map(d=>'<tr><td>'+badge(d[0])+'</td><td>'+badge(d[1],d[1]==='POST'?'blue':'green')+'</td><td class="mono">'+d[2]+'</td><td>'+d[3]+'</td><td class="wh-number">'+d[4]+'</td><td><span>'+d[6]+'</span><small class="wh-last-date">'+d[5]+'</small></td><td class="wh-right">'+btn('编辑',{href:'02-api-edit.html',small:true,icon:'arrow'})+'</td></tr>').join('');
const statsBody = '<div class="wh-root wh-stats"><div class="wh-kpi-grid">'+
  '<section class="wh-card wh-kpi"><label>启用接口</label><strong>132<span>/ 143</span></strong><p>全部 4 个团队 · 当前启用数量</p></section>'+
  '<section class="wh-card wh-kpi"><label>今日调用</label><strong>12,840<span>次</span></strong><p>2026-10-28 · 自然日 00:00 起</p></section>'+
  '<section class="wh-card wh-kpi"><label>14 日区间调用</label><strong>'+total+'<span>次</span></strong><p>2026-10-15 — 2026-10-28 · 含今日</p></section>'+
  '<section class="wh-card wh-kpi"><label>平均响应耗时</label><strong>42<span>ms</span></strong><p>当前 14 日统计窗口内</p></section></div>'+
  '<div class="wh-charts"><section class="wh-card wh-trend-card"><div class="wh-section-head">'+title('14 日调用趋势','每日调用次数')+'<span class="wh-chart-legend"><i></i>调用次数</span></div>'+trend+'</section><section class="wh-card wh-top-card"><div class="wh-section-head">'+title('接口热度 TOP 10','14 日内调用最多的接口')+'<span class="wh-muted">单位：次</span></div>'+topChart+'</section></div>'+
  '<section class="wh-card wh-dormant"><div class="wh-section-head">'+title('长期未调用的启用接口 '+badge('4 个','amber'),'超过 30 天未被调用，可进入接口编辑页检查是否仍需启用')+'<span class="wh-muted">全部团队</span></div><table class="wh-dormant-table"><colgroup><col style="width:104px"><col style="width:76px"><col><col style="width:164px"><col style="width:78px"><col style="width:126px"><col style="width:82px"></colgroup><thead><tr><th>团队</th><th>方法</th><th>路径</th><th>名称</th><th class="wh-right">累计命中</th><th>最近调用</th><th></th></tr></thead><tbody>'+dormantRows+'</tbody></table><div class="wh-dormant-foot"><span>当前展示全部 4 个接口</span><span>仅列出启用状态的接口</span></div></section>'+
  '<div class="wh-stats-note">'+icon('info')+'<span>统计由请求日志聚合，受日志保留与清理策略影响，可能不含已清理的历史调用。</span><span class="wh-spacer"></span><span>演示数据 · 更新于 14:32</span></div></div>';
renderPage({file:'09-stats.html',title:'使用统计',active:'使用统计',subtitle:'<span>4 个团队 · 143 个接口</span><span class="dot"></span><span>调用趋势、接口热度与闲置接口</span>',actions:select('团队','全部团队')+'<div class="wh-period"><button>7 日</button><button class="is-active">14 日</button><button>30 日</button></div>'+btn('刷新',{icon:'refresh'}),body:statsBody,styles:css});
console.log('Generated 07-files.html, 08-variables.html, 09-stats.html');
