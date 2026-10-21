import { page, icon, switchOn, radio, aiBadge } from './lib.mjs';

function navItem(label, iconName, active, badge) {
  return [
    '<div class="s2-item' + (active ? ' s2-item--on' : '') + '">',
    icon(iconName),
    '<span>' + label + '</span>',
    badge ? '<span class="s2-item__badge">' + badge + '</span>' : '',
    '</div>'
  ].join('');
}

function settingsNav(active) {
  return [
    '<aside class="settings2__nav">',
    '  <div class="nav-group">',
    '    <div class="nav-group__title">AI 能力</div>',
    navItem('AI 供应商', 'plug', active === 'provider', '新'),
    navItem('助手与能力', 'ai', active === 'assistant'),
    navItem('MCP Server', 'terminal', active === 'mcp'),
    '  </div>',
    '  <div class="nav-group">',
    '    <div class="nav-group__title">系统</div>',
    navItem('基础设置', 'server', active === 'basic'),
    navItem('日志与保留', 'doc', active === 'logs'),
    navItem('文件存储', 'files', active === 'files'),
    '  </div>',
    '  <div class="settings2__hint">仅超级管理员可修改全局设置，保存后对全部团队生效。</div>',
    '</aside>'
  ].join('\n');
}

function settingsPanel(opt) {
  return [
    '<section class="settings2__panel">',
    '  <div class="crumb">' + (opt.crumb || '全局设置') + '</div>',
    '  <div class="panel-title">' + opt.title + (opt.chip || '') + '</div>',
    opt.desc ? '  <div class="panel-desc">' + opt.desc + '</div>' : '',
    '  <div class="panel-body">' + opt.body + '</div>',
    opt.foot ? '  <div class="panel-foot">' + opt.foot + '</div>' : '',
    '</section>'
  ].join('\n');
}

function field(label, control, hint, cls) {
  return [
    '<div class="field2 ' + (cls || '') + '">',
    '  <div class="field2__label">' + label + '</div>',
    '  ' + control,
    hint ? '  <div class="field2__hint">' + hint + '</div>' : '',
    '</div>'
  ].join('');
}

function input(value, cls) {
  return '<div class="input ' + (cls || '') + '">' + value + '</div>';
}

const PROVIDERS = [
  { name: 'DeepSeek', letter: 'D', bg: '#EEF0FF', fg: '#4F46E5', host: 'api.deepseek.com', on: true },
  { name: 'Kimi', letter: 'K', bg: '#F3F4F6', fg: '#111827', host: 'api.moonshot.cn' },
  { name: 'MiniMax', letter: 'M', bg: '#FFF7ED', fg: '#C2410C', host: 'api.minimax.cn' },
  { name: '智谱 GLM', letter: 'G', bg: '#EFF6FF', fg: '#2563EB', host: 'open.bigmodel.cn' },
  { name: '通义千问', letter: 'Q', bg: '#F5F3FF', fg: '#7C3AED', host: 'dashscope.aliyuncs.com' },
  { name: 'OpenAI', letter: 'O', bg: '#ECFDF5', fg: '#059669', host: 'api.openai.com' },
  { name: 'Ollama', letter: 'L', bg: '#F1F5F9', fg: '#475569', host: 'localhost:11434', local: true },
  { name: '自定义', letter: '+', dashed: true, host: 'OpenAI 兼容接口' }
];

function providerCard(p) {
  const cls = 'provider' + (p.on ? ' provider--on' : '') + (p.dashed ? ' provider--dashed' : '');
  const badge = p.local ? '<span class="s2-item__badge" style="font-size:9px;color:#64748B;background:#F1F5F9;border-color:#E2E8F0">本地</span>' : '';
  return [
    '<div class="' + cls + '">',
    '  <span class="provider__logo" style="background:' + (p.bg || '#F7F8FA') + ';color:' + (p.fg || '#A3AED0') + '">' + p.letter + '</span>',
    '  <span class="provider__body">',
    '    <span class="provider__name">' + p.name + badge + '</span>',
    '    <span class="provider__host">' + p.host + '</span>',
    '  </span>',
    p.on ? '<span class="provider__check">' + icon('check') + '</span>' : '',
    '</div>'
  ].join('');
}

const providerCards = PROVIDERS.map(providerCard).join('\n');

