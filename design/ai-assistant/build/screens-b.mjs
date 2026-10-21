import { page, icon, methodTag, chip, aiBadge, switchOn, codeBlock, k, str, num, pun } from './lib.mjs';

/* ---------------- 通用：接口编辑页背景 ---------------- */
function tabs() {
  return [
    '<div class="tabs-line">',
    '  <div class="tab-item tab-item--on"><span class="tab-dot"></span><span class="tab-index">①</span>成功响应</div>',
    '  <div class="tab-item"><span class="tab-dot"></span><span class="tab-index">②</span>空列表</div>',
    '  <div class="tab-item"><span class="tab-dot tab-dot--off"></span><span class="tab-index">③</span>错误响应<span style="font-size:10px;background:#F3F4F6;color:#9CA3AF;border-radius:8px;padding:0 5px">未启用</span></div>',
    '  <div class="tab-add">' + icon('plus', 'icon--16') + '</div>',
    '</div>'
  ].join('\n');
}

function formGrid() {
  return [
    '<div class="form-grid">',
    '  <div class="field"><span class="field__label">返回体名称</span><div class="input">成功响应</div></div>',
    '  <div class="field field--sm"><span class="field__label">响应状态码</span><div class="input">200</div></div>',
    '  <div class="field"><span class="field__label">Content-Type</span><div style="display:flex;align-items:center;gap:10px;height:38px"><span class="ct-hint">自动识别：JSON ' + icon('check', 'icon--14 check') + '</span><span class="seg" style="margin-left:auto"><span class="seg__item seg__item--on">JSON</span><span class="seg__item">XML</span><span class="seg__item">Text</span></span></div></div>',
    '  <div class="field field--sm"><span class="field__label">延迟 (ms)</span><div class="input">0</div></div>',
    '</div>'
  ].join('\n');
}

function editorToolbar() {
  return [
    '<div class="editor-toolbar">',
    '  <span class="seg"><span class="seg__item seg__item--on">文本</span><span class="seg__item">文件</span></span>',
    '  <button class="btn btn--sm">' + icon('magic') + '格式化</button>',
    '  <button class="btn btn--sm">' + icon('upload') + '上传文本</button>',
    '  <button class="btn btn--sm">' + icon('magic') + '插入变量 ' + icon('chevDown', 'icon--14') + '</button>',
    '  <button class="btn btn--sm btn--ai-edge">' + icon('ai') + 'AI 生成</button>',
    '  <span class="spacer"></span>',
    '  <span class="hint">1,024 字符</span>',
    '</div>'
  ].join('\n');
}

const bodyJson = [
  pun('{'),
  '  ' + k('code') + pun(':') + ' ' + num('0') + pun(','),
  '  ' + k('message') + pun(':') + ' ' + str('success') + pun(','),
  '  ' + k('data') + pun(':') + ' ' + pun('{'),
  '    ' + k('id') + pun(':') + ' ' + str('10086') + pun(','),
  '    ' + k('nickname') + pun(':') + ' ' + str('林晚') + pun(','),
  '    ' + k('avatar') + pun(':') + ' ' + str('https://cdn.mockhub.dev/a/8f3a.png') + pun(','),
  '    ' + k('level') + pun(':') + ' ' + str('VIP') + pun(','),
  '    ' + k('levelLabel') + pun(':') + ' ' + str('黄金会员') + pun(','),
  '    ' + k('registeredAt') + pun(':') + ' ' + str('2024-03-18T09:24:00+08:00'),
  '  ' + pun('}'),
  pun('}')
];

const condCollapsed = [
  '<div class="cond">',
  '  <div class="cond__head">' + icon('chevRight', 'icon--14') + '<b>响应规则</b><span class="muted" style="font-size:11.5px">所有条件都满足才命中（AND）</span><span class="spacer"></span><span class="muted" style="font-size:11.5px">无规则（兜底）</span></div>',
  '</div>'
].join('\n');

