import { page, icon, methodTag, typeTag, teamTag, switchOn, cb, chip, aiBadge, codeBlock, k, str, num, pun } from './lib.mjs';

const rows = [
  ['REST', 'GET', '/api/order/detail', '订单详情', ['订单', '核心'], 'ORDER', true, '3 分钟前'],
  ['REST', 'POST', '/api/pay/create', '创建支付单', ['支付'], 'PAY', true, '12 分钟前'],
  ['REST', 'GET', '/api/user/info', '用户信息', ['用户'], 'USER', true, '1 小时前'],
  ['REST', 'POST', '/api/refund/apply', '退款申请', ['支付', '异步'], 'PAY', true, '2 小时前'],
  ['REST', 'GET', '/api/coupon/list', '优惠券列表', [], 'USER', true, '昨天 18:42'],
  ['SOAP', 'POST', '/ws/notice', '通知服务', ['SOAP'], 'ORDER', false, '从未调用']
];

function rowHtml(r) {
  const tags = r[4].length
    ? '<div class="tag-list">' + r[4].map(function (t) { return '<span class="mini-tag">' + t + '</span>'; }).join('') + '</div>'
    : '<span class="muted" style="color:#CBD5E0">-</span>';
  return [
    '<tr' + (r[6] ? '' : ' style="opacity:.55"') + '>',
    '  <td style="width:36px">' + cb(false) + '</td>',
    '  <td style="width:72px">' + typeTag(r[0]) + '</td>',
    '  <td style="width:76px">' + methodTag(r[1]) + '</td>',
    '  <td class="path">' + r[2] + '</td>',
    '  <td class="name">' + r[3] + '</td>',
    '  <td style="width:132px">' + tags + '</td>',
    '  <td style="width:88px">' + teamTag(r[5]) + '</td>',
    '  <td style="width:64px">' + switchOn(r[6]) + '</td>',
    '  <td style="width:104px;color:' + (r[7] === '从未调用' ? '#B0B7C3' : '#4A5568') + ';font-size:12.5px">' + r[7] + '</td>',
    '  <td style="width:108px"><div class="row-actions"><span class="link">编辑</span><span class="link">日志</span><span style="color:#CBD5E0">⋯</span></div></td>',
    '</tr>'
  ].join('\n');
}

function listBody() {
  return [
    '<div class="card toolbar" style="padding:14px 16px">',
    '  <div class="toolbar__filters">',
    '    <div class="input search-box">' + icon('search', 'icon--16') + '<span class="input__placeholder">搜索接口名称或路径</span></div>',
    '    <div class="input select" style="width:118px"><span class="input__placeholder">接口类型</span>' + icon('chevDown', 'chev') + '</div>',
    '    <div class="input select" style="width:118px"><span class="input__placeholder">HTTP 方法</span>' + icon('chevDown', 'chev') + '</div>',
    '    <div class="input select" style="width:118px"><span class="input__placeholder">启用状态</span>' + icon('chevDown', 'chev') + '</div>',
    '  </div>',
    '  <div class="toolbar__actions">',
    '    <button class="btn">' + icon('tag') + '标签管理</button>',
    '    <button class="btn">' + icon('upload') + '导入</button>',
    '    <button class="btn">' + icon('download') + '导出</button>',
    '    <button class="btn btn--primary">' + icon('plus') + '新建接口</button>',
    '  </div>',
    '</div>',
    '<div class="table-card">',
    '  <table class="tbl">',
    '    <thead><tr>',
    '      <th style="width:36px"><span class="cb"></span></th>',
    '      <th style="width:72px">类型</th>',
    '      <th style="width:76px">方法</th>',
    '      <th>路径</th>',
    '      <th style="width:132px">名称</th>',
    '      <th style="width:132px">标签</th>',
    '      <th style="width:88px">团队</th>',
    '      <th style="width:64px">启用</th>',
    '      <th style="width:104px">最近调用</th>',
    '      <th style="width:108px" class="t-right">操作</th>',
    '    </tr></thead>',
    '    <tbody>',
    rows.map(rowHtml).join('\n'),
    '    </tbody>',
    '  </table>',
    '  <div class="table-foot"><span>共 12 条 · 每页 20 条</span><div class="pager"><span>‹</span><span class="on">1</span><span>2</span><span>›</span></div></div>',
    '</div>'
  ].join('\n');
}

export const entry = page({
  title: '接口管理',
  active: 'apis',
  teamTree: true,
  body: listBody() + [
    '',
    '<div class="fab-bubble">',
    '  <div class="fab-bubble__title">MockHub AI 助手 ' + aiBadge('Beta') + '</div>',
    '  <div class="fab-bubble__text">试试「最近 7 天调用最多的接口」，或让我帮你建一个 Mock。</div>',
    '  <div class="fab-bubble__close">' + icon('close', 'icon--14') + '</div>',
    '</div>',
    '<div class="fab">' + icon('ai') + '<span class="fab__status"></span></div>'
  ].join('\n')
});

