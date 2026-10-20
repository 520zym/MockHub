/* 设计稿公共部件：图标 + 应用骨架 + 小工具（无模板字符串，便于静态生成） */
export const ICONS = {
  connection: '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>',
  files: '<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>',
  magic: '<path d="m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72"/><path d="m14 7 3 3"/><path d="M5 6v4"/><path d="M19 14v4"/><path d="M10 2v2"/><path d="M7 8H3"/><path d="M21 16h-4"/><path d="M11 3H9"/>',
  chart: '<path d="M3 3v18h18"/><path d="M18 17V9"/><path d="M13 17V5"/><path d="M8 17v-3"/>',
  building: '<rect x="4" y="2" width="16" height="20" rx="2"/><path d="M9 22v-4h6v4"/><path d="M8 6h.01M16 6h.01M12 6h.01M12 10h.01M12 14h.01M16 10h.01M16 14h.01M8 10h.01M8 14h.01"/>',
  user: '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  doc: '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/>',
  gear: '<path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
  plus: '<path d="M5 12h14M12 5v14"/>',
  upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m17 8-5-5-5 5"/><path d="M12 3v12"/>',
  download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5"/><path d="M12 15V3"/>',
  folder: '<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>',
  tag: '<path d="M12.6 2.6A2 2 0 0 0 11.2 2H4a2 2 0 0 0-2 2v7.2a2 2 0 0 0 .6 1.4l8.2 8.2a2 2 0 0 0 2.8 0l7.2-7.2a2 2 0 0 0 0-2.8Z"/><circle cx="7.5" cy="7.5" r="1.2"/>',
  chevDown: '<path d="m6 9 6 6 6-6"/>',
  chevRight: '<path d="m9 6 6 6-6 6"/>',
  chevLeft: '<path d="m15 6-6 6 6 6"/>',
  close: '<path d="M18 6 6 18M6 6l12 12"/>',
  check: '<path d="m20 6-11 11-5-5"/>',
  checkCircle: '<circle cx="12" cy="12" r="9"/><path d="m8.5 12 2.5 2.5 4.5-5"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 16v-4M12 8h.01"/>',
  warning: '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4M12 17h.01"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  send: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/>',
  copy: '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  edit: '<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.1 2.1 0 0 1 3 3L12 15l-4 1 1-4Z"/>',
  trash: '<path d="M3 6h18"/><path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M10 11v6M14 11v6"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
  external: '<path d="M15 3h6v6"/><path d="M10 14 21 3"/><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/>',
  key: '<circle cx="7.5" cy="15.5" r="5.5"/><path d="m21 2-9.6 9.6"/><path d="m15.5 7.5 3 3L21 7.5 18 4.5"/>',
  server: '<rect x="2" y="3" width="20" height="8" rx="2"/><rect x="2" y="13" width="20" height="8" rx="2"/><path d="M6 7h.01M6 17h.01"/>',
  terminal: '<path d="m4 17 6-6-6-6"/><path d="M12 19h8"/>',
  history: '<path d="M3 12a9 9 0 1 0 9-9 9 9 0 0 0-6.36 2.64L3 8"/><path d="M3 3v5h5"/><path d="M12 8v4l3 2"/>',
  maximize: '<path d="M8 3H5a2 2 0 0 0-2 2v3M16 3h3a2 2 0 0 1 2 2v3M8 21H5a2 2 0 0 1-2-2v-3M16 21h3a2 2 0 0 0 2-2v-3"/>',
  arrowLeft: '<path d="M19 12H5"/><path d="m12 19-7-7 7-7"/>',
  arrowRight: '<path d="M5 12h14"/><path d="m12 5 7 7-7 7"/>',
  arrowUp: '<path d="M12 19V5"/><path d="m5 12 7-7 7 7"/>',
  sidePanel: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>',
  plug: '<path d="M12 22v-5"/><path d="M9 8V2M15 8V2"/><path d="M18 8v5a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V8Z"/>',
  fileText: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/><path d="M8 13h8M8 17h5"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13"/><path d="M3 6h.01M3 12h.01M3 18h.01"/>',
  wand: '<path d="m15 4 5 5L9 20H4v-5Z"/><path d="m14 5 5 5"/>',
  ai: '<path d="M11 3.2l1.8 5 5 1.8-5 1.8-1.8 5-1.8-5-5-1.8 5-1.8z"/><path d="M18.5 14.5l.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9z"/>',
  star: '<path d="M12 2.5l2.9 6 6.6.9-4.8 4.6 1.2 6.5-5.9-3.1-5.9 3.1 1.2-6.5L2.5 9.4l6.6-.9z"/>',
};

export function icon(name, cls) {
  cls = cls || '';
  const filled = name === 'ai' || name === 'star';
  return '<svg class="icon ' + cls + '" viewBox="0 0 24 24" ' + (filled ? 'style="fill:currentColor;stroke:none"' : '') + ' aria-hidden="true">' + (ICONS[name] || '') + '</svg>';
}

