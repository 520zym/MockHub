import { page, icon, chip, switchOn, aiBadge, teamTag } from './lib.mjs';

/* ---------------- 05 全局设置 · AI 助手 ---------------- */
function row(label, control, hint) {
  return [
    '<div class="form-item">',
    '  <div class="form-item__label">' + label + '</div>',
    control,
    hint ? '<div class="form-item__hint">' + hint + '</div>' : '',
    '</div>'
  ].join('\n');
}

function capItem(title, desc, on, badge) {
  return [
    '<div class="cap-item">',
    '  <div style="flex:1"><div class="cap-item__title">' + title + (badge ? ' ' + badge : '') + '</div><div class="cap-item__desc">' + desc + '</div></div>',
    switchOn(on),
    '</div>'
  ].join('\n');
}

export const settingsAI = page({
  title: '全局设置',
  active: 'settings',
  body: [
    '<div class="settings">',
    '  <div class="settings-card">',
    '    <div class="settings-section">',
    '      <h3 class="section-title">AI 助手 ' + aiBadge('Beta') + '</h3>',
    '      <p class="section-desc">配置 MockHub 内置 AI 助手使用的模型服务与可用能力，仅超级管理员可见。</p>',
    '',
    '      <div class="switch-row" style="justify-content:space-between;padding:4px 0 16px">',
    '        <div><div class="switch-row__label" style="font-size:14px;font-weight:500;color:#1B2559">启用 AI 助手</div><div class="switch-row__desc">关闭后隐藏全部 AI 入口，不影响已有接口与规则</div></div>',
    '        <div style="display:flex;align-items:center;gap:10px"><span style="font-size:12px;color:#10B981">运行中</span>' + switchOn(true) + '</div>',
    '      </div>',
    '',
    '      <div class="two-col">',
    row('Provider', '<div class="input select"><span class="select__value">OpenAI 兼容</span>' + icon('chevDown', 'chev') + '</div>', '支持 OpenAI、DeepSeek、Ollama 等兼容接口'),
    row('Model', '<div class="input">deepseek-chat</div>', ''),
    '      </div>',
    '',
    row('Base URL', '<div class="input">https://api.deepseek.com/v1</div>', '请求将从服务端发出，浏览器不直接访问模型服务'),
    row('API Key', [
      '<div class="key-field">',
      '  <div class="input" style="flex:1"><span class="key-masked">sk-••••••••••••••••3f9a</span><span class="chip" style="height:22px;font-size:11px;color:#10B981;background:#ECFDF5">已配置</span></div>',
      '  <button class="btn">' + icon('edit') + '替换</button>',
      '  <button class="btn btn--danger-text">清除</button>',
      '</div>'
    ].join(''), '仅写入不回显，保存后前端只返回掩码；不会出现在接口响应、日志或 localStorage 中'),
    '<div class="test-row" style="margin:-4px 0 20px"><button class="btn btn--plain">' + icon('plug') + '测试连接</button><span class="test-ok">' + icon('checkCircle') + '连接正常 · 820 ms</span><span class="hint">最近检测：今天 17:32</span></div>',
    '    </div>',
    '',
    '    <div class="divider" style="margin:24px 0"></div>',
    '',
    '    <div class="settings-section">',
    '      <h3 class="section-title">能力开关</h3>',
    '      <p class="section-desc">按场景逐步开放，关闭的能力不会出现在任何入口里。</p>',
    '      <div class="cap-list">',
    capItem('生成响应体', '在返回体编辑器里根据接口信息生成 JSON / XML 示例', true),
    capItem('生成条件规则', '把自然语言描述转成现有 conditions 结构，可逐条确认', true),
    capItem('解释匹配结果', '在请求日志里解释某次请求为什么命中 / 未命中', true),
    capItem('创建接口草稿', '在对话中创建接口草稿，需回到确认卡片才会写入', true, '<span class="chip" style="height:18px;font-size:10px;padding:0 6px">Phase 2</span>'),
    '      </div>',
    '    </div>',
    '',
    '    <div class="divider" style="margin:24px 0"></div>',
    '',
    '    <div class="settings-section">',
    '      <h3 class="section-title">使用范围</h3>',
    '      <p class="section-desc">AI 始终继承当前登录用户的权限，团队数据保持隔离。</p>',
    '      <div class="radio-line">' + '<span class="radio radio--on"></span><div><div class="cap-item__title">所有成员</div><div class="cap-item__desc">每个成员只能看到和操作自己有权限的团队与接口</div></div></div>',
    '      <div class="radio-line" style="margin-top:6px">' + '<span class="radio"></span><div><div class="cap-item__title">仅超级管理员</div><div class="cap-item__desc">隐藏普通成员的全部 AI 入口</div></div></div>',
    '    </div>',
    '',
    '    <div class="alert alert--info" style="margin-top:22px">' + icon('shield') + '<div><div class="alert__title">数据安全</div>发送给模型前会移除 Authorization、Cookie、token、password 等敏感字段；API Key 仅保存在服务端，不写入日志。所有 AI 写操作都会记录到操作日志。</div></div>',
    '',
    '    <div class="settings-footer"><button class="btn btn--primary btn--lg">保存设置</button></div>',
    '  </div>',
    '</div>'
  ].join('\n')
});

/* ---------------- 06 全局设置 · MCP Server ---------------- */
function tool(name, desc, rw, on) {
  return [
    '<div class="tool-item">',
    '  <code>mockhub.' + name + '</code>',
    '  <span class="desc">' + desc + '</span>',
    '  <span class="chip" style="height:18px;font-size:10px;' + (rw === '写' ? 'color:#B45309;background:#FFFBEB' : 'color:#4A5568') + '">' + rw + '</span>',
    switchOn(on),
    '</div>'
  ].join('\n');
}

