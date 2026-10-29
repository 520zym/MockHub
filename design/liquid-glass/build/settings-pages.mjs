import { renderPage, settingsNav, btn, field, badge, toggle, icon } from './ui.mjs';

// Illustrative instance data only. This generator never reads keys or calls an API.
const savedAt = '2026-10-28 14:32';
const subtitle = '管理实例级连接、能力与维护策略，保存后对所有团队生效。';
const heading = (title, note, extra = '') =>   '<div class="s-heading p-between"><div><h2 class="p-title p-title--lg">' + title + '</h2><p class="p-note">' + note + '</p></div>' + extra + '</div>';
const notice = (title, text, tone = 'blue', ic = 'info') =>   '<div class="s-notice s-notice--' + tone + '">' + icon(ic) + '<div><b>' + title + '</b><span>' + text + '</span></div></div>';
const save = (label, note = '配置已保存 · 当前实例生效') =>   '<div class="s-footer p-between"><span class="s-save-state">' + icon('check') + note + '</span>' + btn(label, { primary: true }) + '</div>';
const radio = (title, note = '', selected = false) =>   '<button type="button" role="radio" aria-checked="' + selected + '" class="s-choice' + (selected ? ' is-selected' : '') + '"><span class="s-radio">' + (selected ? '<i></i>' : '') + '</span><span><b>' + title + '</b>' + (note ? '<small>' + note + '</small>' : '') + '</span></button>';
const subsection = (title, right = '') => '<div class="s-subheading p-between"><h3 class="p-title">' + title + '</h3>' + right + '</div>';

function page(file, kind, content) {
  renderPage({ file, title: '全局设置', active: '全局设置', subtitle, badge: '实例配置',
    actions: badge('仅超级管理员') + badge('演示数据', 'blue'),
    body: ('<div class="p-settings s-settings s-' + kind + '">' + settingsNav(file) + '<div class="p-settings-content s-content">' + content + '</div></div>').replaceAll('><', '>\n<'),
    styles: ['settings.css'] });
  console.log('Generated ' + file);
}

const vendor = (letter, name, host, selected = false, tag = '') =>   '<button class="s-vendor' + (selected ? ' is-selected' : '') + '" type="button" role="radio" aria-checked="' + selected + '"><span class="s-vendor-mark">' + letter + '</span><span class="p-grow"><b>' + name + (tag ? '<small>' + tag + '</small>' : '') + '</b><span class="mono">' + host + '</span></span>' + (selected ? icon('check') : '') + '</button>';

page('14-settings-providers.html', 'providers',   '<section class="p-card s-card s-panel s-panel--provider">' +
    heading('AI 供应商', '模型请求全部由服务端发出，浏览器不会直接访问模型服务。', badge('API 接入', 'blue')) +
    '<div><div class="s-vendors" role="radiogroup" aria-label="AI 供应商">' +
      vendor('D', 'DeepSeek', 'api.deepseek.com', true) +
      vendor('O', 'Ollama', 'localhost:11434', false, '本地') +
      vendor(icon('code'), '自定义', 'OpenAI 兼容服务') +
    '</div><p class="s-micro">选择供应商会填入默认地址与模型；本地服务与自定义 OpenAI 兼容地址均可接入。</p></div>' +
    '<div class="s-form-grid">' +
      field('API 地址', 'https://api.deepseek.com/v1', { mono: true, hint: '自动拼接 /chat/completions，也可填完整地址。' }) +
      field('默认模型', 'deepseek-chat' + icon('down'), { mono: true, hint: '推荐模型 · 支持实时拉取或手动输入模型 ID。' }) +
    '</div>' +
    '<label class="p-field-group"><span>API Key</span><span class="s-key-row"><span class="p-field mono s-key-mask">•••••••••••••••••••• 8F2A</span>' + badge('已配置', 'green') + '<button class="s-text-action s-text-action--danger" type="button">清除</button></span><small>仅写入、不回显；留空保持已保存的 Key。此处为无效脱敏示例。</small></label>' +
    '<div class="s-test-row"><div class="p-row">' + btn('测试连接', { small: true, icon: 'play' }) + '<span class="s-test-status">' + icon('check') + '连接正常 <span class="mono">682 ms</span></span></div><div class="s-test-meta"><span class="mono">deepseek-chat</span><small>已保存配置的测试结果 · 14:33</small></div></div>' +
    notice('Key 只存在服务端', 'AES-GCM 加密保存，不进入日志或 localStorage。备份数据库时请同时备份 <code>data/.ai-key</code>，否则已保存的 Key 无法解密。', 'blue', 'lock') +
    '<div class="s-demo-model"><span class="s-vendor-mark">M</span><div class="p-grow"><b>内置演示模型</b><small>本地模板生成，无需 API Key；适合离线演示与 UI 验证。</small></div>' + badge('未启用') + btn('切换为演示模型', { small: true }) + '</div>' +
    '<div class="s-footer p-between"><span class="s-save-state">' + icon('check') + '最近保存：<span class="mono">' + savedAt + '</span></span>' + btn('保存供应商配置', { primary: true }) + '</div>' +
  '</section>');