const NAV = [
  ['apis', '接口管理', 'connection'],
  ['files', '文件服务器', 'files'],
  ['variables', '动态变量', 'magic'],
  ['stats', '使用统计', 'chart'],
  ['teams', '团队管理', 'building'],
  ['users', '用户管理', 'user'],
  ['logs', '日志', 'doc'],
  ['settings', '全局设置', 'gear']
];

export function sidebar(active, opts) {
  opts = opts || {};
  const nav = NAV.map(function (item) {
    const on = item[0] === active ? ' nav-item--active' : '';
    return '<div class="nav-item' + on + '">' + icon(item[2]) + '<span>' + item[1] + '</span></div>';
  }).join('\n      ');

  let tree = '<div style="flex:1"></div>';
  if (opts.teamTree) {
    tree = [
      '<div class="sidebar__tree">',
      '  <div class="sidebar__divider"></div>',
      '  <div class="sidebar__section">团队筛选</div>',
      '  <div class="tree-node tree-node--active"><span class="tree-node__label">所有接口</span><span class="tree-node__count">12</span></div>',
      '  <div class="tree-node"><span class="team-tag team-tag--pay">PAY</span><span class="tree-node__label">支付中心</span><span class="tree-node__count">5</span></div>',
      '  <div class="tree-node"><span class="team-tag team-tag--user">USER</span><span class="tree-node__label">用户中心</span><span class="tree-node__count">4</span></div>',
      '  <div class="tree-node"><span class="team-tag team-tag--order">ORDER</span><span class="tree-node__label">订单服务</span><span class="tree-node__count">3</span></div>',
      '</div>'
    ].join('\n    ');
  }

  return [
    '<aside class="sidebar">',
    '  <div class="sidebar__logo"><div class="sidebar__mark">M</div><div class="sidebar__name">MockHub</div></div>',
    '  <div class="sidebar__nav">',
    '      ' + nav,
    '  </div>',
    '    ' + tree,
    '  <div class="sidebar__footer" style="color:#A3AED0">' + icon('sidePanel', 'icon--16') + '</div>',
    '</aside>'
  ].join('\n');
}

export function topbar(title) {
  return [
    '<header class="topbar">',
    '  <div class="topbar__left">',
    '    <div class="topbar__toggle">' + icon('sidePanel', 'icon--18') + '</div>',
    '    <div class="topbar__title">' + title + '</div>',
    '  </div>',
    '  <div class="topbar__user"><div class="avatar">张</div><span class="topbar__username">张一鸣</span></div>',
    '</header>'
  ].join('\n');
}

export function page(cfg) {
  return [
    '<!doctype html>',
    '<html lang="zh-CN">',
    '<head>',
    '<meta charset="utf-8">',
    '<meta name="viewport" content="width=1440, initial-scale=1">',
    '<title>' + cfg.title + ' · MockHub AI 设计稿</title>',
    '<link rel="stylesheet" href="assets/mock.css">',
    '<style>' + (cfg.css || '') + '</style>',
    '</head>',
    '<body>',
    sidebar(cfg.active || 'apis', { teamTree: cfg.teamTree }),
    topbar(cfg.title),
    '<main class="main">' + cfg.body + '</main>',
    cfg.overlay || '',
    '</body>',
    '</html>'
  ].join('\n');
}

export const methodTag = function (m) {
  const map = { GET: 'm-get', POST: 'm-post', PUT: 'm-put', DELETE: 'm-del' };
  return '<span class="m-tag ' + (map[m] || 'm-get') + '">' + m + '</span>';
};
export const typeTag = function (t) {
  return '<span class="type-tag ' + (t === 'SOAP' ? 'type-soap' : 'type-rest') + '">' + t + '</span>';
};
export const teamTag = function (t) {
  const map = { PAY: 'team-tag--pay', USER: 'team-tag--user', ORDER: 'team-tag--order' };
  return '<span class="team-tag ' + (map[t] || 'team-tag--pay') + '">' + t + '</span>';
};
export const switchOn = function (on) {
  return '<span class="switch' + (on === false ? '' : ' switch--on') + '"></span>';
};
export const cb = function (on) {
  return '<span class="cb' + (on === false ? '' : ' cb--on') + '">' + (on === false ? '' : icon('check')) + '</span>';
};
export const radio = function (on) {
  return '<span class="radio' + (on === false ? '' : ' radio--on') + '"></span>';
};
export const chip = function (text, cls) {
  return '<span class="chip ' + (cls || '') + '">' + text + '</span>';
};
export const aiBadge = function (text) {
  return '<span class="ai-badge">' + (text || 'AI') + '</span>';
};

export function codeBlock(lines, opts) {
  opts = opts || {};
  const start = opts.start || 1;
  const rows = lines.map(function (ln, i) {
    return '<div class="code__row"><span class="ln">' + (start + i) + '</span><span>' + ln + '</span></div>';
  }).join('');
  return '<div class="code ' + (opts.cls || '') + '">' + rows + '</div>';
}

export const k = function (s) { return '<span class="tok-k">"' + s + '"</span>'; };
export const str = function (v) { return '<span class="tok-s">"' + v + '"</span>'; };
export const num = function (v) { return '<span class="tok-n">' + v + '</span>'; };
export const pun = function (v) { return '<span class="tok-p">' + v + '</span>'; };