export const provider = page({
  cssFile: 'assets/settings-v2.css',
  title: '全局设置',
  active: 'settings',
  body: [
    '<div class="settings2">',
    settingsNav('provider'),
    settingsPanel({
      crumb: '全局设置 / AI 供应商',
      title: 'AI 供应商',
      chip: '<span class="api-type-chip">API 接入</span>',
      desc: '选择模型服务商，用 API Key 接入。请求全部由服务端发出，浏览器不会直接访问模型服务。',
      body: [
        '<div class="conn-strip">',
        '  <span class="provider__logo" style="background:#EEF0FF;color:#4F46E5">D</span>',
        '  <span>当前使用 <b>DeepSeek</b> · deepseek-chat</span>',
        '  <span class="conn-ok">连接正常 · 1.1s</span>',
        '</div>',

        '<div class="block-title">选择供应商</div>',
        '<div class="block-desc">先选服务商，再填 Key。DeepSeek、Kimi、MiniMax、智谱 GLM 等都走 OpenAI 兼容协议。</div>',
        '<div class="provider-grid">',
        providerCards,
        '</div>',
        '<div class="provider-hint">选择供应商会自动填入默认接口地址与模型，仍可手动修改；自定义用于任何 OpenAI 兼容服务。</div>',

        '<div class="section-divider"></div>',

        '<div class="block-title">连接配置</div>',
        '<div class="block-desc">只支持 API Key 方式，不接入 OAuth 或账号授权。</div>',
        '<div class="form-grid2">',
        field('API 地址', input('https://api.deepseek.com/v1'), '已按供应商自动填写；服务端会拼接 /chat/completions', 'full'),
        field('默认模型', '<div class="model-field"><div class="input model-input"><span class="model-id">deepseek-chat</span><span class="model-chip">推荐</span></div><button class="btn btn--plain btn--sm">' + icon('chevDown') + '</button></div>', '可从推荐列表选择、从服务商拉取，或手动输入'),
        field('接口协议', input('OpenAI Chat Completions', 'input--readonly'), '所有供应商统一使用这一协议'),
        field('API Key', [
          '<div class="key-row2">',
          '  <div class="input key-mask2">sk-••••••••••••5416</div>',
          '  <span class="chip" style="height:22px;font-size:11px;color:#10B981;background:#ECFDF5">已配置</span>',
          '  <button class="btn btn--plain btn--sm">' + icon('edit') + '替换</button>',
          '</div>'
        ].join(''), '仅写入不回显；保存后只返回掩码，不会进入日志或 localStorage', 'full'),
        '</div>',

        '<div class="test-line">',
        '  <button class="btn btn--plain">' + icon('plug') + '测试连接</button>',
        '  <span class="test-ok2">' + icon('checkCircle') + '连接正常 · 1.1s</span>',
        '  <span class="hint">最近检测：刚刚</span>',
        '</div>',

        '<div class="alert alert--info" style="margin-top:16px">',
        icon('shield'),
        '<div><div class="alert__title">Key 只存在服务端</div>API Key 以 AES-GCM 密文写入数据库；页面只显示掩码。备份数据库时请同时备份 data/.ai-key。</div>',
        '</div>'
      ].join('\n'),
      foot: [
        '<button class="btn btn--primary btn--lg">保存供应商配置</button>',
        '<span class="link-btn">没有 Key？先用内置演示模型 ' + icon('arrowRight') + '</span>'
      ].join('')
    }),
    '</div>'
  ].join('\n')
});

