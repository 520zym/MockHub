import { page, icon, methodTag, teamTag, chip, aiBadge, switchOn, codeBlock, k, str, num, pun } from './lib.mjs';

/* ---------------- 07 请求日志 · AI 匹配诊断 ---------------- */
const logs = [
  ['2026-06-23 17:04:22', 'POST', '/mock/pay/api/order/detail', 200, 138],
  ['2026-06-23 17:03:58', 'GET', '/mock/user/api/user/info', 200, 42],
  ['2026-06-23 17:02:41', 'GET', '/mock/order/api/order/list', 404, 8],
  ['2026-06-23 17:01:09', 'POST', '/mock/pay/api/pay/create', 200, 96],
  ['2026-06-23 16:58:30', 'POST', '/mock/pay/api/refund/apply', 500, 210],
  ['2026-06-23 16:55:12', 'GET', '/mock/user/api/coupon/list', 200, 31],
  ['2026-06-23 16:51:44', 'GET', '/mock/pay/api/order/detail', 200, 12],
  ['2026-06-23 16:47:03', 'POST', '/mock/pay/api/pay/create', 200, 58]
];

function logRow(r, idx) {
  const codeColor = r[3] >= 500 ? '#DC2626' : r[3] >= 400 ? '#EF4444' : '#10B981';
  return [
    '<tr' + (idx === 0 ? ' style="background:#F7F8FF"' : '') + '>',
    '  <td style="color:#6B7280;font-family:monospace;font-size:12px;white-space:nowrap">' + r[0] + '</td>',
    '  <td style="width:88px">' + methodTag(r[1]) + '</td>',
    '  <td class="path" style="font-family:\'SF Mono\',Menlo,monospace">' + r[2] + '</td>',
    '  <td style="width:80px"><span style="color:' + codeColor + ';font-weight:600">' + r[3] + '</span></td>',
    '  <td style="width:80px;color:#A3AED0">' + r[4] + ' ms</td>',
    '  <td style="width:96px"><span class="link">查看详情</span></td>',
    '</tr>'
  ].join('\n');
}

function chainItem(no, title, status, desc, kind, last) {
  return [
    '<div class="chain-item">',
    '  <div class="chain-rail"><div class="chain-dot chain-dot--' + (kind === 'hit' ? 'hit' : 'miss') + '">' + no + '</div>' + (last ? '' : '<div class="chain-line"></div>') + '</div>',
    '  <div class="chain-body">',
    '    <div class="chain-title">' + title + ' <span class="badge-' + kind + '">' + status + '</span></div>',
    '    <div class="chain-desc">' + desc + '</div>',
    '  </div>',
    '</div>'
  ].join('\n');
}

