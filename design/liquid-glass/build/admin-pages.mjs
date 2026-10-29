import { renderPage, btn, icon, field, badge, toggle, footer, esc } from './ui.mjs';

// This builder writes four offline design previews only. No application/API integration.
const styles = ['admin.css'];
const action = (label, attrs, options = {}) => btn(label, options).replace('type="button"', 'type="button" ' + attrs);
const demo = (label, text, options = {}) => action(label, 'data-demo="' + esc(text) + '"', options);
const linkButton = (label, attrs = '', danger = false) => '<button type="button" class="admin-link' + (danger ? ' admin-link--danger' : '') + '" ' + attrs + '>' + label + '</button>';
const input = (label, value, attrs = '') => '<label class="admin-field">' + label + '<input class="admin-input" value="' + esc(value) + '" ' + attrs + '></label>';
const select = (label, options, value, attrs = '') => '<span class="admin-select-wrap"><select class="admin-select" aria-label="' + esc(label) + '" ' + attrs + '>' + options.map(([key, text]) => '<option value="' + key + '"' + (key === value ? ' selected' : '') + '>' + text + '</option>').join('') + '</select>' + icon('down') + '</span>';
const roles = [['TEAM_ADMIN', '团队管理员'], ['MEMBER', '普通成员']];
const roleField = (label, value = 'MEMBER', attrs = '', empty = false) => select(label, empty ? [['', '未分配'], ...roles] : roles, value, attrs);
const note = text => '<div class="admin-demo-foot">' + icon('info') + text + '</div>';
const kv = (label, value) => '<dl class="p-kv"><dt>' + label + '</dt><dd>' + value + '</dd></dl>';
const pager = (count, range = '1—10') => footer(count).replace('每页 20 条', '每页 10 条').replace('1—8', range);
const readonlyToggle = () => toggle(true).replace('<button ', '<button disabled ');
const closeButton = (id, label = '取消') => action(label, 'data-close="' + id + '"');
const modal = (id, title, content, confirm = '保存演示', mode = '') => '<div class="admin-overlay" id="' + id + '" hidden><form class="admin-dialog" role="dialog" aria-modal="true" aria-labelledby="' + id + '-title" data-dialog-form="' + mode + '"><div class="p-between"><h2 id="' + id + '-title">' + title + '</h2>' + linkButton(icon('close'), 'data-close="' + id + '" aria-label="关闭"') + '</div>' + content + '<div class="admin-actions">' + closeButton(id) + btn(confirm, { primary: true }).replace('type="button"', 'type="submit"') + '</div></form></div>';