export const assistant = page({
  cssFile: 'assets/settings-v2.css',
  title: '全局设置',
  active: 'settings',
  body: [
    '<div class="settings2">',
    settingsNav('assistant'),
    settingsPanel({
      crumb: '全局设置 / 助手与能力',
      title: '助手与能力',
      chip: aiBadge('Beta'),
      desc: '配置助手是否启用、可用的生成能力与使用范围。供应商与 Key 在「AI 供应商」里单独维护。',
      body: [
        '<div class="provider-summary">',
        '  <span class="provider-summary__logo">D</span>',
        '  <div><div class="provider-summary__name">DeepSeek</div><div class="provider-summary__meta">deepseek-chat · API Key 已配置</div></div>',
        '  <span class="conn-ok" style="margin-left:12px">已连接</span>',
        '  <span class="grow"></span>',
        '  <span class="link-btn">更换供应商 ' + icon('arrowRight') + '</span>',
        '</div>',

        '<div class="switch-row" style="justify-content:space-between;padding:2px 0 16px">',
        '  <div><div class="switch-row__label" style="font-size:14px;font-weight:500;color:#1B2559">启用 AI 助手</div><div class="switch-row__desc">关闭后隐藏全部 AI 入口，不影响已有接口与规则</div></div>',
        '  <div style="display:flex;align-items:center;gap:10px"><span style="font-size:12px;color:#10B981">运行中</span>' + switchOn(true) + '</div>',
        '</div>',

        '<div class="block-title">能力开关</div>',
        '<div class="block-desc">关闭某项能力后，对应入口会从编辑器和日志页消失，其余能力不受影响。</div>',
        '<div class="cap2">',
        '  <div class="cap2__row"><div><div class="cap2__title">生成响应体</div><div class="cap2__desc">在返回体编辑器里根据接口信息生成 JSON / XML</div></div>' + switchOn(true) + '</div>',
        '  <div class="cap2__row"><div><div class="cap2__title">生成条件规则</div><div class="cap2__desc">把自然语言转成 Body / Query / Header 条件，逐条确认</div></div>' + switchOn(true) + '</div>',
        '  <div class="cap2__row"><div><div class="cap2__title">解释匹配结果</div><div class="cap2__desc">在请求日志里重放条件链路，并用模型组织说明</div></div>' + switchOn(true) + '</div>',
        '  <div class="cap2__row"><div><div class="cap2__title">创建接口草稿</div><div class="cap2__desc">对话助手可创建待确认草稿，确认后才落库</div></div>' + switchOn(true) + '</div>',
        '</div>',

        '<div class="section-divider"></div>',

        '<div class="block-title">使用范围</div>',
        '<div class="block-desc">AI 始终继承当前登录用户的权限；这里控制哪些成员能看到入口。</div>',
        '<div class="radio-row">',
        '  <div class="radio-row__item">' + radio(true) + '<div><div>所有成员（继承各自权限）</div><div class="radio-row__desc">成员只能访问自己有权访问的团队与接口</div></div></div>',
        '  <div class="radio-row__item">' + radio(false) + '<div><div>仅超级管理员</div><div class="radio-row__desc">普通成员完全看不到 AI 入口</div></div></div>',
        '</div>',

        '<div class="alert alert--info" style="margin-top:20px">',
        icon('shield'),
        '<div><div class="alert__title">数据安全</div>发送给模型前会移除 Authorization、Cookie、token、password 等敏感字段；所有 AI 写操作都会记录到操作日志。</div>',
        '</div>'
      ].join('\n'),
      foot: '<button class="btn btn--primary btn--lg">保存助手配置</button>'
    }),
    '</div>'
  ].join('\n')
});

function toolRow(name, desc, on) {
  return [
    '<div class="tool2">',
    '  <code>' + name + '</code>',
    '  <span class="desc">' + desc + '</span>',
    '  ' + switchOn(on),
    '</div>'
  ].join('');
}

