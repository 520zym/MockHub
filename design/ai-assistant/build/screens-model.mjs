import { icon } from './lib.mjs';

const head = [
  '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>默认模型选择 · MockHub 设计稿</title>',
  '<link rel="stylesheet" href="assets/mock.css">',
  '<link rel="stylesheet" href="assets/settings-v2.css">',
  '<style>',
  '.doc__hero{background:radial-gradient(120% 120% at 10% 0%, #EEF0FF 0%, #F6F4FF 42%, #FBFBFE 100%)}',
  '.doc__note{background:#fff;border-radius:12px;padding:14px 16px;font-size:11.5px;color:#4A5568;line-height:1.8}',
  '.doc__note b{color:#1B2559}',
  '</style></head>',
  '<body class="scrollable" style="overflow:auto;background:#F5F6FA">',
  '<div class="doc">'
].join('\n');

const foot = '</div></body></html>';

function modelField(value, tag, open) {
  return [
    '<div class="model-field">',
    '  <div class="input model-input"><span class="model-id">' + value + '</span>' + (tag ? '<span class="model-chip">' + tag + '</span>' : '') + '</div>',
    '  <button class="btn btn--plain btn--sm">' + icon(open ? 'chevDown' : 'chevDown') + '</button>',
    '</div>'
  ].join('');
}

function modelItem(id, desc, tag, on, tagClass) {
  return [
    '<div class="model-item' + (on ? ' model-item--on' : '') + '">',
    '  <div class="model-item__main"><div class="model-item__id">' + id + '</div><div class="model-item__desc">' + desc + '</div></div>',
    tag ? '<span class="model-tag ' + (tagClass || '') + '">' + tag + '</span>' : '',
    on ? '<span class="model-item__check">' + icon('check') + '</span>' : '',
    '</div>'
  ].join('');
}

function searchRow(placeholder) {
  return [
    '<div class="model-search">' + icon('search') + '<span class="placeholder">' + placeholder + '</span></div>'
  ].join('');
}

function pickerPop(opts) {
  const live = opts.live;
  return [
    '<div class="model-pop">',
    searchRow(live ? '搜索 8 个模型' : '搜索模型 ID，如 deepseek-chat'),
    '<div class="model-group">',
    '  <div class="model-group__head">推荐模型<span class="grow"></span><span>2 个</span></div>',
    modelItem('deepseek-chat', '通用对话 · 适合生成响应体 / 条件', '推荐', true, 'model-tag--rec'),
    modelItem('deepseek-reasoner', '深度推理 · 响应更慢，适合复杂诊断', '推理', false, 'model-tag--reason'),
    '</div>',
    live ? [
      '<div class="model-group">',
      '  <div class="model-group__head">来自 api.deepseek.com<span class="grow"></span><span>共 8 个</span></div>',
      modelItem('deepseek-chat', '通用对话 · 128K 上下文', '服务商', false, 'model-tag--live'),
      modelItem('deepseek-reasoner', '深度推理 · 64K 上下文', '服务商', false, 'model-tag--live'),
      modelItem('deepseek-v4-flash', '高速版本 · 适合批量生成', '服务商', false, 'model-tag--live'),
      '</div>'
    ].join('') : '',
    '<div class="model-pop__foot">',
    '  <span class="link-btn">' + icon('refresh') + (live ? '刷新模型列表' : '从服务商拉取模型') + '</span>',
    '  <span class="grow"></span>',
    '  <span class="link-btn" style="color:#4A5568">手动输入模型 ID</span>',
    '</div>',
    '</div>'
  ].join('\n');
}