const capability = (ic, title, note) =>   '<div class="s-capability"><span class="s-capability-icon">' + icon(ic) + '</span><div class="p-grow"><b>' + title + '</b><small>' + note + '</small></div>' + toggle(true) + '</div>';

page('15-settings-assistant.html', 'assistant',   '<section class="p-card s-card s-panel s-panel--assistant">' +
    heading('助手与能力', '选择可用能力与使用范围；供应商和密钥在「AI 供应商」独立维护。', badge('Beta', 'blue')) +
    '<div class="s-provider-summary"><span class="s-vendor-mark">D</span><div class="p-grow"><b>DeepSeek <span class="mono">deepseek-chat</span></b><small>API Key 已配置 · 最近连接测试 682 ms</small></div>' + badge('已连接', 'green') + '<a class="s-text-action" href="14-settings-providers.html">更换供应商 ' + icon('arrow') + '</a></div>' +
    '<div class="s-master-row"><div><b>启用 AI 助手</b><small>关闭后隐藏全部 AI 入口，不影响已有接口与规则。</small></div><div class="p-row">' + badge('运行中', 'green') + toggle(true) + '</div></div>' +
    '<div>' + subsection('能力开关', '<span class="s-micro">4 项能力已启用</span>') + '<div class="s-capabilities">' +
      capability('code', '生成响应体', '根据接口信息，在返回体编辑器生成 JSON / XML 示例。') +
      capability('edit', '生成条件规则', '将自然语言转为 conditions 结构，支持逐条确认。') +
      capability('search', '解释匹配结果', '在请求日志解释一次请求为什么命中或未命中。') +
      capability('file', '创建 / 修改接口草稿', '对话中准备变更，需在确认卡片确认后才会写入。') +
    '</div></div>' +
    '<div>' + subsection('使用范围') + '<div class="s-scope" role="radiogroup" aria-label="助手使用范围">' + radio('所有成员', '继承各自权限，团队数据隔离', true) + radio('仅超级管理员', '不向其他成员开放 AI 入口') + '</div></div>' +
    notice('脱敏、隔离与写入审计', '发送模型前移除 Authorization、Cookie、token、password 等敏感字段；AI 始终继承当前用户权限，所有 AI 写操作记录到操作日志。', 'blue', 'lock') +
    save('保存助手配置') +
  '</section>');

// Keep these names aligned with Settings.vue / McpToolRegistry.java, not legacy aliases.
const tools = [
  ['mockhub.search_apis', '按关键字搜索可见接口', true],
  ['mockhub.get_api_detail', '读取接口与返回体配置', true],
  ['mockhub.get_api_stats', '窗口统计与热门接口', true],
  ['mockhub.create_api_draft', '创建接口草稿，支持多返回体', true],
  ['mockhub.update_api_draft', '修改、替换或追加返回体', true],
  ['mockhub.apply_api_draft', '仅 DIRECT 策略下执行写入', false],
  ['mockhub.generate_response_body', '生成 JSON / XML 响应体', true],
  ['mockhub.generate_conditions', '自然语言转条件规则', true],
  ['mockhub.explain_match_result', '解释一次请求的命中链路', true]
];
const toolCards = tools.map(([name, desc, enabled]) => '<div class="s-tool' + (enabled ? '' : ' is-off') + '"><div class="p-between"><code>' + name + '</code>' + toggle(enabled) + '</div><small>' + desc + '</small></div>').join('');