function drawer() {
  return [
    '<aside class="ai-drawer" style="width:760px">',
    '  <div class="drawer__head">',
    '    <div class="drawer__title">请求详情</div>',
    '    <span class="chip mono" style="font-size:11px">#RQ-0623-0842</span>',
    '    <span style="flex:1"></span>',
    '    <div class="modal__close">' + icon('close', 'icon--16') + '</div>',
    '  </div>',
    '  <div class="drawer__body" style="overflow:hidden">',
    '    <div style="padding:18px 24px">',
    '      <div class="req-summary">',
    '        <div class="req-line">' + methodTag('POST') + '<code>/mock/pay/api/order/detail</code><span class="status-pill">200</span><span style="color:#A3AED0;font-size:12px">138 ms</span><span class="chip" style="height:22px;font-size:11px;margin-left:auto">返回体 ② VIP 响应</span></div>',
    '        <div class="req-meta"><span>2026-06-23 17:04:22</span><span>·</span><span>支付中心</span><span>·</span><span>来源 IP 10.20.8.31</span><span>·</span><span>trace 8f3a-c21</span></div>',
    '      </div>',
    '',
    '      <div class="diag">',
    '        <div class="diag-head"><div class="modal__icon">' + icon('ai') + '</div><b style="font-size:14px">AI 匹配诊断</b><span class="chip" style="height:22px;font-size:11px">DeepSeek-V3</span><span style="flex:1"></span><span class="link" style="font-size:12px">' + icon('refresh', 'icon--14') + '重新诊断</span></div>',
    '        <div class="verdict">',
    '          <div class="verdict__k">' + icon('checkCircle', 'icon--14') + '命中 ② VIP 响应</div>',
    '          <div class="verdict__v">3 个启用返回体按条件顺序匹配，② 的条件全部满足后停止，③ 兜底未参与。</div>',
    '        </div>',
    '        <div class="chain" style="margin-top:14px">',
    chainItem('1', '① 成功响应', '未命中', 'header.env 期望 <code>"test"</code>，实际 <code>"prod"</code>', 'miss'),
    chainItem('2', '② VIP 响应', '命中', 'body.userType 期望 <code>"vip"</code>，实际 <code>"vip"</code>', 'hit'),
    chainItem('3', '③ 兜底返回', '未参与', '前一条已命中，按顺序停止匹配', 'skip', true),
    '        </div>',
    '        <div class="suggest-card" style="margin-top:4px">',
    '          <div class="suggest-card__title">' + icon('magic', 'icon--14') + '建议</div>',
    '          <div class="suggest-card__text">① 对 header.env 的取值限定过死。若希望线上请求（env=prod）也命中 ①，可把条件改为「header.env 存在」，或在 ② 之前新增一条 env=prod 的条件返回体。</div>',
    '          <div class="suggest-card__actions"><button class="btn btn--sm btn--plain">' + icon('edit') + '应用到 ① 的规则</button><button class="btn btn--sm">' + icon('copy') + '复制诊断</button><span class="hint" style="font-size:11px;align-self:center">应用前会打开规则预览确认</span></div>',
    '        </div>',
    '      </div>',
    '',
    '      <div style="margin-top:16px">',
    '        <div class="detail-label">请求快照（敏感字段已脱敏）</div>',
    '        <div class="snap-grid">',
    '          <div><div class="hint">Headers</div><pre class="pre">{' + '\n' + '  "content-type": "application/json",' + '\n' + '  "env": "prod",' + '\n' + '  "tenant": "pay",' + '\n' + '  "authorization": "Bearer ••••••"' + '\n' + '}</pre></div>',
    '          <div><div class="hint">Body</div><pre class="pre">{' + '\n' + '  "userId": 10086,' + '\n' + '  "userType": "vip",' + '\n' + '  "orderId": "20260623001"' + '\n' + '}</pre></div>',
    '        </div>',
    '      </div>',
    '    </div>',
    '  </div>',
    '  <div class="ai-drawer__foot">',
    '    <span class="foot-note">' + icon('shield', 'icon--14') + '诊断基于本次请求快照，不会修改任何配置</span>',
    '    <button class="btn">关闭</button>',
    '    <button class="btn btn--primary">在编辑页查看 ②</button>',
    '  </div>',
    '</aside>'
  ].join('\n');
}

export const matchExplain = page({
  title: '请求日志',
  active: 'logs',
  body: [
    '<div class="log-toolbar" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:18px">',
    '  <div class="seg" style="padding:4px"><span class="seg__item" style="height:32px;padding:0 18px">操作日志</span><span class="seg__item seg__item--on" style="height:32px;padding:0 18px">请求日志</span></div>',
    '  <div class="input select" style="width:200px"><span class="select__value">' + teamTag('PAY') + '支付中心</span>' + icon('chevDown', 'chev') + '</div>',
    '</div>',
    '<div class="table-card">',
    '  <table class="tbl">',
    '    <thead><tr><th style="width:170px">时间</th><th style="width:88px">方法</th><th>请求路径</th><th style="width:80px">响应码</th><th style="width:80px">耗时</th><th style="width:96px">操作</th></tr></thead>',
    '    <tbody>',
    logs.map(logRow).join('\n'),
    '    </tbody>',
    '  </table>',
    '  <div class="table-foot"><span>共 248 条 · 每页 50 条</span><div class="pager"><span>‹</span><span class="on">1</span><span>2</span><span>3</span><span>›</span></div></div>',
    '</div>'
  ].join('\n'),
  overlay: drawer()
});