export const modelPicker = [
  head,
  '  <div class="doc__hero">',
  '    <span class="doc__eyebrow">' + icon('ai', 'icon--14') + 'AI 供应商 · 默认模型</span>',
  '    <h1 class="doc__title">默认模型选择</h1>',
  '    <p class="doc__sub">模型字段不再是纯文本输入：选中供应商时先给推荐列表，需要更多时由服务端向供应商拉取 /models，仍可手动输入自定义模型。列表只用于本次选择，不落库。</p>',
  '    <div class="doc__meta"><span>组件设计</span><span>2026-10-21</span><span>1440 宽基准</span></div>',
  '  </div>',
  '  <div class="doc__wrap">',
  '    <div class="model-board">',
  '      <div class="model-demo">',
  '        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:18px">',
  '          <div><div class="crumb" style="margin:0">AI 供应商 / DeepSeek</div><div style="font-size:13px;color:#A3AED0;margin-top:3px">api.deepseek.com/v1 · API Key 已配置</div></div>',
  '          <span class="api-type-chip">API 接入</span>',
  '        </div>',
  '        <div style="font-size:12.5px;font-weight:600;color:#4A5568;margin-bottom:7px">默认模型</div>',
  modelField('deepseek-chat', '推荐', true),
  pickerPop({ live: false }),
  '        <div class="model-note">推荐列表随 MockHub 版本维护；拉取结果来自服务商 <b>/models</b> 接口；两者都可以被手动输入覆盖。</div>',
  '        <div class="model-test model-test--ok">' + icon('checkCircle') + 'deepseek-chat 可用 · 1.1s</div>',
  '      </div>',
  '',
  '      <div class="model-notes">',
  '        <div class="model-note-card">',
  '          <div class="model-note-card__title">' + icon('list') + '候选列表从哪来</div>',
  '          <div class="model-note-card__body">',
  '            <b>① 推荐列表</b>：内置在 MockHub 中，选中供应商后立即可用，断网也能选。<br>',
  '            <b>② 服务商模型</b>：点「从服务商拉取」，服务端带上已保存的 Key 请求 <span style="font-family:monospace">GET {baseUrl}/models</span>，把返回的 ID 列出来。<br>',
  '            <b>③ 手动输入</b>：微调模型、新上线模型或服务商不提供 /models 时使用。',
  '          </div>',
  '        </div>',
  '        <div class="model-note-card">',
  '          <div class="model-note-card__title">' + icon('plug') + '交互顺序</div>',
  '          <div class="model-steps">',
  '            <div class="model-step"><span class="model-step__n">1</span><div>选供应商 → 自动填 Base URL，并默认选中第一个推荐模型。</div></div>',
  '            <div class="model-step"><span class="model-step__n">2</span><div>点模型字段打开面板：搜索、上下选择、回车确认。</div></div>',
  '            <div class="model-step"><span class="model-step__n">3</span><div>需要更多模型时点「从服务商拉取」；失败不影响推荐列表继续使用。</div></div>',
  '            <div class="model-step"><span class="model-step__n">4</span><div>选完自动收起；点「测试连接」验证当前模型，而不是只测 Base URL。</div></div>',
  '          </div>',
  '        </div>',
  '        <div class="model-note-card">',
  '          <div class="model-note-card__title">' + icon('server') + '数据流</div>',
  '          <div class="model-api"><span class="g">POST</span> /api/ai/models<br>{ <span class="v">"vendor"</span>: <span class="v">"deepseek"</span> }<br>→ { <span class="v">"models"</span>: [...], <span class="v">"fetchedAt"</span>: ... }</div>',
  '          <div class="model-note-card__body" style="margin-top:8px">只读取服务端已保存的配置；Key 不进入浏览器请求、响应或日志。未保存 Key 时拉取按钮禁用。</div>',
  '        </div>',
  '      </div>',
  '    </div>',
  '  </div>',
  '</div>',
  foot
].join('\n');

function stateCard(kicker, title, desc, body) {
  return [
    '<div class="state-card">',
    '  <div class="state-card__k">' + kicker + '</div>',
    '  <div class="state-card__t">' + title + '</div>',
    '  <div class="state-card__d">' + desc + '</div>',
    body,
    '</div>'
  ].join('\n');
}