export const mcp = page({
  title: '全局设置',
  active: 'settings',
  body: [
    '<div class="settings" style="max-width:780px">',
    '  <div class="settings-card">',
    '    <div class="settings-section">',
    '      <h3 class="section-title">MCP Server</h3>',
    '      <p class="section-desc">把 MockHub 的检索、统计与生成能力开放给 Claude、Cursor、Codex 等外部 AI 工具。</p>',
    '',
    '      <div class="switch-row" style="justify-content:space-between;padding:4px 0 16px">',
    '        <div><div class="switch-row__label" style="font-size:14px;font-weight:500;color:#1B2559">启用 MCP Server</div><div class="switch-row__desc">外部客户端通过 Bearer Token 调用，权限与 Token 所属用户一致</div></div>',
    '        <div style="display:flex;align-items:center;gap:10px"><span style="font-size:12px;color:#10B981">已启用</span>' + switchOn(true) + '</div>',
    '      </div>',
    '',
    '      <div class="form-item"><div class="form-item__label">服务地址</div>',
    '        <div class="mcp-endpoint"><span>https://mockhub.example.com/mcp</span><span class="tag">SSE</span><button class="btn btn--sm" style="margin-left:auto">' + icon('copy') + '复制</button></div>',
    '        <div class="form-item__hint">本地部署时使用 <code style="font-family:monospace">http://&lt;host&gt;:8080/mcp</code>；连接示例见文档。</div>',
    '      </div>',
    '    </div>',
    '',
    '    <div class="divider" style="margin:24px 0"></div>',
    '',
    '    <div class="settings-section">',
    '      <h3 class="section-title">可用工具</h3>',
    '      <p class="section-desc">工具内部复用现有业务服务，不会绕过权限与团队隔离。</p>',
    '      <div class="tool-list">',
    tool('search_apis', '按关键字搜索接口', '读', true),
    tool('get_api_detail', '读取接口与返回体配置', '读', true),
    tool('get_api_stats', '查询调用次数、最近调用与僵尸 Mock', '读', true),
    tool('create_api_draft', '根据描述生成接口草稿', '写', true),
    tool('apply_api_draft', '应用草稿（默认需 Web 确认）', '写', false),
    tool('generate_response_body', '生成 JSON / XML 响应体', '写', true),
    tool('generate_conditions', '把自然语言转成条件规则', '写', true),
    tool('explain_match_result', '解释一次请求的命中链路', '读', true),
    '      </div>',
    '    </div>',
    '',
    '    <div class="divider" style="margin:24px 0"></div>',
    '',
    '    <div class="settings-section">',
    '      <h3 class="section-title">写操作策略</h3>',
    '      <p class="section-desc">外部 AI 工具默认只能创建草稿，最终写入必须由用户在 MockHub 页面确认。</p>',
    '      <div class="apply-grid">',
    '        <div class="apply-opt apply-opt--on"><span class="radio radio--on"></span><div><div class="apply-opt__t">草稿 + Web 确认（推荐）</div><div class="apply-opt__d">返回确认链接，用户在 MockHub 完成最终确认</div></div></div>',
    '        <div class="apply-opt"><span class="radio"></span><div><div class="apply-opt__t">允许直接写入</div><div class="apply-opt__d">风险较高，仅建议在隔离环境开启</div></div></div>',
    '      </div>',
    '    </div>',
    '',
    '    <div class="divider" style="margin:24px 0"></div>',
    '',
    '    <div class="settings-section">',
    '      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:6px">',
    '        <h3 class="section-title" style="margin:0">访问 Token</h3>',
    '        <button class="btn btn--primary btn--sm">' + icon('plus') + '新建 Token</button>',
    '      </div>',
    '      <p class="section-desc">Token 只在创建时显示一次，可随时撤销。</p>',
    '      <table class="token-table">',
    '        <thead><tr><th>名称</th><th>所有者</th><th>创建时间</th><th>最后使用</th><th>状态</th><th></th></tr></thead>',
    '        <tbody>',
    '          <tr><td>Cursor · 本地</td><td>张一鸣</td><td>2026-06-18 10:24</td><td>3 分钟前</td><td><span style="color:#10B981">生效中</span></td><td style="text-align:right"><span class="link" style="font-size:12px">撤销</span></td></tr>',
    '          <tr><td>Claude Desktop</td><td>张一鸣</td><td>2026-06-03 15:08</td><td>2 天前</td><td><span style="color:#10B981">生效中</span></td><td style="text-align:right"><span class="link" style="font-size:12px">撤销</span></td></tr>',
    '          <tr><td>CI 冒烟测试</td><td>李闻</td><td>2026-05-21 09:41</td><td>31 天前</td><td><span style="color:#A3AED0">已过期</span></td><td style="text-align:right"><span class="link" style="font-size:12px">删除</span></td></tr>',
    '        </tbody>',
    '      </table>',
    '    </div>',
    '',
    '    <div class="alert alert--warn" style="margin-top:22px">' + icon('warning') + '<div><div class="alert__title">开放前请确认</div>MCP 工具继承 Token 对应用户的权限；草稿确认链接会过期；所有工具调用（含读取）都会写入操作日志，便于追溯。</div></div>',
    '  </div>',
    '</div>'
  ].join('\n')
});
