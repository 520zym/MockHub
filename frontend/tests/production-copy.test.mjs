import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { parse } from '@vue/compiler-sfc'
const template = async path => parse(await readFile(new URL('../src/' + path, import.meta.url), 'utf8')).descriptor.template.content

test('API table uses flat named columns, without stacked audit metadata or role teaching', async () => {
  const source = await template('views/ApiList.vue'), table = source.slice(source.indexOf('<table class="api-grid"'), source.indexOf('</table>'))
  for (const label of ['类型', '方法', '路径', '名称', '分组', '标签', '命中次数', '最近调用', '修改时间', '创建人', '启用', '操作']) assert.ok(table.includes('>' + label + '</th>'), label)
  for (const removed of ['api-subtitle', 'revision-chip', 'updatedByName', 'workspace-scope']) assert.ok(!source.includes(removed), removed)
  assert.ok(source.includes('api-grid-scroll'))
})

test('response editor has explicit section names, not numbered process metaphors', async () => {
  const body = await template('components/ResponseTabs.vue'), rules = await template('components/match/ConditionPanel.vue')
  assert.ok(body.includes('<h4>返回内容</h4>')); assert.ok(rules.includes('<strong>匹配条件</strong>'))
  for (const source of [body, rules]) for (const removed of ['body-section-step', 'section-step', '何时返回', '返回什么']) assert.ok(!source.includes(removed), removed)
  assert.ok(body.includes('aria-pressed')); assert.ok(rules.includes('<MatchGuide'))
})

test('routine page headers do not teach permissions or advertise implementation boundaries', async () => {
  const checks = {
    'views/ApiList.vue': ['创建人用于追踪，不限制团队协作', '仅展示当前团队数据'],
    'views/ApiEdit.vue': ['API WORKSPACE', '修改属于团队共享配置', '写清接口背景、字段约定和负责人', '路径只包含路由；按 Query'],
    'views/TeamManage.vue': ['系统管理与团队成员身份独立'],
    'views/UserManage.vue': ['两层权限，独立授权', '角色不混用'],
    'views/TeamSettings.vue': ['本团队权限边界', '成员共享团队资源'],
    'views/Account.vue': ['MY ACCOUNT', '团队管理员不等于超级管理员'],
    'views/AccountTokens.vue': ['每次调用 ='],
    'views/TeamVariables.vue': ['可编辑成员可维护内容，只读成员仅查看'],
    'views/Stats.vue': ['查看 Mock 接口的调用情况，识别活跃和僵尸接口'],
    'views/Settings.vue': ['AI 始终继承当前登录用户的权限，团队数据保持隔离'],
    'components/layout/Sidebar.vue': ['团队隔离 · 服务端授权']
  }
  for (const [file, removed] of Object.entries(checks)) { const source = await template(file); for (const text of removed) assert.ok(!source.includes(text), file + ': ' + text) }
})

test('safety and actionable state remain after removing page narration', async () => {
  assert.ok((await template('views/McpConfirm.vue')).includes('SOAP 操作列表'))
  const trash = await template('views/ApiTrash.vue'); assert.ok(trash.includes('彻底删除')); assert.ok(trash.includes('所有写操作被冻结'))
  const account = await template('views/AccountTokens.vue'); assert.ok(account.includes('明文') || account.includes('一次'))
  assert.ok((await template('components/match/MatchGuide.vue')).includes('condition-flow'))
})