function localInteractions() {
  const toast = text => {
    let box = document.querySelector('.admin-toast');
    if (!box) { box = document.createElement('div'); box.className = 'admin-toast'; box.setAttribute('role', 'status'); document.body.append(box); }
    box.textContent = text;
    clearTimeout(window.adminToastTimer);
    window.adminToastTimer = setTimeout(() => box.remove(), 2800);
  };
  let pendingMember = null;
  let demoMemberTotal = 8;
  const syncMembers = delta => {
    demoMemberTotal += delta;
    document.getElementById("member-total").textContent = demoMemberTotal + " 位成员";
    document.getElementById("member-summary").textContent = "展示 " + document.querySelectorAll("#member-list .admin-member-row").length + " / " + demoMemberTotal + " 位";
    document.querySelector(".admin-team-card.is-active .admin-team-stats strong").textContent = demoMemberTotal;
  };
  document.addEventListener('click', event => {
    const open = event.target.closest('[data-open]');
    if (open) {
      const dialog = document.getElementById(open.dataset.open);
      if (dialog) {
        if (open.dataset.member) { pendingMember = open.closest('.admin-member-row'); dialog.querySelector('[data-member-name]').textContent = open.dataset.member; }
        if (open.dataset.teamId) {
          dialog.querySelector('[name="name"]').value = open.dataset.teamName;
          dialog.querySelector('[name="identifier"]').value = open.dataset.teamId;
          dialog.querySelector('[name="color"]').value = open.dataset.teamColor;
        }
        if (open.dataset.username) {
          dialog.querySelector('[name="displayName"]').value = open.dataset.username;
          const role = dialog.querySelector('[name="globalRole"]');
          role.value = open.dataset.super === 'true' ? 'SUPER_ADMIN' : 'USER';
          role.disabled = open.dataset.super === 'true';
          dialog.querySelector('[data-super-note]').hidden = !role.disabled;
        }
        dialog.hidden = false;
        dialog.querySelector('input,select,button')?.focus();
      }
    }
    const close = event.target.closest('[data-close]');
    if (close) document.getElementById(close.dataset.close).hidden = true;
    const addMember = event.target.closest('[data-add-member]');
    if (addMember) {
      const user = document.getElementById('new-member');
      if (!user.value) return toast('请先选择要添加的用户');
      const row = document.getElementById('member-row-template').content.firstElementChild.cloneNode(true);
      const [name, username] = user.value.split('|');
      row.querySelector('.p-initial').textContent = name.slice(0, 1);
      row.querySelector('.member-name').textContent = name;
      row.querySelector('.member-login').textContent = username;
      row.querySelector('select').value = document.getElementById('new-member-role').value;
      row.querySelector('[data-member]').dataset.member = name;
      document.getElementById('member-list').append(row);
      syncMembers(1);
      user.selectedOptions[0].remove();
      user.value = '';
      toast('已在当前静态稿中添加 ' + name + '，未发送业务请求');
    }
    if (event.target.closest('[data-reset-password]')) {
      const pw = document.getElementById('reset-password');
      if (pw.value.length < 6) { toast('新密码至少 6 位'); pw.focus(); return; }
      pw.value = '';
      document.getElementById('reset-status').textContent = '演示已重置 · 下次登录需强制修改';
      toast('密码重置状态已演示，未修改任何真实账号');
    }
    if (event.target.closest('[data-save-roles]')) toast('王琳的团队角色已在本页预览，真实权限未更改');
    const stateButton = event.target.closest('[data-draft-state]');
    if (stateButton) setDraftState(stateButton.dataset.draftState);
  });
  document.addEventListener('submit', event => {
    const form = event.target.closest('[data-dialog-form]');
    if (!form) return;
    event.preventDefault();
    if (form.dataset.dialogForm === 'remove-member' && pendingMember) { pendingMember.remove(); syncMembers(-1); }
    form.closest('.admin-overlay').hidden = true;
    toast(form.dataset.dialogForm === 'remove-member' ? '已演示移除成员；真实团队未改变' : '已完成本地表单演示；没有保存到线上');
  });
  document.addEventListener('change', event => {
    if (event.target.matches('[data-member-role]')) toast('已切换演示角色，真实权限未改变');
    if (event.target.id === 'draft-state-preview') setDraftState(event.target.value);
  });
  const search = document.getElementById('user-search');
  if (search) search.addEventListener('input', () => {
    const keyword = search.value.trim().toLowerCase();
    document.querySelectorAll('[data-user-row]').forEach(row => { row.hidden = !row.textContent.toLowerCase().includes(keyword); });
  });
  function setDraftState(state) {
    const states = {
      PENDING: ['待确认', '请先检查修改内容', '确认后才会更新接口；服务端会重新校验草稿归属、有效期与团队权限。'],
      CONFIRMED: ['已应用', '演示：退款接口已更新', '本地预览已切换为完成状态，可继续查看编辑稿；没有向真实接口写入。'],
      CANCELLED: ['已取消', '该草稿已取消', '草稿不再可操作；如需继续，请让 AI 重新生成。此处仅为演示状态。'],
      EXPIRED: ['已过期', '该草稿已失效', '有效期已结束，请让 AI 重新生成；不会应用任何接口修改。']
    };
    const [label, title, copy] = states[state];
    const pill = document.getElementById('draft-status');
    pill.textContent = label;
    pill.className = 'p-badge p-badge--' + (state === 'PENDING' ? 'blue' : state === 'CONFIRMED' ? 'green' : 'gray');
    document.getElementById('draft-state-title').textContent = title;
    document.getElementById('draft-state-copy').textContent = copy;
    document.getElementById('draft-pending-actions').hidden = state !== 'PENDING';
    document.getElementById('draft-complete-link').hidden = state !== 'CONFIRMED';
    document.getElementById('draft-state-preview').value = state;
    toast('静态状态预览：' + label + ' · 没有线上写入');
  }
}
const interactions = '<script>(' + localInteractions.toString() + ')();</script>';