export const mcp = page({
  cssFile: 'assets/settings-v2.css',
  title: '全局设置',
  active: 'settings',
  body: [
    '<div class="settings2">',
    settingsNav('mcp'),
    settingsPanel({
      crumb: '全局设置 / MCP Server',
      title: 'MCP Server',
      chip: '<span class="api-type-chip">Streamable HTTP</span>',
      desc: '把检索、统计与生成能力开放给 Claude、Cursor、Codex 等外部 AI 工具。',
      body: [
        '<div class="conn-strip">',
        '  <span class="provider__logo" style="background:#ECFDF5;color:#059669">' + icon('terminal') + '</span>',
        '  <span>MCP Server <b>已启用</b> · 3 个有效 Token</span>',
        '  <span class="conn-ok">最近调用：2 分钟前</span>',
        '</div>',

        '<div class="switch-row" style="justify-content:space-between;padding:2px 0 16px">',
        '  <div><div class="switch-row__label" style="font-size:14px;font-weight:500;color:#1B2559">启用 MCP Server</div><div class="switch-row__desc">关闭后 /mcp 返回 403，已有 Token 保留</div></div>',
        '  <div style="display:flex;align-items:center;gap:10px"><span style="font-size:12px;color:#10B981">已启用</span>' + switchOn(true) + '</div>',
        '</div>',

        '<div class="block-title">服务地址</div>',
        '<div class="block-desc">外部客户端使用 Streamable HTTP（POST /mcp）连接，返回 JSON 响应。</div>',
        '<div class="mcp-endpoint2"><code>https://mockhub.example.com/mcp</code><button class="btn btn--plain btn--sm">' + icon('copy') + '复制</button></div>',

        '<div class="section-divider"></div>',

        '<div class="block-title">写操作策略</div>',
        '<div class="block-desc">控制外部工具能否直接创建接口。</div>',
        '<div class="radio-row" style="margin-bottom:18px">',
        '  <div class="radio-row__item">' + radio(true) + '<div><div>草稿 + Web 确认（推荐）</div><div class="radio-row__desc">返回确认链接，用户在 MockHub 页面确认后才落库</div></div></div>',
        '  <div class="radio-row__item">' + radio(false) + '<div><div>允许直接写入</div><div class="radio-row__desc">仅对可信客户端开启；仍会写操作日志</div></div></div>',
        '</div>',

        '<div class="block-title">可用工具</div>',
        '<div class="block-desc">关闭后从 tools/list 移除；直接调用返回 -32602。</div>',
        '<div class="tool2-grid">',
        toolRow('search_apis', '搜索接口', true),
        toolRow('get_api_detail', '读取接口详情', true),
        toolRow('get_api_stats', '查询调用统计', true),
        toolRow('create_api_draft', '创建接口草稿', true),
        toolRow('apply_api_draft', '应用草稿', false),
        toolRow('generate_response_body', '生成响应体', true),
        toolRow('generate_conditions', '生成条件规则', true),
        toolRow('explain_match_result', '解释命中链路', true),
        '</div>',

        '<div class="section-divider"></div>',

        '<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">',
        '  <div><div class="block-title" style="margin:0">访问 Token</div><div class="block-desc" style="margin:2px 0 0">Token 只在创建时显示一次，可随时撤销。</div></div>',
        '  <button class="btn btn--plain btn--sm">' + icon('plus') + '新建 Token</button>',
        '</div>',
        '<table class="token2">',
        '  <thead><tr><th>名称</th><th>前缀</th><th>最后使用</th><th>状态</th><th style="width:56px">操作</th></tr></thead>',
        '  <tbody>',
        '    <tr><td>Cursor · 张一鸣</td><td class="mono">mhk_7f2a...c91d</td><td>2 分钟前</td><td style="color:#10B981">生效中</td><td style="color:#EF4444">撤销</td></tr>',
        '    <tr><td>Claude Desktop</td><td class="mono">mhk_b13e...88a0</td><td>昨天 18:24</td><td style="color:#10B981">生效中</td><td style="color:#EF4444">撤销</td></tr>',
        '  </tbody>',
        '</table>'
      ].join('\n'),
      foot: '<button class="btn btn--primary btn--lg">保存 MCP 配置</button>'
    }),
    '</div>'
  ].join('\n')
});

export const basic = page({
  cssFile: 'assets/settings-v2.css',
  title: '全局设置',
  active: 'settings',
  body: [
    '<div class="settings2">',
    settingsNav('basic'),
    settingsPanel({
      crumb: '全局设置 / 基础设置',
      title: '基础设置',
      desc: '服务地址与 Mock 跨域。日志保留、文件存储已经拆到左侧独立分类。',
      body: [
        '<div class="block-title">服务地址</div>',
        '<div class="block-desc">用于生成 Mock URL、MCP 确认链接与导出文件中的地址。</div>',
        field('外部访问地址', input('https://mockhub.example.com'), '留空时使用浏览器当前地址'),

        '<div class="section-divider"></div>',

        '<div class="block-title">Mock 跨域</div>',
        '<div class="block-desc">只影响 /mock/** 响应，管理接口始终按登录态校验。</div>',
        '<div class="switch-row" style="justify-content:space-between;padding:2px 0 4px">',
        '  <div><div class="switch-row__label">允许浏览器跨域调用 Mock 接口</div><div class="switch-row__desc">返回 Access-Control-Allow-Origin: *，并处理 OPTIONS 预检</div></div>',
        switchOn(true),
        '</div>',

        '<div class="alert alert--warn" style="margin-top:20px">',
        icon('warning'),
        '<div><div class="alert__title">修改服务地址后</div>已生成的 MCP 确认链接不会自动更新；建议在对外发布前配置固定域名。</div>',
        '</div>'
      ].join('\n'),
      foot: '<button class="btn btn--primary btn--lg">保存基础设置</button>'
    }),
    '</div>'
  ].join('\n')
});