page('16-settings-mcp.html', 'mcp',   '<section class="p-card s-card s-panel s-panel--mcp">' +
    heading('MCP Server', '向 Claude、Cursor、Codex 开放检索、统计与生成能力。', '<div class="s-mcp-master p-row">' + badge('已启用', 'green') + toggle(true) + '</div>') +
    '<div class="s-mcp-address"><div class="s-endpoint"><span class="s-method mono">POST</span><code>http://mockhub.internal:8080/mcp</code><span class="p-grow"></span>' + btn('复制服务地址', { small: true, icon: 'copy' }) + '</div><p class="s-micro">Streamable HTTP · POST /mcp · 返回 JSON，不支持 SSE 流。Bearer Token 继承所属用户权限。</p></div>' +
    '<div class="s-write-policy"><div class="p-row"><b>写操作策略</b><div class="s-policy-choices" role="radiogroup" aria-label="MCP 写操作策略">' + radio('草稿 + Web 确认', '', true) + radio('允许直接写入') + '</div>' + badge('推荐', 'blue') + '</div><p class="s-micro">当前草稿模式返回确认链接；只有用户在 MockHub 页面确认后才会写入数据库。</p></div>' +
    '<div class="s-tools-section">' + subsection('可用工具', '<span class="s-micro">8 / 9 项已启用</span>') + '<div class="s-tools">' + toolCards + '</div></div>' +
    '<div class="s-token-section"><div class="s-token-head p-between"><div class="p-row"><h3 class="p-title">访问 Token</h3><span class="s-micro">仅创建时显示一次，下方均为无效脱敏示例</span></div>' + btn('新建 Token', { small: true, icon: 'add' }) + '</div><table class="p-table s-token-table" aria-label="MCP Token 列表"><colgroup><col class="s-token-name"><col class="s-token-owner"><col class="s-token-prefix"><col class="s-token-last"><col class="s-token-state"><col class="s-token-action"></colgroup><thead><tr><th>名称</th><th>所有者</th><th>前缀</th><th>最后使用</th><th>状态</th><th>操作</th></tr></thead><tbody><tr><td>Cursor · 本地开发</td><td>张一鸣</td><td class="mono">mcp_••••8F2A</td><td class="mono">10-28 14:26</td><td>' + badge('已启用', 'green') + '</td><td><button class="s-text-action s-text-action--danger" type="button">撤销</button></td></tr><tr><td>Codex · 联调演示</td><td>李思远</td><td class="mono">mcp_••••3B7C</td><td class="mono">10-26 09:18</td><td>' + badge('已撤销') + '</td><td><span class="s-micro">—</span></td></tr></tbody></table></div>' +
    '<div class="s-inline-note">' + icon('lock') + '<span>撤销立即使对应客户端失效；生成能力还受 AI 助手及其能力开关约束。</span></div>' +
    save('保存 MCP 配置', '草稿确认后才写入 · 当前配置已保存') +
  '</section>');

page('17-settings-general.html', 'general',   '<section class="p-card s-card s-panel s-general-main">' +
    heading('基础设置', '配置实例的对外地址与 Mock 跨域访问，不改变管理接口的权限。', badge('服务与安全')) +
    field('服务器地址', 'http://mockhub.internal:8080', { mono: true, hint: '用于拼接 Mock URL、MCP 确认链接与导出文件中的地址；留空时自动检测内网 IP。' }) +
    '<div class="s-address-uses"><div><span>' + icon('code') + 'Mock URL</span><code>/mock/{identifier}{path}</code><small>复制给调用方的完整接口地址</small></div><div><span>' + icon('check') + 'MCP 确认链接</span><code>/#/mcp-confirm/{id}</code><small>外部工具返回的 Web 确认入口</small></div><div><span>' + icon('download') + '导出内容</span><code>当前服务器地址</code><small>确保导出地址在团队网络可达</small></div></div>' +
    '<div class="s-master-row s-cors-row"><div><b>Mock CORS</b><small>启用后，Mock 接口允许所有跨域请求。</small></div><div class="p-row">' + badge('已启用', 'blue') + toggle(true) + '</div></div>' +
    notice('跨域放行仅作用于 Mock 接口', '管理接口仍按登录态与用户权限校验；开启 Mock CORS 不会开放管理权限。', 'blue', 'lock') +
    save('保存基础设置', '配置已保存 · 服务器地址与 CORS 已生效') +
  '</section>' +
  '<section class="p-card s-card s-service-summary">' + subsection('服务摘要', badge('只读信息')) + '<div class="s-summary-grid"><div><span>实例状态</span><b class="s-running">' + '<i class="p-dot"></i>服务运行中</b><small>当前内网实例</small></div><div><span>服务端口</span><b class="mono">8080</b><small>对外地址需可从客户端访问</small></div><div><span>配置作用范围</span><b>当前实例 · 所有团队</b><small>仅超级管理员可修改</small></div></div><p class="s-micro">日志清理与文件维护分别在「日志与保留」「文件存储」管理，不在本页重复保存。</p></section>');