const teams = [
  { id: 'PAY', name: '支付中心', members: 8, apis: 58, color: 'pay', description: '支付、退款与渠道回调', people: ['张', '李', '王', '赵'] },
  { id: 'USER', name: '用户中心', members: 11, apis: 42, color: 'user', description: '账号、认证与用户资料', people: ['王', '林', '孙', '许'] },
  { id: 'ORDER', name: '订单中心', members: 9, apis: 31, color: 'order', description: '订单、履约与商品服务', people: ['陈', '郑', '周', '何'] },
  { id: 'OPS', name: '平台运维', members: 4, apis: 12, color: 'ops', description: '网关、监控与基础服务', people: ['周', '张', '吴'] }
];
const teamCard = team => '<article class="admin-team-card' + (team.id === 'PAY' ? ' is-active' : '') + '"><div class="p-row"><span class="admin-team-mark admin-team-mark--' + team.color + '">' + team.id + '</span><div class="p-grow"><h3>' + team.name + '</h3><span class="admin-minor">' + team.description + '</span></div>' + (team.id === 'PAY' ? badge('正在管理', 'blue') : '') + '</div><div class="admin-team-stats"><div><strong>' + team.members + '</strong><span>成员</span></div><div><strong>' + team.apis + '</strong><span>接口</span></div><div class="p-grow"></div><div class="p-avatar-stack">' + team.people.map(person => '<span>' + person + '</span>').join('') + '</div></div><div class="admin-team-ops">' + linkButton('编辑', 'data-open="edit-team-dialog" data-team-name="' + team.name + '" data-team-id="' + team.id + '" data-team-color="' + ({ pay: '#3568D4', user: '#2E8B6A', order: '#A5782D', ops: '#6E62B5' })[team.color] + '"') + linkButton('成员管理 ' + icon('arrow'), team.id === 'PAY' ? 'data-demo="右侧已展开支付中心成员管理"' : 'data-demo="此静态稿以支付中心演示团队成员管理"') + '<button class="admin-link admin-disabled" type="button" disabled title="团队下仍有 ' + team.apis + ' 个接口，不能删除">删除</button></div></article>';
const members = [['张一鸣', 'zhangyiming', 'TEAM_ADMIN'], ['李哲', 'lizhe', 'TEAM_ADMIN'], ['王琳', 'wanglin', 'MEMBER'], ['赵晴', 'zhaoqing', 'MEMBER'], ['吴昊', 'wuhao', 'MEMBER'], ['郑然', 'zhengran', 'MEMBER']];
const memberRow = ([name, username, role]) => '<div class="admin-member-row"><span class="p-initial">' + name.slice(0, 1) + '</span><div class="p-grow"><div class="member-name">' + name + '</div><div class="member-login">' + username + '</div></div>' + roleField(name + '的团队角色', role, 'data-member-role') + linkButton('移除', 'data-open="remove-member-dialog" data-member="' + name + '"', true) + '</div>';
const teamDialogFields = input('团队名称', '支付中心', 'name="name" required maxlength="50"') + input('团队标识', 'PAY', 'name="identifier" required pattern="[A-Z0-9]{2,8}" maxlength="8"') + '<p class="p-note">2–8 位大写字母或数字，用于拼接 Mock 路径。</p>' + input('团队颜色', '#3568D4', 'name="color" required pattern="#[A-Fa-f0-9]{6}"') + '<div class="p-hint">仅超级管理员可修改团队信息。此表单不会写入真实数据。</div>';
const teamBody = '<div class="admin-layout"><div class="admin-left"><div class="admin-team-grid">' + teams.map(teamCard).join('') + '</div><section class="p-card admin-team-settings"><div class="p-between"><div><div class="p-eyebrow">SELECTED TEAM</div><h2 class="p-title">支付中心 · 团队信息</h2></div>' + action('编辑信息', 'data-open="edit-team-dialog"', { small: true, icon: 'edit' }) + '</div><div class="admin-edit-grid">' + field('团队名称', '支付中心') + field('团队标识', 'PAY', { mono: true }) + field('团队颜色', '<span class="admin-color-value"><span class="admin-color-dot"></span>#3568D4</span>', { mono: true }) + '</div><div class="admin-note">' + icon('lock') + '团队下仍有 58 个接口，当前不可删除；需先迁移或删除这些接口。</div></section>' + note('仅超级管理员可创建、编辑团队与管理成员。所有数据均为静态演示。') + '</div><aside class="p-card admin-panel"><div class="p-between"><div><div class="p-eyebrow">MEMBER MANAGEMENT</div><h2 class="admin-h2">支付中心 · 成员</h2></div>' + badge('8 位成员', 'blue').replace('<span ', '<span id="member-total" ') + '</div><div class="admin-members-form"><label class="admin-field">添加用户' + select('选择待添加用户', [['', '选择要添加的用户'], ['周远|zhouyuan', '周远 · zhouyuan'], ['林嘉|linjia', '林嘉 · linjia']], '', 'id="new-member"') + '</label><label class="admin-field">团队角色' + roleField('新成员角色', 'MEMBER', 'id="new-member-role"') + '</label>' + action('添加到支付中心', 'data-add-member', { primary: true, small: true, icon: 'add' }) + '</div><div><div class="admin-subhead"><h3>当前成员</h3><span class="admin-minor" id="member-summary">展示 6 / 8 位</span></div><div id="member-list">' + members.map(memberRow).join('') + '</div></div><div class="p-hint"><b>团队角色与全局角色独立</b><br>TEAM_ADMIN 可维护分组、动态变量与文件；MEMBER 可维护接口。超级管理员仍拥有全部团队权限。</div></aside></div><template id="member-row-template">' + memberRow(['新成员', 'newmember', 'MEMBER']) + '</template>' + modal('new-team-dialog', '新建团队', input('团队名称', '', 'required placeholder="例如：结算中心"') + input('团队标识', '', 'required pattern="[A-Z0-9]{2,8}" placeholder="2–8 位大写字母或数字"') + input('团队颜色', '#3568D4', 'required'), '创建演示', 'new-team') + modal('edit-team-dialog', '编辑团队', teamDialogFields, '保存演示', 'edit-team') + modal('remove-member-dialog', '移除团队成员', '<div class="p-note">确定从支付中心移除 <b data-member-name>王琳</b>？</div><div class="p-hint">只移除该团队关联，不会删除用户账号；此操作仅用于当前静态稿演示。</div>', '确认移除', 'remove-member') + interactions;