const shots = [
  ['09', 'screens/09-settings-provider.png', '09-settings-provider.html', 'AI 供应商', '供应商预设 + 自动填充地址/模型，Key 只显示掩码'],
  ['10', 'screens/10-settings-assistant.png', '10-settings-assistant.html', '助手与能力', '启用、能力开关、使用范围从供应商配置里拆开'],
  ['11', 'screens/11-settings-mcp.png', '11-settings-mcp.html', 'MCP Server', '独立分类承载接入、写策略、工具与 Token'],
  ['12', 'screens/12-settings-basic.png', '12-settings-basic.html', '系统基础设置', '服务地址与跨域归入系统分类']
];

const gallery = shots.map(function (s) {
  return [
    '<div class="shot">',
    '  <a href="' + s[1] + '"><img class="shot__img" src="' + s[1] + '" alt="' + s[3] + '"></a>',
    '  <div class="shot__body">',
    '    <div class="shot__kicker">' + s[0] + ' / SCREEN</div>',
    '    <div class="shot__title">' + s[3] + '</div>',
    '    <div class="shot__desc">' + s[4] + '</div>',
    '    <div style="margin-top:8px"><a class="link" style="font-size:11.5px" href="' + s[2] + '">打开 HTML</a></div>',
    '  </div>',
    '</div>'
  ].join('\n');
}).join('\n');