function responseBackground() {
  return [
    '<div class="card edit-card">',
    '  <h3 class="section-title">返回体配置</h3>',
    '  <div class="mode-bar"><span>选择方式</span><span class="seg"><span class="seg__item seg__item--on">按条件匹配</span><span class="seg__item">等概率随机</span></span><span>按条件依次匹配；多个启用返回体时必须保留一个无条件兜底。</span></div>',
    tabs(),
    condCollapsed,
    formGrid(),
    editorToolbar(),
    '<div class="monaco">' + codeBlock(bodyJson, { start: 1 }) + '</div>',
    '</div>'
  ].join('\n');
}

/* ---------------- 03 AI 生成响应体 ---------------- */
const genJson = [
  pun('{'),
  '  ' + k('code') + pun(':') + ' ' + num('0') + pun(','),
  '  ' + k('message') + pun(':') + ' ' + str('success') + pun(','),
  '  ' + k('data') + pun(':') + ' ' + pun('{'),
  '    ' + k('id') + pun(':') + ' ' + str('{{request.path.id}}') + pun(','),
  '    ' + k('nickname') + pun(':') + ' ' + str('林晚') + pun(','),
  '    ' + k('avatar') + pun(':') + ' ' + str('https://cdn.mockhub.dev/avatar/8f3a.png') + pun(','),
  '    ' + k('level') + pun(':') + ' ' + str('VIP') + pun(','),
  '    ' + k('levelLabel') + pun(':') + ' ' + str('黄金会员') + pun(','),
  '    ' + k('registeredAt') + pun(':') + ' ' + str('2024-03-18T09:24:00+08:00') + pun(','),
  '    ' + k('stats') + pun(':') + ' ' + pun('{'),
  '      ' + k('orders') + pun(':') + ' ' + num('42') + pun(','),
  '      ' + k('coupons') + pun(':') + ' ' + num('3'),
  '    ' + pun('}'),
  '  ' + pun('}'),
  pun('}')
];

function genDrawer() {
  return [
    '<aside class="ai-drawer">',
    '  <div class="ai-drawer__head">',
    '    <div class="modal__icon">' + icon('ai') + '</div>',
    '    <div style="flex:1">',
    '      <div class="modal__title">AI 生成响应体 ' + aiBadge('Beta') + '</div>',
    '      <div class="modal__desc">根据接口信息和你的描述生成内容，确认后才会写入编辑器</div>',
    '    </div>',
    '    <div class="modal__close">' + icon('close', 'icon--16') + '</div>',
    '  </div>',
    '  <div class="ai-drawer__ctx">',
    '    ' + methodTag('GET') + '<span class="chip mono">/api/user/{id}</span><span class="chip">① 成功响应</span><span class="chip">JSON</span><span class="chip">用户中心</span>',
    '  </div>',
    '  <div class="ai-drawer__body">',
    '    <div class="ai-label">描述你想要的响应 <span class="muted">可选</span></div>',
    '    <div class="textarea textarea--focus" style="min-height:86px">返回用户详情：昵称、头像、等级、注册时间；等级从普通 / VIP / SVIP 中取一个，注册时间用 ISO 格式<span class="caret"></span></div>',
    '    <div class="ai-row">',
    '      <div class="field"><span class="ai-label">生成方式</span><span class="seg"><span class="seg__item seg__item--on">新建内容</span><span class="seg__item">补全现有</span></span></div>',
    '      <div class="field"><span class="ai-label">字段规模</span><span class="seg"><span class="seg__item">精简</span><span class="seg__item seg__item--on">标准</span><span class="seg__item">丰富</span></span></div>',
    '    </div>',
    '    <div class="preview-head">生成结果 <span class="muted" style="font-weight:400">JSON · 16 行</span><span class="right"><span class="link" style="font-size:12px">' + icon('refresh', 'icon--14') + ' 重新生成</span></span></div>',
    '    <div class="code-panel">' + codeBlock(genJson, { start: 1 }) + '</div>',
    '    <div class="ai-tip">' + icon('info', 'icon--14') + '<span>已识别路径参数 <b>{id}</b>，生成时自动写入动态变量 <code>{{request.path.id}}</code>；不会发送 Authorization / Cookie。</span></div>',
    '  </div>',
    '  <div class="ai-drawer__foot">',
    '    <span class="foot-note">' + icon('shield', 'icon--14') + '确认前不会写入编辑器</span>',
    '    <button class="btn">取消</button>',
    '    <button class="btn btn--soft">预览全部</button>',
    '    <button class="btn btn--primary">插入到编辑器</button>',
    '  </div>',
    '</aside>'
  ].join('\n');
}