const users = [
  ['zhangyiming', '张一鸣', 'SUPER_ADMIN', ['PAY', 'OPS'], '2026-08-01'], ['wanglin', '王琳', 'USER', ['PAY', 'USER'], '2026-08-03'], ['lizhe', '李哲', 'USER', ['PAY'], '2026-08-05'], ['chenmo', '陈默', 'USER', ['ORDER'], '2026-08-06'], ['zhaoqing', '赵晴', 'USER', ['PAY'], '2026-08-09'], ['zhouyuan', '周远', 'USER', ['OPS'], '2026-08-12'], ['linjia', '林嘉', 'USER', ['USER'], '2026-08-15'], ['zhengran', '郑然', 'USER', ['ORDER', 'PAY'], '2026-09-02'], ['sunyue', '孙悦', 'USER', ['USER'], '2026-09-05'], ['wuhao', '吴昊', 'USER', ['PAY', 'OPS'], '2026-09-08']
];
const userTableRows = users.map(([username, name, role, userTeams, date]) => '<tr data-user-row' + (username === 'wanglin' ? ' class="is-selected"' : '') + '><td class="mono">' + username + '</td><td><span class="p-cell-title">' + name + '</span></td><td>' + badge(role === 'SUPER_ADMIN' ? '超级管理员' : '普通用户', role === 'SUPER_ADMIN' ? 'blue' : 'gray') + '</td><td><div class="p-row" style="gap:4px">' + userTeams.map(team => badge(team, 'gray')).join('') + '</div></td><td class="mono p-quiet">' + date + '</td><td><div class="admin-row-actions">' + linkButton('编辑', 'data-open="edit-user-dialog" data-username="' + name + '" data-super="' + (role === 'SUPER_ADMIN') + '"') + linkButton('分配', 'data-demo="右侧展示王琳的团队角色分配；不会更改真实权限"') + linkButton('重置', 'data-demo="右侧可演示密码重置；下次登录强制改密"') + (role === 'SUPER_ADMIN' ? '<button type="button" class="admin-link admin-disabled" disabled title="超级管理员不可删除">删除</button>' : linkButton('删除', 'data-demo="删除需二次确认；此稿不执行真实用户删除"', true)) + '</div></td></tr>').join('');
const assignedRoles = { PAY: 'MEMBER', USER: 'TEAM_ADMIN', ORDER: '', OPS: '' };
const userRoleRows = teams.map(team => '<div class="admin-role-row"><div class="admin-role-team"><span class="tdot tdot--' + (team.color === 'ops' ? 'pay' : team.color) + '"></span><div>' + team.name + '<small>' + team.id + '</small></div></div>' + roleField(team.name + '团队角色', assignedRoles[team.id], '', true) + '</div>').join('');
const userBody = '<div class="admin-layout admin-layout--users"><section class="p-card admin-table-shell"><div class="p-toolbar"><label class="admin-search" style="width:220px">' + icon('search') + '<input id="user-search" aria-label="筛选当前页用户" placeholder="搜索用户名或显示名"></label><span class="p-grow"></span><span class="admin-minor">当前页 10 / 26 位</span></div><div class="p-table-wrap"><table class="p-table admin-user-table"><colgroup><col style="width:102px"><col style="width:63px"><col style="width:89px"><col style="width:105px"><col style="width:94px"><col style="width:142px"></colgroup><thead><tr><th>用户名</th><th>显示名</th><th>全局角色</th><th>所属团队</th><th>创建时间</th><th>操作</th></tr></thead><tbody>' + userTableRows + '</tbody></table></div>' + pager('共 26 位用户') + '<div class="admin-note" style="padding:11px 8px 5px">' + icon('lock') + '超级管理员不可降级、不可删除；团队角色不会改变全局角色。</div></section><aside class="p-card admin-panel"><div class="p-between"><span class="p-eyebrow" style="margin:0">TEAM ASSIGNMENT</span>' + badge('已选用户', 'blue') + '</div><div class="admin-user-head"><span class="p-initial">王</span><div><h2>王琳</h2><span class="admin-short-code">wanglin · 普通用户 USER</span></div></div><section><div class="admin-subhead"><h3>分配团队与角色</h3><span class="admin-minor">已加入 2 个</span></div>' + userRoleRows + '<p class="p-note">选择“未分配”将移除该团队关联。保存整体替换团队分配，不影响其他用户。</p></section>' + action('保存团队分配', 'data-save-roles', { primary: true, small: true, icon: 'check' }) + '<section><div class="admin-subhead"><h3>重置密码</h3>' + badge('管理员操作', 'gray') + '</div><div class="admin-password-row"><input id="reset-password" class="admin-input" type="password" aria-label="王琳的新密码" placeholder="新密码，至少 6 位" autocomplete="new-password">' + action('重置', 'data-reset-password', { small: true }) + '</div><p class="p-note" id="reset-status">重置后，王琳下次登录必须修改初始密码。</p></section><div class="p-hint"><b>全局角色保护</b><br>超级管理员只能修改显示名，不能降为普通用户，也不能删除。普通用户的团队管理员身份在此单独分配。</div>' + note('演示账号与角色；不调用账号或权限 API。') + '</aside></div>' + modal('new-user-dialog', '新建用户', input('用户名', '', 'name="username" required maxlength="50" placeholder="用于登录"') + input('初始密码', '', 'type="password" required minlength="6" placeholder="至少 6 位" autocomplete="new-password"') + input('显示名', '', 'required maxlength="50"') + '<label class="admin-field">全局角色' + select('新用户全局角色', [['USER', '普通用户 USER'], ['SUPER_ADMIN', '超级管理员']], 'USER') + '</label><div class="p-hint">首次登录强制修改初始密码；创建后可分配团队。</div>', '创建演示', 'new-user') + modal('edit-user-dialog', '编辑用户', input('显示名', '王琳', 'name="displayName" required maxlength="50"') + '<label class="admin-field">全局角色' + select('用户全局角色', [['USER', '普通用户 USER'], ['SUPER_ADMIN', '超级管理员']], 'USER', 'name="globalRole"') + '</label><p class="p-note" data-super-note hidden>超级管理员角色不可降级，仅可修改显示名。</p>', '保存演示', 'edit-user') + interactions;