page('18-settings-logs.html', 'logs',   '<section class="p-card s-card s-panel s-logs-main">' +
    heading('日志与保留', '为操作日志和请求日志设置自动清理规则，控制实例的日志占用。', badge('自动保留策略', 'blue')) +
    '<div>' + subsection('保留模式') + '<div class="s-retain-modes" role="radiogroup" aria-label="日志保留模式">' + radio('按条数保留', '100–100000 条，超出后删除最旧记录', true) + radio('按天数保留', '1–365 天，超期日志自动清理') + '</div></div>' +
    '<div class="s-retain-config"><label class="p-field-group"><span>保留条数</span><span class="s-number-control"><span class="mono">10000</span><span class="s-unit">条</span><span class="s-stepper"><button type="button" aria-label="增加保留条数">+</button><button type="button" aria-label="减少保留条数">−</button></span></span><small>允许 100–100000 条，步进 100 条。</small></label><div class="s-retain-current"><span>当前生效策略</span><b>保留最新 <span class="mono">10000</span> 条</b><small>按时间从旧到新自动清理超出部分。</small></div></div>' +
    notice('旧记录会被自动删除', '达到保留上限后，系统会自动删除最旧的日志。删除后不可恢复，请按排查与审计需要选择保留规模。', 'amber', 'trash') +
    '<div class="s-rule-preview"><span class="s-rule-step">01</span><div><b>新日志写入</b><small>请求与操作正常记录</small></div>' + icon('arrow') + '<span class="s-rule-step">02</span><div><b>检查保留上限</b><small>当前按 10000 条计算</small></div>' + icon('arrow') + '<span class="s-rule-step">03</span><div><b>删除最旧记录</b><small>保留最新明细</small></div></div>' +
    save('保存日志设置', '已保存 · 按条数保留 10000 条') +
  '</section>' +
  '<section class="p-card s-card s-retention-info">' + subsection('保留规则的影响', badge('范围说明')) + '<div class="s-impact-grid"><div><b>操作日志</b><p>历史配置变更、AI 写入等审计明细受保留策略约束。</p></div><div><b>请求日志</b><p>清理后的请求明细及匹配链路将无法继续追溯。</p></div><div><b>统计窗口</b><p>按日志聚合的时间窗口，仅基于仍保留的日志计算。</p></div></div><div class="s-inline-note s-inline-note--plain">' + icon('info') + '<span>接口累计命中数独立累计，不会因为清理日志而回退。</span></div></section>');

const orphanFiles = [
  ['legacy-response.json', 'team-pay/legacy-response.json', '1.2 MB', '10-25 10:23'],
  ['payment-demo.pdf', 'team-pay/payment-demo.pdf', '2.8 MB', '10-24 16:42'],
  ['old-avatar.png', 'team-user/old-avatar.png', '864 KB', '10-23 09:15'],
  ['sample-export.zip', 'team-pay/sample-export.zip', '1.3 MB', '10-21 11:08']
];
const orphanRows = orphanFiles.map(([name, path, size, modified]) => '<tr><td><span class="s-file-cell">' + icon('file') + '<span>' + name + '</span></span></td><td class="mono">' + path + '</td><td class="mono">' + size + '</td><td class="mono">' + modified + '</td></tr>').join('');

page('19-settings-storage.html', 'storage',   '<section class="p-card s-card s-storage-overview">' +
    heading('文件存储', '实例磁盘维护：扫描未被任何文件类型返回体引用的文件。', badge('只读配置')) +
    '<div class="s-storage-location"><div><span>存储目录</span><code>data/files</code></div><div><span>单文件上传上限</span><b class="mono">10 MB</b></div></div>' +
    '<div class="s-storage-metrics"><div><span>文件总量</span><b>28 <small>个</small></b><small class="mono">164.8 MB</small></div><div><span>引用文件</span><b>24 <small>个</small></b><small>由文件类型返回体引用</small></div><div class="s-orphan-metric"><span>孤儿文件</span><b>4 <small>个</small></b><small class="mono">6.2 MB · 可清理</small></div></div>' +
  '</section>' +
  '<section class="p-card s-card s-panel s-storage-results">' +
    '<div class="p-between"><div><div class="p-row"><h3 class="p-title">孤儿文件扫描结果</h3>' + badge('4 个文件', 'amber') + '</div><p class="s-micro">最近扫描：2026-10-28 14:35 · 合计 6.2 MB · 路径相对于 data/files</p></div>' + btn('扫描孤儿文件', { small: true, icon: 'refresh' }) + '</div>' +
    '<table class="p-table s-orphan-table" aria-label="孤儿文件扫描结果"><colgroup><col class="s-file-name"><col class="s-file-path"><col class="s-file-size"><col class="s-file-date"></colgroup><thead><tr><th>文件名</th><th>存储路径</th><th>大小</th><th>修改时间</th></tr></thead><tbody>' + orphanRows + '</tbody></table>' +
    notice('清理后不可恢复', '只删除未被任何文件类型返回体引用的文件，不影响仍被引用的文件。请确认扫描结果后再执行清理。', 'amber', 'trash') +
    '<div class="s-storage-separation">' + icon('folder') + '<div><b>与「文件服务器」独立</b><small>此处仅维护实例存储与孤儿文件；文件服务的浏览、上传和管理请前往 <a href="07-files.html">文件服务器 ' + icon('arrow') + '</a>。</small></div></div>' +
    '<div class="s-footer p-between"><span class="s-save-state">' + icon('check') + '扫描完成 · 共检查 28 个存储文件</span>' + btn('清理 4 个孤儿文件', { danger: true, icon: 'trash' }) + '</div>' +
  '</section>');