/* ---------------- 08 状态与边界规范 ---------------- */
function frame(title, badge, body, caption) {
  return [
    '<div class="frame">',
    '  <div class="frame__head"><span class="frame__title">' + title + '</span>' + (badge ? '<span class="frame__badge">' + badge + '</span>' : '') + '</div>',
    '  <div class="frame__body">' + body + '</div>',
    '  <div class="frame__caption">' + caption + '</div>',
    '</div>'
  ].join('\n');
}

const miniCard = function (kind) {
  if (kind === 'pending') {
    return [
      '<div class="ai-card confirm" style="width:100%">',
      '  <div class="ai-card__head" style="font-size:11px">' + icon('edit', 'icon--14') + '待确认 · CREATE_API<span class="countdown">' + icon('clock', 'icon--14') + '11:42</span></div>',
      '  <div class="ai-card__body" style="padding:8px 10px;font-size:11.5px;color:#4A5568">GET <code style="font-family:monospace">/api/user/info</code> · 用户中心</div>',
      '  <div class="ai-card__foot" style="padding:7px 10px"><span class="hint" style="font-size:10px">确认后写入</span><span style="margin-left:auto;display:flex;gap:6px"><span class="btn btn--sm btn--ghost" style="height:24px">取消</span><span class="btn btn--sm btn--primary" style="height:24px">确认创建</span></span></div>',
      '</div>'
    ].join('\n');
  }
  if (kind === 'confirmed') {
    return [
      '<div class="ai-card done-card" style="width:100%">',
      '  <div class="ai-card__head" style="font-size:11px">' + icon('checkCircle', 'icon--14') + '已创建 · GET /api/user/info<span class="right">17:02</span></div>',
      '  <div class="ai-card__body" style="padding:9px 10px;font-size:11.5px;color:#4A5568">由张一鸣确认，接口已加入「用户中心」团队。</div>',
      '  <div class="ai-card__foot" style="padding:7px 10px;border-top-color:#E7F6F0"><span class="link" style="font-size:11px">查看接口 →</span><span style="margin-left:auto;font-size:10px;color:#A3AED0">操作日志已记录</span></div>',
      '</div>'
    ].join('\n');
  }
  return [
    '<div class="ai-card" style="width:100%;opacity:.7">',
    '  <div class="ai-card__head" style="font-size:11px;color:#A3AED0">' + icon('clock', 'icon--14') + '已过期 · CREATE_API<span class="right">12:00</span></div>',
    '  <div class="ai-card__body" style="padding:9px 10px;font-size:11.5px;color:#A3AED0">草稿已失效，请让 AI 重新生成。</div>',
    '  <div class="ai-card__foot" style="padding:7px 10px"><span class="hint" style="font-size:10px">按钮不可用</span><span style="margin-left:auto;display:flex;gap:6px"><span class="btn btn--sm btn--disabled" style="height:24px">取消</span><span class="btn btn--sm btn--primary btn--disabled" style="height:24px">确认创建</span></span></div>',
    '</div>'
  ].join('\n');
};

const skeleton = [
  '<div style="display:flex;gap:8px;margin-bottom:10px"><div class="sk" style="width:24px;height:24px;border-radius:8px"></div><div class="sk" style="height:12px;width:36%;margin-top:6px"></div></div>',
  '<div class="sk" style="height:10px;width:92%;margin-bottom:7px"></div>',
  '<div class="sk" style="height:10px;width:78%;margin-bottom:7px"></div>',
  '<div class="sk" style="height:10px;width:85%;margin-bottom:12px"></div>',
  '<div class="sk" style="height:64px;width:100%;border-radius:10px"></div>',
  '<div style="display:flex;gap:8px;margin-top:10px;align-items:center"><span class="spinner"></span><span class="hint" style="font-size:11px">正在生成响应体…</span><span style="margin-left:auto" class="link">停止生成</span></div>'
].join('\n');