export const modelStates = [
  head,
  '  <div class="doc__hero">',
  '    <span class="doc__eyebrow">' + icon('list', 'icon--14') + 'AI 供应商 · 模型选择状态</span>',
  '    <h1 class="doc__title">默认模型 · 状态与边界</h1>',
  '    <p class="doc__sub">同一个字段要覆盖四种情况：推荐列表直接选、服务商拉取成功、拉取失败、手动输入。失败不阻塞保存，但保存前建议测试。</p>',
  '    <div class="doc__meta"><span>4 个状态</span><span>组件设计</span><span>2026-10-21</span></div>',
  '  </div>',
  '  <div class="doc__wrap">',
  '    <div class="state-grid">',
  stateCard('STATE 01', '已选并验证', '默认态：选中推荐模型，字段收起；测试按钮验证当前模型。',
    modelField('deepseek-chat', '推荐', false) +
    '<div class="model-test model-test--ok">' + icon('checkCircle') + 'deepseek-chat 可用 · 1.1s</div>' +
    '<div class="model-note">未点开时只显示模型 ID 和来源，不占用表单高度。</div>' +
    '<div style="border-top:1px solid #F1F5F9;margin-top:12px;padding-top:10px;font-size:11px;color:#A3AED0;line-height:1.9">来源：推荐列表 · 2 个模型<br>选择后需要点保存才会写入全局设置<br>「测试连接」会验证当前模型，而不是只测 Base URL</div>'
  ),
  stateCard('STATE 02', '拉取成功', '服务端返回 8 个模型；搜索、刷新、选择，不落库。',
    modelField('deepseek-chat', '推荐', true) +
    '<div class="model-pop">' + searchRow('搜索 8 个模型') +
    '<div class="model-group"><div class="model-group__head">来自 api.deepseek.com<span class="grow"></span><span>共 8 个</span></div>' +
    modelItem('deepseek-chat', '通用对话 · 128K 上下文', '服务商', true, 'model-tag--live') +
    modelItem('deepseek-reasoner', '深度推理 · 64K 上下文', '服务商', false, 'model-tag--live') +
    modelItem('deepseek-v4-flash', '高速版本 · 适合批量生成', '服务商', false, 'model-tag--live') +
    '</div><div class="model-pop__foot"><span class="link-btn">' + icon('refresh') + '刷新模型列表</span><span class="grow"></span><span class="link-btn" style="color:#4A5568">手动输入</span></div></div>' +
    '<div class="model-note">拉取结果仅用于本次选择；刷新会重新请求，不写入数据库。</div>'
  ),
  stateCard('STATE 03', '拉取失败', 'Key 无效、服务商不支持 /models 或网络超时；推荐列表仍可用。',
    modelField('deepseek-chat', '推荐', false) +
    '<div class="model-error">' + icon('warning') + '<div><b>无法获取模型列表：401 Invalid API Key</b><br>可以继续使用推荐模型，或检查 Key 后重试。</div></div>' +
    '<div style="display:flex;align-items:center;gap:10px;margin-top:10px"><button class="btn btn--plain btn--sm">' + icon('refresh') + '重试</button><span class="link-btn">继续使用推荐模型</span></div>' +
    '<div class="model-note">错误只影响本次拉取，不禁用保存；测试连接会给出更准确的模型级错误。</div>'
  ),
  stateCard('STATE 04', '手动输入', '微调模型或服务商不提供列表时，直接输入 ID；保存前建议测试。',
    '<div class="model-field"><div class="input model-input"><span class="model-id">my-finetune-v2</span><span class="model-chip" style="background:#FFF7ED;color:#B45309">自定义</span></div><button class="btn btn--plain btn--sm">' + icon('chevDown') + '</button></div>' +
    '<div class="model-error">' + icon('info') + '<div><b>不在推荐或服务商列表中</b><br>仍可保存，但建议先测试；模型 ID 区分大小写。</div></div>' +
    '<div style="display:flex;align-items:center;gap:10px;margin-top:10px"><button class="btn btn--primary btn--sm">使用此 ID</button><span class="link-btn">取消</span></div>' +
    '<div class="model-test model-test--bad">' + icon('warning') + '测试失败：404 model not found</div>'
  ),
  '    </div>',
  '  </div>',
  '</div>',
  foot
].join('\n');