export const generate = page({
  title: '编辑接口',
  active: 'apis',
  teamTree: true,
  css: '.main{padding-top:0}.edit-card{border-radius:0 0 12px 12px}.main{overflow:hidden}',
  body: responseBackground(),
  overlay: genDrawer()
});

/* ---------------- 04 AI 生成条件规则 ---------------- */
function sampleTree() {
  const lines = [
    ['data', 0, true], ['userType', 1, false], ['orderNo', 1, false], ['items', 1, true],
    ['[0].skuId', 2, false], ['[0].count', 2, false], ['header', 0, true], ['env', 1, false], ['tenantId', 1, false]
  ];
  return lines.map(function (l) {
    const indent = 8 + l[1] * 12;
    const leaf = !l[2];
    return '<div class="tree-line" style="padding-left:' + indent + 'px">' +
      (leaf ? '<span style="width:10px"></span>' : icon('chevDown', 'chev')) +
      '<span class="' + (leaf ? '' : 'k') + '">' + l[0] + '</span>' +
      (leaf ? '<span style="color:#CBD5E0;margin-left:auto">string</span>' : '') +
      '</div>';
  }).join('\n');
}

function condRow(src, path, op, val) {
  return '<div class="cond-row"><span class="src">' + src + '</span><span class="p">' + path + '</span><span class="op">' + op + '</span><span class="v">' + val + '</span>' + icon('close', 'icon--14') + '</div>';
}

function conditionsBackground() {
  return [
    '<div class="card edit-card" style="padding-bottom:22px">',
    '  <h3 class="section-title">返回体配置</h3>',
    '  <div class="mode-bar"><span>选择方式</span><span class="seg"><span class="seg__item seg__item--on">按条件匹配</span><span class="seg__item">等概率随机</span></span><span>按条件依次匹配；多个启用返回体时必须保留一个无条件兜底。</span></div>',
    tabs(),
    '  <div class="cond">',
    '    <div class="cond__head">' + icon('chevDown', 'icon--14') + '<b>响应规则</b><span class="muted" style="font-size:11.5px">所有条件都满足才命中（AND）</span><span class="spacer"></span><span class="muted" style="font-size:11.5px">2 条规则</span></div>',
    '    <div class="cond__toolbar">',
    '      <span class="label">新增条件来源</span>',
    '      <span class="seg"><span class="seg__item seg__item--on">Body</span><span class="seg__item">Query</span></span>',
    '      <button class="btn btn--sm btn--plain">' + icon('edit') + '粘贴示例</button>',
    '      <button class="btn btn--sm btn--ai-edge">' + icon('ai') + 'AI 生成规则</button>',
    '      <span class="spacer"></span>',
    '      <span class="hint">' + icon('info', 'icon--14') + ' 规则空时，该返回体作为兜底</span>',
    '    </div>',
    '    <div class="cond__split">',
    '      <div class="cond__tree"><div class="cond__tree-title">Body 示例（18 个字段）</div>' + sampleTree() + '</div>',
    '      <div class="cond__table">',
    condRow('BODY', 'userType', '等于', '"vip"'),
    condRow('BODY', 'items[0].count', '大于', '2'),
    '        <div class="cond-empty">点击左侧字段可快速追加条件</div>',
    '      </div>',
    '    </div>',
    '  </div>',
    formGrid(),
    editorToolbar(),
    '<div class="monaco">' + codeBlock(bodyJson, { start: 1 }) + '</div>',
    '</div>'
  ].join('\n');
}