const searchCard = [
  '<div class="ai-card" style="width:100%">',
  '  <div class="ai-card__head" style="font-size:11px">' + icon('search', 'icon--14') + '找到 3 个接口<span class="right">用户中心</span></div>',
  '  <div class="ai-card__body" style="padding:6px 10px">',
  '    <div style="display:flex;align-items:center;gap:8px;padding:5px 0;border-bottom:1px solid #F8FAFC"><span class="m-tag m-get" style="font-size:10px">GET</span><span class="mono" style="font-size:11px">/api/user/info</span><span style="margin-left:auto;font-size:10.5px;color:#A3AED0">用户信息</span></div>',
  '    <div style="display:flex;align-items:center;gap:8px;padding:5px 0;border-bottom:1px solid #F8FAFC"><span class="m-tag m-post" style="font-size:10px">POST</span><span class="mono" style="font-size:11px">/api/user/update</span><span style="margin-left:auto;font-size:10.5px;color:#A3AED0">更新资料</span></div>',
  '    <div style="display:flex;align-items:center;gap:8px;padding:5px 0"><span class="m-tag m-get" style="font-size:10px">GET</span><span class="mono" style="font-size:11px">/api/user/level</span><span style="margin-left:auto;font-size:10.5px;color:#A3AED0">用户等级</span></div>',
  '  </div>',
  '</div>'
].join('\n');

const statsMini = [
  '<div class="ai-card" style="width:100%">',
  '  <div class="ai-card__head" style="font-size:11px">' + icon('chart', 'icon--14') + '近 7 天调用<span class="right">只读</span></div>',
  '  <div class="ai-card__body" style="padding:8px 10px">',
  '    <div style="display:flex;align-items:baseline;gap:6px"><span style="font-size:22px;font-weight:700;color:#1B2559">1,284</span><span style="font-size:11px;color:#10B981">+18%</span><span style="margin-left:auto;font-size:10.5px;color:#A3AED0">GET /api/order/detail</span></div>',
  '    <div class="spark" style="margin-top:8px;height:30px;gap:4px"><span style="height:10px"></span><span style="height:16px"></span><span style="height:12px" class="on"></span><span style="height:20px" class="on"></span><span style="height:15px"></span><span style="height:24px" class="on"></span><span style="height:28px" class="on"></span></div>',
  '  </div>',
  '</div>'
].join('\n');

const longCode = [
  '<div class="fade-mask"><div class="code" style="opacity:.85">',
  '  <div class="code__row"><span class="ln">1</span><span>' + pun('{') + '</span></div>',
  '  <div class="code__row"><span class="ln">2</span><span>  ' + k('code') + pun(':') + ' ' + num('0') + pun(',') + '</span></div>',
  '  <div class="code__row"><span class="ln">3</span><span>  ' + k('data') + pun(':') + ' ' + pun('{') + '</span></div>',
  '  <div class="code__row"><span class="ln">4</span><span>    ' + k('name') + pun(':') + ' ' + str('张三') + pun(',') + '</span></div>',
  '  <div class="code__row"><span class="ln">5</span><span>    ' + k('avatar') + pun(':') + ' ' + str('https://…') + pun(',') + '</span></div>',
  '  <div class="code__row"><span class="ln">6</span><span>    ' + k('level') + pun(':') + ' ' + str('VIP') + '</span></div>',
  '  <div class="code__row"><span class="ln">7</span><span>  ' + pun('}') + '</span></div>',
  '  <div class="code__row"><span class="ln">8</span><span>' + pun('}') + '</span></div>',
  '</div></div>',
  '<div style="text-align:center;margin-top:-10px"><span class="link" style="font-size:11px">展开全部 42 行</span></div>'
].join('\n');