const logs = [
  ['16:42:08', '张一鸣', '修改接口', 'blue', 'API', '退款申请', '新增超时返回体；保留默认响应'], ['16:38:41', '李哲', '修改接口', 'blue', 'API', '订单详情', '响应延迟 0 → 120 ms'], ['16:35:12', '王琳', '新建标签', 'green', 'TAG', '联调中', '颜色 #3568D4'], ['16:31:56', '张一鸣', '修改分组', 'gray', 'GROUP', '渠道回调', '调整分组展示顺序'], ['16:28:33', '赵晴', '创建接口', 'green', 'API', '微信退款查询', 'GET /api/pay/refund/query'], ['16:24:09', '李哲', '启用接口', 'green', 'API', '支付宝回调', 'enabled: false → true'], ['16:19:47', '张一鸣', '导入接口', 'gray', 'IMPORT', 'pay-mocks.zip', '合并导入：新增 4 / 跳过 2'], ['16:14:25', '李哲', '修改变量', 'blue', 'VARIABLE', 'refund_status', '新增候选值 PROCESSING'], ['16:09:18', '王琳', '复制接口', 'gray', 'API', '退款申请 - 副本', '由退款申请复制'], ['16:03:50', '赵晴', '修改接口', 'blue', 'API', '余额查询', '更新默认响应中的冻结金额']
];
const logTableRows = logs.map((row, index) => '<tr' + (index === 0 ? ' class="is-selected"' : '') + '><td class="mono">10-28 ' + row[0] + '</td><td>' + row[1] + '</td><td>' + badge(row[2], row[3]) + '</td><td class="mono p-quiet">' + row[4] + '</td><td><span class="p-cell-title">' + row[5] + '</span></td><td class="admin-wrap">' + row[6] + '</td></tr>').join('');
const logBody = '<div class="admin-layout admin-layout--logs"><section class="p-card admin-table-shell"><div class="p-toolbar"><div class="p-tabs"><a class="is-active" href="12-operation-logs.html" aria-current="page">操作日志</a><a href="03-logs.html">请求日志</a></div><span class="p-grow"></span><span class="admin-scope"><span class="tdot tdot--pay"></span>支付中心 PAY</span></div><div class="admin-note" style="padding:0 7px 13px">' + icon('info') + '当前仅展示支付中心的操作记录；切换团队后重新查询，不跨团队合并。</div><div class="p-table-wrap"><table class="p-table admin-log-table"><colgroup><col style="width:101px"><col style="width:57px"><col style="width:75px"><col style="width:65px"><col style="width:112px"><col></colgroup><thead><tr><th>时间</th><th>操作人</th><th>操作类型</th><th>目标类型</th><th>目标名称</th><th>详细信息</th></tr></thead><tbody>' + logTableRows + '</tbody></table></div>' + pager('共 342 条 · 支付中心') + '</section><aside class="p-card admin-panel admin-log-detail"><div class="p-between"><span class="p-eyebrow" style="margin:0">OPERATION DETAIL</span>' + badge('修改接口', 'blue') + '</div><div><h2 class="admin-h2">退款申请</h2><div class="admin-short-code" style="margin-top:6px">POST /api/pay/refund</div></div><div>' + kv('操作人', '张一鸣 · zhangyiming') + kv('操作时间', '<span class="mono">2026-10-28 16:42:08</span>') + kv('所属团队', '<span class="p-blue">PAY · 支付中心</span>') + kv('目标类型', '<span class="mono">API / api_refund_002</span>') + kv('操作标识', '<span class="mono">op_a13f80</span>') + kv('管理请求', '<span class="mono">req_mgmt_8f3a2c</span>') + '</div><div><div class="admin-subhead"><h3>本次变更</h3><span class="admin-caption">BEFORE → AFTER</span></div><div class="admin-diff-stack"><span class="admin-diff-line admin-diff-line--old">− responses.length: 1</span><span class="admin-diff-line admin-diff-line--new">+ responses.length: 2</span><span class="admin-diff-line admin-diff-line--new">+ name: "上游超时"</span><span class="admin-diff-line admin-diff-line--new">+ responseCode: 504</span><span class="admin-diff-line admin-diff-line--new">+ delayMs: 2000</span></div></div><div><div class="admin-subhead"><h3>记录摘要</h3></div><p class="p-code">新增条件返回体：Header<br>X-Scenario = timeout<br>原默认响应保持启用，作为兜底。</p></div><a href="02-api-edit.html" class="admin-link">查看接口编辑稿 ' + icon('arrow') + '</a>' + note('演示数据 · 变更展开与请求标识为审计呈现示意，不执行重放或写入。') + '</aside></div>' + interactions;