export const chat = page({
  title: '接口管理',
  active: 'apis',
  teamTree: true,
  css: '.chat__body{gap:8px;padding:10px 14px 4px}.bubble-ai{font-size:12.5px;padding:8px 11px}.composer{padding:8px 14px 9px}.composer__box{padding:6px 10px 6px 12px}.composer__tip{margin-top:5px;font-size:10px}.stat-row{padding:2px 0}.ai-card__head{padding:8px 12px}.confirm .code{font-size:10.5px;line-height:1.75}.confirm .ln{width:26px;padding-right:8px}.confirm__note{font-size:10.5px;line-height:1.5}',
  body: listBody() + [
    '',
    '<section class="chat">',
    '  <div class="chat__head">',
    '    <div class="chat__avatar">' + icon('ai') + '</div>',
    '    <div>',
    '      <div class="chat__title">MockHub 助手 ' + aiBadge('Beta') + '</div>',
    '      <div class="chat__sub"><span class="dot"></span>在线 · DeepSeek-V3</div>',
    '    </div>',
    '    <div class="chat__actions">',
    '      <div class="chat__action">' + icon('history', 'icon--16') + '</div>',
    '      <div class="chat__action">' + icon('maximize', 'icon--16') + '</div>',
    '      <div class="chat__action">' + icon('close', 'icon--16') + '</div>',
    '    </div>',
    '  </div>',
    '  <div class="chat__context">',
    '    <span class="chip">范围：全部团队</span>',
    '    <span class="chip">' + icon('shield', 'icon--14') + '以你的权限运行</span>',
    '    <span style="margin-left:auto">' + icon('list', 'icon--14') + '</span>',
    '  </div>',
    '  <div class="chat__body">',
    messageUser('最近 7 天哪个接口被调用得最多？'),
    messageAI([
      '<div class="bubble-ai">近 7 天调用最多的是 <b>GET /api/order/detail</b>，共 <b>1,284</b> 次，占全部调用的 31%。</div>',
      statsCard()
    ]),
    messageUser('帮我创建一个 GET /api/user/info 的 Mock，返回姓名、头像、等级'),
    messageAI([
      '<div class="bubble-ai">已按「用户中心」团队生成草稿，确认后才会创建：</div>',
      confirmCard()
    ]),
    '  </div>',
    '  <div class="composer">',
    '    <div class="composer__box">',
    '      <div class="composer__input">继续追问，或描述要创建的接口…</div>',
    '      <div class="composer__foot">',
    '        <div class="composer__tool">' + icon('plus', 'icon--16') + '</div>',
    '        <div class="composer__tool">' + icon('wand', 'icon--16') + '</div>',
    '        <div class="composer__send">' + icon('send') + '</div>',
    '      </div>',
    '    </div>',
    '    <div class="composer__tip">' + icon('shield', 'icon--14') + 'AI 生成内容需确认后生效，不会自动保存</div>',
    '  </div>',
    '</section>'
  ].join('\n')
});

function messageUser(text) {
  return [
    '<div class="msg msg--user">',
    '  <div class="bubble-user">' + text + '</div>',
    '</div>'
  ].join('\n');
}

function messageAI(blocks) {
  return [
    '<div class="msg">',
    '  <div class="msg__avatar">' + icon('ai') + '</div>',
    '  <div class="msg__stack">',
    blocks.map(function (b) { return '    ' + b; }).join('\n'),
    '  </div>',
    '</div>'
  ].join('\n');
}

function statsCard() {
  return [
    '<div class="ai-card">',
    '  <div class="ai-card__head">' + icon('chart', 'icon--14') + '热门接口 · 近 7 天<span class="right">只读</span></div>',
    '  <div class="ai-card__body" style="padding:8px 12px">',
    statRow(1, 'GET', '/api/order/detail', '1,284'),
    statRow(2, 'POST', '/api/pay/create', '963'),
    statRow(3, 'GET', '/api/user/info', '742'),
    '  </div>',
    '</div>'
  ].join('\n');
}

function statRow(idx, method, path, count) {
  return [
    '<div class="stat-row">',
    '  <span class="stat-row__idx stat-row__idx--' + idx + '">' + idx + '</span>',
    '  <span class="stat-row__path">' + method + ' ' + path + '</span>',
    '  <span class="stat-row__num">' + count + '<span class="stat-row__unit">次</span></span>',
    '</div>'
  ].join('\n');
}

function confirmCard() {
  const lines = [
    pun('{') + ' ' + k('code') + pun(':') + ' ' + num('0') + pun(','),
    k('data') + pun(':') + ' ' + pun('{') + ' ' + k('name') + pun(':') + ' ' + str('张三') + pun(',') + ' ' + k('level') + pun(':') + ' ' + str('VIP') + ' ' + pun('}'),
    pun('}')
  ];
  return [
    '<div class="ai-card confirm">',
    '  <div class="ai-card__head">' + icon('edit', 'icon--14') + '待确认操作 · CREATE_API',
    '    <span class="countdown">' + icon('clock', 'icon--14') + '11:42</span></div>',
    '  <div class="ai-card__body" style="padding:10px 12px">',
    '    <div class="confirm__grid">',
    '      <div><div class="confirm__k">方法 / 路径</div><div class="confirm__v"><code>GET</code> <code>/api/user/info</code></div></div>',
    '      <div><div class="confirm__k">名称 / 团队</div><div class="confirm__v">用户信息查询 · <code>USER</code></div></div>',
    '    </div>',
    '    <div style="margin-top:8px;border:1px solid #F1F5F9;border-radius:8px;overflow:hidden">' + codeBlock(lines, { start: 1 }) + '</div>',
    '    <div class="confirm__note">' + icon('shield') + '确认后服务端会重新校验权限并写入，操作记入审计日志。</div>',
    '    <div class="confirm__actions">',
    '      <button class="btn btn--sm btn--ghost" style="color:#A3AED0">取消</button>',
    '      <button class="btn btn--sm btn--ghost">预览</button>',
    '      <button class="btn btn--sm btn--ghost">编辑</button>',
    '      <button class="btn btn--sm btn--primary">确认创建</button>',
    '    </div>',
    '  </div>',
    '</div>'
  ].join('\n');
}