export const states = [
  '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>组件状态 · MockHub AI 设计稿</title>',
  '<link rel="stylesheet" href="assets/mock.css"><style>.frame__body{gap:10px}.state-cap{font-size:10.5px;color:#A3AED0;margin-top:8px;text-align:center}.spinner{width:14px;height:14px;border-radius:50%;border:2px solid #E0E7FF;border-top-color:#6366F1;display:inline-block}.fab--pulse{box-shadow:0 0 0 0 rgba(99,102,241,.45);animation:pulse 1.8s infinite}@keyframes pulse{70%{box-shadow:0 0 0 14px rgba(99,102,241,0)}100%{box-shadow:0 0 0 0 rgba(99,102,241,0)}}.frame__body{background:#FDFDFF}</style></head><body class="scrollable" style="overflow:auto">',
  '<div class="board">',
  '  <div class="board__head">',
  '    <div class="board__title">AI 组件 · 状态与边界</div>',
  '    <div class="board__desc">覆盖加载、失败、未配置、权限受限与长内容等分支，保证 AI 不阻断原有 Mock 流程。</div>',
  '  </div>',
  '  <div class="board__grid">',
  frame('浮动入口', 'entry', [
    '<div style="display:flex;gap:28px;justify-content:center;align-items:flex-start">',
    '  <div><div class="fab fab--sm">' + icon('ai') + '</div><div class="state-cap">默认</div></div>',
    '  <div><div class="fab fab--sm fab--hover">' + icon('ai') + '</div><div class="state-cap">悬停</div></div>',
    '  <div><div class="fab fab--sm fab--pulse">' + icon('ai') + '</div><div class="state-cap">生成中</div></div>',
    '</div>'
  ].join(''), '右下角全局入口；悬停上浮 2px，生成中显示脉冲环，点击展开对话。'),
  frame('确认卡片', 'pending action', [
    miniCard('pending'),
    '<div style="display:flex;gap:8px;justify-content:center;margin-top:4px"><span class="badge-hit">PENDING</span><span class="badge-hit">CONFIRMED</span><span class="badge-skip">EXPIRED</span></div>'
  ].join(''), '状态由后端 pending action 维护（PENDING / CONFIRMED / CANCELLED / EXPIRED），前端只展示与触发。'),
  frame('生成中', 'loading', skeleton, '生成过程可取消；取消后不写入任何内容，已输入的描述保留。'),
  frame('生成失败', 'error', [
    '<div class="state-error">' + icon('warning') + '<div><div class="state-error__t">生成失败：模型服务超时</div><div class="state-error__d">已等待 30s，未收到响应。接口信息已保留，可直接重试。</div></div></div>',
    '<div style="display:flex;gap:8px;margin-top:10px"><button class="btn btn--sm btn--primary">' + icon('refresh') + '重试</button><button class="btn btn--sm">改用模板生成</button></div>'
  ].join(''), '超时、限流、鉴权失败统一展示可读原因；不会自动重试，避免重复计费。'),
  frame('未启用 AI', 'empty', [
    '<div class="state-empty"><div class="state-empty__icon">' + icon('ai') + '</div><div class="state-empty__t">AI 助手尚未启用</div><div class="state-empty__d">管理员配置模型服务后即可使用生成与诊断能力，当前不影响接口编辑。</div>',
    '<div style="display:flex;gap:8px;justify-content:center"><button class="btn btn--sm btn--primary">去配置</button><button class="btn btn--sm">了解能力</button></div></div>',
    '<div style="text-align:center;font-size:10.5px;color:#A3AED0;margin-top:8px">普通成员看不到「去配置」，显示「联系超级管理员」</div>'
  ].join(''), '未配置时入口保持可见但降级为说明，不新增阻断。'),
  frame('权限受限', 'permission', [
    '<div class="state-empty"><div class="state-empty__icon" style="color:#A3AED0;background:#F7F8FA;border-color:#F1F5F9">' + icon('shield') + '</div><div class="state-empty__t">当前团队未开放 AI 能力</div><div class="state-empty__d">AI 会继承你的权限与团队范围，不能跨团队检索或创建接口。</div>',
    '<div style="display:flex;gap:8px;justify-content:center"><button class="btn btn--sm btn--plain">切换到有权限的团队</button></div></div>'
  ].join(''), '越权请求由后端拒绝并记录；前端不缓存跨团队结果。'),
  frame('搜索结果卡', 'chat card', searchCard, '只展示当前用户可见的接口，点击定位到接口详情。'),
  frame('统计卡', 'chat card', statsMini, '附带统计口径与时间范围，避免把 Mock 调用量误读为线上流量。'),
  frame('长内容折叠', 'code', longCode, '超过 20 行折叠，展开后在面板内滚动；复制始终复制完整内容。'),
  '  </div>',
  '</div>',
  '</body></html>'
].join('\n');