const defaultResponse = JSON.stringify({ code: 0, message: '退款受理成功', data: { refundNo: 'RF202610280021', status: 'PROCESSING', amount: 12800 } }, null, 2);
const timeoutResponse = JSON.stringify({ code: 'UPSTREAM_TIMEOUT', message: '上游处理超时，请稍后重试', retryable: true }, null, 2);
const responseCard = (number, title, code, rule, delay, body, fallback = false) => '<article class="admin-response"><div class="p-between"><div class="p-row" style="gap:7px"><span class="admin-caption">0' + number + '</span><h3>' + title + '</h3></div>' + badge(String(code), code === 200 ? 'green' : 'amber') + '</div><div class="admin-response-meta"><span>application/json</span><span>·</span><span>延迟 ' + delay + ' ms</span></div><div class="admin-response-rule">' + (fallback ? badge('兜底', 'gray') : badge('条件', 'blue')) + '<span>' + rule + '</span></div><pre class="p-code">' + esc(body) + '</pre><div class="admin-response-footer"><span>' + (fallback ? '其他条件未命中时返回' : 'Header 名称不区分大小写') + '</span><span class="p-row" style="gap:5px">已启用 ' + readonlyToggle() + '</span></div></article>';
const draftBody = '<section class="p-card admin-draft-overview"><span class="admin-draft-symbol">' + icon('edit') + '</span><div><div class="admin-draft-title">修改退款申请 <span class="method m-POST">POST</span></div><div class="admin-draft-sub mono">/api/pay/refund · api_refund_002</div></div><div class="admin-draft-meta"><div><small>所属团队</small><b>支付中心 PAY</b></div><div><small>变更范围</small><b>返回体 1 → 2 个</b></div><div><small>写入策略</small><b>草稿 + Web 确认</b></div></div></section><div class="admin-layout admin-layout--draft"><div class="admin-left"><section class="p-card admin-draft-responses"><div class="p-between"><div><h2 class="p-title">修改后的返回体</h2><p class="p-note">按条件匹配 · 已保留一个无条件默认响应</p></div>' + badge('2 个启用', 'blue') + '</div><div class="admin-response-grid">' + responseCard(1, '默认响应', 200, '无条件 · 保留原默认返回体', 0, defaultResponse, true) + responseCard(2, '上游超时 · 2s', 504, 'Header · X-Scenario = timeout', 2000, timeoutResponse) + '</div></section><section class="p-card admin-draft-diff"><div class="p-between"><h2 class="p-title">修改前后</h2><span class="admin-minor">新增 1 个返回体 · 不改变路径与默认响应</span></div><div class="admin-draft-diff-grid"><div><div class="admin-diff-label">修改前 BEFORE</div><div class="admin-diff-stack"><span class="admin-diff-line admin-diff-line--old">− responses: [ 默认响应 ]</span><span class="admin-diff-line">  默认: 200 / 0 ms / 无条件</span></div></div><div><div class="admin-diff-label">修改后 AFTER</div><div class="admin-diff-stack"><span class="admin-diff-line admin-diff-line--new">+ responses: [ 默认响应, 上游超时 ]</span><span class="admin-diff-line admin-diff-line--new">+ 504 / 2000 ms / X-Scenario=timeout</span></div></div></div></section>' + note('演示草稿：生成内容不会自动保存；“确认应用”仅切换本地状态，不会写入线上。') + '</div><aside class="p-card admin-panel admin-review"><div class="p-between"><span class="p-eyebrow" style="margin:0">MCP DRAFT REVIEW</span><span id="draft-status" class="p-badge p-badge--blue">待确认</span></div><div><h2 id="draft-state-title" class="admin-h2">请先检查修改内容</h2><p class="admin-status-copy" id="draft-state-copy">确认后才会更新接口；服务端会重新校验草稿归属、有效期与团队权限。</p></div><div>' + kv('发起者', '张一鸣 · 当前登录用户') + kv('来源', 'Cursor · MCP Token') + kv('草稿编号', '<span class="mono">draft_a4f72c</span>') + kv('创建时间', '<span class="mono">2026-10-28 16:42</span>') + kv('有效至', '<span class="mono">2026-10-28 17:12</span>') + '</div><div class="admin-soft admin-soft--blue"><div class="admin-review-check">' + icon('check') + '仅草稿本人或超级管理员可访问</div><div class="admin-review-check" style="margin-top:7px">' + icon('check') + '应用时重新校验支付中心访问权限</div><div class="admin-review-check" style="margin-top:7px">' + icon('check') + '真实写入须记录到操作日志</div></div><div id="draft-pending-actions" class="admin-actions">' + action('确认应用修改', 'data-draft-state="CONFIRMED"', { primary: true, icon: 'check' }) + action('取消草稿', 'data-draft-state="CANCELLED"') + '</div><div id="draft-complete-link" hidden>' + btn('查看接口编辑稿', { primary: true, href: '02-api-edit.html', icon: 'arrow' }) + '</div><div class="admin-state-switch"><span>演示状态</span>' + select('切换草稿演示状态', [['PENDING', '待确认'], ['CONFIRMED', '已应用'], ['CANCELLED', '已取消'], ['EXPIRED', '已过期']], 'PENDING', 'id="draft-state-preview"') + '</div><div class="admin-minor">时间为固定演示快照；有效期不实时倒数。<br>本页不连接真实 MCP Server。</div></aside></div>' + interactions;