export const overview = [
  '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>全局设置重构 · 设计稿</title>',
  '<link rel="stylesheet" href="assets/mock.css">',
  '<link rel="stylesheet" href="assets/settings-v2.css">',
  '<style>',
  '.doc__hero{background:radial-gradient(120% 120% at 10% 0%, #EEF0FF 0%, #F6F4FF 42%, #FBFBFE 100%)}',
  '.gallery{grid-template-columns:repeat(2,1fr);gap:20px}',
  '.shot__img{aspect-ratio:16/10;object-fit:cover;object-position:top left;width:100%}',
  '.doc__note{background:#fff;border-radius:12px;padding:16px 18px;font-size:12.5px;color:#4A5568;line-height:1.8}',
  '.doc__note b{color:#1B2559}',
  '.ia-grid{margin-bottom:8px}',
  '</style></head>',
  '<body class="scrollable" style="overflow:auto">',
  '<div class="doc">',
  '  <div class="doc__hero">',
  '    <span class="doc__eyebrow">' + icon('gear', 'icon--14') + '全局设置 · 信息架构改版</span>',
  '    <h1 class="doc__title">设置分类 + AI 供应商</h1>',
  '    <p class="doc__sub">把原来一页到底的全局设置拆成「AI 能力 / 系统」两组，仍然只占侧边栏一个「全局设置」入口。AI 供应商从助手配置里独立出来，先支持 API Key 接入，并为 DeepSeek、Kimi、MiniMax、智谱 GLM 等预填默认地址与模型。</p>',
  '    <div class="doc__meta"><span>版本 v1.1</span><span>2026-10-21</span><span>4 个页面</span><span>1440 宽基准</span></div>',
  '  </div>',
  '  <div class="doc__wrap">',
  '    <div class="doc__section-title">设计原则</div>',
  '    <div class="doc__section-desc">分类是为了减少单页长度，不是增加导航层级。</div>',
  '    <div class="principle-grid">',
  '      <div class="principle"><div class="principle__num">01</div><div class="principle__title">一个入口，组内分类</div><div class="principle__desc">侧边栏仍然只有「全局设置」；进入后用左侧子导航切换 AI 能力、系统、日志与文件存储。</div></div>',
  '      <div class="principle"><div class="principle__num">02</div><div class="principle__title">供应商与能力分离</div><div class="principle__desc">供应商负责地址、模型与 Key；助手页只负责启用、能力开关与使用范围。换模型不用再翻一整页。</div></div>',
  '      <div class="principle"><div class="principle__num">03</div><div class="principle__title">预设但可改</div><div class="principle__desc">选中供应商自动填默认 Base URL 与模型，字段仍可编辑；自定义入口承接任意 OpenAI 兼容服务。</div></div>',
  '    </div>',
  '',
  '    <div style="height:34px"></div>',
  '    <div class="doc__section-title">分类结构</div>',
  '    <div class="doc__section-desc">每个分类只承载一类设置；保存按钮留在当前分类底部。</div>',
  '    <div class="ia-grid">',
  '      <div class="ia-col"><div class="ia-col__title">AI 能力</div>',
  '        <div class="ia-item"><b>AI 供应商</b><span class="tag">新</span></div>',
  '        <div class="ia-item">助手与能力</div>',
  '        <div class="ia-item">MCP Server</div>',
  '      </div>',
  '      <div class="ia-col"><div class="ia-col__title">系统</div>',
  '        <div class="ia-item">基础设置</div>',
  '        <div class="ia-item">日志与保留</div>',
  '        <div class="ia-item">文件存储</div>',
  '      </div>',
  '      <div class="ia-col"><div class="ia-col__title">保持不变</div>',
  '        <div class="ia-item">侧边栏一个入口</div>',
  '        <div class="ia-item">仅超管可见</div>',
  '        <div class="ia-item">保存后全局生效</div>',
  '      </div>',
  '    </div>',
  '',
  '    <div style="height:34px"></div>',
  '    <div class="doc__section-title">AI 供应商默认值</div>',
  '    <div class="doc__section-desc">只做 API Key 接入；地址按 2026-10 官方 OpenAI 兼容端点预填，模型可在页面修改。</div>',
  '    <table class="provider-table">',
  '      <thead><tr><th>供应商</th><th>默认 API 地址</th><th>默认模型</th><th>说明</th></tr></thead>',
  '      <tbody>',
  '        <tr><td><b>DeepSeek</b></td><td><code>https://api.deepseek.com/v1</code></td><td><code>deepseek-chat</code></td><td>默认选中</td></tr>',
  '        <tr><td><b>Kimi</b></td><td><code>https://api.moonshot.cn/v1</code></td><td><code>kimi-k3</code></td><td>月之暗面</td></tr>',
  '        <tr><td><b>MiniMax</b></td><td><code>https://api.minimax.cn/v1</code></td><td><code>MiniMax-M3</code></td><td>国内站；海外站用 api.minimax.io</td></tr>',
  '        <tr><td><b>智谱 GLM</b></td><td><code>https://open.bigmodel.cn/api/paas/v4</code></td><td><code>glm-4.6</code></td><td>OpenAI 兼容</td></tr>',
  '        <tr><td><b>通义千问</b></td><td><code>https://dashscope.aliyuncs.com/compatible-mode/v1</code></td><td><code>qwen-plus</code></td><td>DashScope 兼容模式</td></tr>',
  '        <tr><td><b>OpenAI</b></td><td><code>https://api.openai.com/v1</code></td><td><code>gpt-4o-mini</code></td><td>海外网络</td></tr>',
  '        <tr><td><b>Ollama</b></td><td><code>http://localhost:11434/v1</code></td><td><code>llama3.1</code></td><td>本地模型</td></tr>',
  '        <tr><td><b>自定义</b></td><td>手动填写</td><td>手动填写</td><td>任意 OpenAI 兼容服务</td></tr>',
  '      </tbody>',
  '    </table>',
  '',
  '    <div style="height:34px"></div>',
  '    <div class="doc__section-title">页面</div>',
  '    <div class="doc__section-desc">点击缩略图查看原图；每张卡片下方可直接打开对应 HTML。</div>',
  '    <div class="gallery">',
  gallery,
  '    </div>',
  '',
  '    <div style="height:34px"></div>',
  '    <div class="doc__section-title">交互说明</div>',
  '    <div class="doc__section-desc">以下行为需要在实现时一起落地。</div>',
  '    <div class="principle-grid">',
  '      <div class="doc__note"><b>切换供应商</b><br>点击卡片后自动填入 Base URL 与默认模型，并清空已测试状态；如果当前已填自定义值，弹二次确认再覆盖。</div>',
  '      <div class="doc__note"><b>Key 与测试</b><br>Key 只写不回显；未保存的新 Key 不能测试，保存后测试按钮才可用；测试结果带耗时与时间。</div>',
  '      <div class="doc__note"><b>未配置降级</b><br>供应商未填 Key 时，助手页顶部显示「未配置」，编辑器 AI 按钮隐藏；对话入口仍在，但打开后给出配置引导。</div>',
  '    </div>',
  '  </div>',
  '</div>',
  '</body></html>'
].join('\n');