function ruleRow(on, src, path, op, val) {
  return [
    '<div class="rule-row">',
    '  <span class="cb' + (on ? ' cb--on' : '') + '">' + (on ? icon('check') : '') + '</span>',
    '  <span class="rule-row__src">' + src + '</span>',
    '  <span class="rule-row__path">' + path + '</span>',
    '  <span class="rule-row__op">' + op + '</span>',
    '  <span class="rule-row__val">' + val + '</span>',
    '</div>'
  ].join('\n');
}

function condDrawer() {
  return [
    '<aside class="ai-drawer">',
    '  <div class="ai-drawer__head">',
    '    <div class="modal__icon">' + icon('ai') + '</div>',
    '    <div style="flex:1">',
    '      <div class="modal__title">AI 生成响应规则 ' + aiBadge('Beta') + '</div>',
    '      <div class="modal__desc">把自然语言描述转成现有 conditions 结构，应用前可逐条确认</div>',
    '    </div>',
    '    <div class="modal__close">' + icon('close', 'icon--16') + '</div>',
    '  </div>',
    '  <div class="ai-drawer__ctx">',
    '    <span class="chip">① 成功响应</span><span class="chip">Body 示例 18 个字段</span><span class="chip">按条件匹配</span>',
    '  </div>',
    '  <div class="ai-drawer__body">',
    '    <div class="ai-label">用自然语言描述命中条件</div>',
    '    <div class="textarea textarea--focus" style="min-height:100px">当 header 里的 env 是 test，并且 body 的 userType 是 vip 时命中；如果 orderNo 是 20240601 也命中<span class="caret"></span></div>',
    '    <div class="preview-head">将生成 3 条规则 <span class="muted" style="font-weight:400">· 已选 2 条</span><span class="right"><span class="link" style="font-size:12px">' + icon('refresh', 'icon--14') + ' 重新生成</span></span></div>',
    '    <div class="rule-table">',
    '      <div class="rule-table__head"><span></span><span>来源</span><span>字段路径</span><span>运算符</span><span>值</span></div>',
    ruleRow(true, 'Header', 'env', '等于', '"test"'),
    ruleRow(true, 'Body', 'userType', '等于', '"vip"'),
    ruleRow(false, 'Body', 'orderNo', '等于', '"20240601"'),
    '      <div class="rule-warn">' + icon('warning', 'icon--14') + '<span>第 3 条未勾选：当前 Body 示例中没有 <b>orderNo</b> 字段，确认字段名后再应用。</span></div>',
    '    </div>',
    '    <div class="ai-tip">' + icon('shield', 'icon--14') + '<span>规则会写入现有 conditions 结构，应用前由后端再次校验字段与运算符，不会直接覆盖未勾选的规则。</span></div>',
    '    <div class="ai-label">应用方式</div>',
    '    <div class="apply-grid">',
    '      <div class="apply-opt apply-opt--on">' + '<span class="radio radio--on"></span>' + '<div><div class="apply-opt__t">追加到现有规则</div><div class="apply-opt__d">保留原有 2 条，把勾选的 2 条加在后面</div></div></div>',
    '      <div class="apply-opt">' + '<span class="radio"></span>' + '<div><div class="apply-opt__t">替换现有规则</div><div class="apply-opt__d">清空原有 2 条后再写入，需二次确认</div></div></div>',
    '    </div>',
    '  </div>',
    '  <div class="ai-drawer__foot">',
    '    <span class="foot-note">' + icon('info', 'icon--14') + '已选 2 条，追加到现有 2 条之后</span>',
    '    <button class="btn">取消</button>',
    '    <button class="btn btn--primary">应用 2 条规则</button>',
    '  </div>',
    '</aside>'
  ].join('\n');
}

export const conditions = page({
  title: '编辑接口',
  active: 'apis',
  teamTree: true,
  css: '.main{padding-top:0}.edit-card{border-radius:0 0 12px 12px}.main{overflow:hidden}',
  body: conditionsBackground(),
  overlay: condDrawer()
});