const pages = [
  { file: '10-teams.html', title: '团队管理', active: '团队管理', badge: '4 个团队', subtitle: '组织接口与成员 · 超级管理员工作区 · 静态演示', actions: action('新建团队', 'data-open="new-team-dialog"', { primary: true, icon: 'add' }), body: teamBody },
  { file: '11-users.html', title: '用户管理', active: '用户管理', badge: '26 位用户', subtitle: '管理账号、全局角色与团队分配 · 仅超级管理员 · 静态演示', actions: action('新建用户', 'data-open="new-user-dialog"', { primary: true, icon: 'add' }), body: userBody },
  { file: '12-operation-logs.html', title: '操作日志', active: '请求日志', badge: '支付中心', subtitle: '当前团队 PAY · 记录谁在何时修改了什么 · 2026-10-28 演示快照', actions: demo('刷新记录', '当前为支付中心操作日志演示快照，不请求真实日志', { icon: 'refresh' }), body: logBody },
  { file: '13-mcp-confirm.html', title: 'AI 草稿确认', active: '接口管理', badge: 'MCP · 修改草稿', subtitle: '审核 AI 生成的修改，确认前不会写入接口 · 静态演示', actions: btn('返回接口管理', { href: '01-api-list.html', icon: 'arrow' }), body: draftBody }
];
for (const page of pages) console.log(renderPage({ ...page, styles }));
