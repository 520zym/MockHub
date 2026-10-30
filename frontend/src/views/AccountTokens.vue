<template>
  <div class="account-tokens">
    <header class="heading"><div><h1>个人访问凭证 <span class="badge">MCP</span></h1><p>绑定 {{ userStore.user?.displayName || userStore.user?.username || '本人' }} · 显式授权团队与能力，不与网页登录或 Mock Key 混用</p></div><button class="button" :disabled="loading || creating || revokingId" @click="reload">刷新</button></header>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div>
    <div class="token-layout">
      <section class="panel token-list"><div class="section-heading"><div><h2>我的 MCP 凭证</h2><p>仅显示本人凭证；名称、前缀与范围可查，明文无法取回。</p></div><span class="badge">{{ tokens.length }} 张</span></div><div class="list-scroll" :aria-busy="loading"><div v-if="loading" class="empty">正在加载个人凭证…</div><div v-else-if="!tokens.length" class="empty"><FilledIcon name="key" :size="32" /><h3>尚无个人访问凭证</h3><p>按需选择团队与最小能力，不会自动授予所有团队。</p></div><article v-for="token in loading ? [] : tokens" :key="token.id" class="token-card"><div class="token-heading"><FilledIcon name="key" :size="22" /><div class="token-title"><h3>{{ token.name }}</h3><span class="meta">{{ tokenStatus(token) }} · 本人 / {{ userStore.user?.username }}</span></div><button class="text-button danger" :disabled="creating || !!revokingId || token.revoked || token.legacy" @click="revoke(token)">{{ revokingId === token.id ? '撤销中…' : '撤销' }}</button></div><div class="scope-list"><div v-for="(scopes, id) in token.teamScopes || {}" :key="id" class="token-team"><code>{{ teamById(id)?.identifier || id }}</code><span v-for="scope in scopes" :key="scope" class="badge blue">{{ scopeOptions.find(s => s.value === scope)?.label || scope }}</span></div><span v-if="token.legacy" class="legacy-note">旧版无显式范围，已失效；请按当前权限重新签发。</span></div><div class="token-details"><span>前缀 <code>{{ token.tokenPrefix }}</code></span><span>到期 {{ formatTime(token.expiresAt) }}</span><span>最后使用 {{ token.lastUsedAt ? formatTime(token.lastUsedAt) : '从未' }}</span></div></article></div><footer class="protection"><FilledIcon name="shield" :size="17" /><span>每次调用 = 凭证按队范围 ∩ 本人当前团队能力 ∩ 实例工具 / 写入策略。超级管理员也不能越过凭证范围；角色变化、撤销和到期即时生效。</span></footer></section>
      <aside class="right-panels"><section class="panel"><h2>新建个人凭证</h2><p class="hint">只授权必需团队与能力；不提供永久凭证。</p><form class="create-form" @submit.prevent="create"><label>凭证名称 *<input v-model="form.name" required maxlength="60" :disabled="creating" placeholder="例如：桌面助手 · 支付只读" /></label><label>有效期（天） *<input v-model.number="form.expiresInDays" type="number" required min="1" max="365" step="1" :disabled="creating" /><small>1 至 365 天，到期后需重新签发。</small></label><label for="scope-team">显式选择团队 *</label><div class="add-team"><select id="scope-team" v-model="teamToAdd" :disabled="creating || !identityLoaded"><option value="">选择可访问的团队</option><option v-for="team in availableTeams" :key="team.id" :value="team.id">{{ team.identifier }} · {{ team.name }}{{ team.status === 'ARCHIVED' ? '（归档，只读）' : '' }}</option></select><button type="button" class="button" :disabled="creating || !teamToAdd || rows.length >= 100" @click="addTeam">添加</button></div><div class="scope-editor"><section v-for="(row, index) in rows" :key="row.teamId" class="scope-team"><div class="section-heading"><b>{{ teamById(row.teamId)?.name || '不可用团队' }} <code>{{ teamById(row.teamId)?.identifier || row.teamId }}</code></b><button type="button" class="text-button" :disabled="creating" @click="rows.splice(index, 1)">移除</button></div><label v-for="scope in scopeOptions" :key="scope.value" class="scope-check" :class="{ unavailable: !scopeAllowed(row.teamId, scope) }"><input v-model="row.scopes" type="checkbox" :value="scope.value" :disabled="creating || !scopeAllowed(row.teamId, scope)" /><span>{{ scope.label }}<small>{{ scope.description }}</small></span></label></section><p v-if="!rows.length" class="empty-scopes">没有隐式默认团队。请至少添加一队，逐队选择能力。</p></div><div v-if="createError" class="notice error" role="alert">{{ createError }}</div><button class="button primary" type="submit" :disabled="creating || !!revokingId || !identityLoaded || !rows.length || !!plainToken">{{ creating ? '创建中…' : '创建并仅显示一次' }}</button></form></section><section class="panel boundary"><h2>安全使用</h2><ul><li>只读成员不能取得写入能力；各团队能力分别选择，不会把一队的写权限复制给另一队。</li><li>草稿不等于自动应用。自动应用另需来源快照授权、实例 DIRECT 策略及工具开关。</li><li>撤销来源凭证后，关联草稿也不可从网页确认。网页退出不默认撤销 MCP。</li></ul><div class="endpoint"><span>Streamable HTTP · POST</span><code>{{ endpoint }}</code><button class="text-button" @click="copyExample">复制接入示例（不含密钥）</button></div><p class="hint">实例工具与写策略由超级管理员管理；个人凭证不能绕过。</p></section></aside>
    </div>
    <el-dialog :model-value="!!plainToken" title="个人凭证已创建 · 仅显示一次" width="min(600px, 94vw)" class="personal-token-dialog" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false" destroy-on-close><div class="notice">请立即安全保存。关闭后无法取回明文；服务器仅保存哈希，不将明文写入列表或日志。</div><div class="plain-token"><code aria-label="新凭证明文">{{ plainToken }}</code><button class="button" @click="copySecret">复制凭证</button></div><p class="hint">{{ issuedName }} · 到期 {{ formatTime(issuedExpiry) }}</p><label class="acknowledge"><input v-model="acknowledged" type="checkbox" />我已安全保存，理解关闭后无法再次查看</label><template #footer><button class="button primary" :disabled="!acknowledged" @click="clearSecret">已保存并关闭</button></template></el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FilledIcon from '@/components/FilledIcon.vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getMcpTokens, createMcpToken, revokeMcpToken } from '@/api/mcp'
const userStore = useUserStore(), appStore = useAppStore()
const tokens = ref([]), loading = ref(false), identityLoaded = ref(false), creating = ref(false), revokingId = ref(null), error = ref(''), createError = ref('')
const form = reactive({ name: '', expiresInDays: 30 }), rows = ref([]), teamToAdd = ref('')
const plainToken = ref(''), issuedName = ref(''), issuedExpiry = ref(''), acknowledged = ref(false), now = ref(Date.now())
const scopeOptions = [
  { value: 'API_READ', label: '接口读取', write: false, description: '检索接口与返回体配置' },
  { value: 'STATS_READ', label: '统计读取', write: false, description: '当前团队调用总览与 Top 接口' },
  { value: 'AI_GENERATE', label: 'AI 生成 / 解释', write: false, description: '生成响应、条件与命中解释，不直接落库' },
  { value: 'DRAFT_CREATE', label: '创建接口草稿', write: true, description: '仅在本团队拟定新接口' },
  { value: 'DRAFT_UPDATE', label: '修改接口草稿', write: true, description: '仅在本团队拟定接口变更' },
  { value: 'DRAFT_APPLY', label: '应用来源草稿', write: true, description: '另需实例 DIRECT 策略及工具开关' }
]
const teamById = id => appStore.teams.find(t => t.id === id)
const availableTeams = computed(() => appStore.teams.filter(t => userStore.hasCapability(t.id, 'read') && !rows.value.some(r => r.teamId === t.id)))
const endpoint = computed(() => typeof window === 'undefined' ? '/mcp' : window.location.origin + '/mcp')
const scopeAllowed = (id, scope) => !!teamById(id) && userStore.hasCapability(id, scope.write ? 'write' : 'read') && (!scope.write || teamById(id).status !== 'ARCHIVED')
const message = err => err?.msg || err?.response?.data?.msg || err?.message || '请求失败，请重试'
function formatTime(value) { if (!value) return '旧版无到期'; const date = new Date(value); return Number.isNaN(date.getTime()) ? '无效时间' : date.toLocaleString() }
function tokenStatus(token) { if (token.legacy) return '旧版已失效'; if (token.revoked) return '已撤销'; const expiry = Date.parse(token.expiresAt); if (!Number.isFinite(expiry)) return '到期异常'; return expiry <= now.value ? '已过期' : '生效中' }
let loadRevision = 0, clock
async function loadTokens() { const revision = ++loadRevision; try { const data = await getMcpTokens(); if (revision === loadRevision) tokens.value = data || [] } catch (err) { if (revision === loadRevision) error.value = message(err) } }
async function reload() {
  if (loading.value) return
  loading.value = true; error.value = ''; identityLoaded.value = false
  try { await userStore.refreshIdentity(); await appStore.loadTeams(); identityLoaded.value = true; await loadTokens() }
  catch (err) { error.value = message(err) }
  finally { loading.value = false; now.value = Date.now() }
}
function addTeam() { if (creating.value || !identityLoaded.value || rows.value.length >= 100 || !availableTeams.value.some(t => t.id === teamToAdd.value)) return; rows.value.push({ teamId: teamToAdd.value, scopes: ['API_READ'] }); teamToAdd.value = ''; createError.value = '' }
function payload() {
  const name = form.name.trim(), days = Number(form.expiresInDays)
  if (!name || name.length > 60 || !Number.isInteger(days) || days < 1 || days > 365) throw new Error('请填写名称与1至365天整数有效期。')
  if (!rows.value.length || rows.value.length > 100) throw new Error('请显式选择1至100个团队。')
  const ids = new Set(), union = new Set(), teamScopes = Object.create(null)
  for (const row of rows.value) {
    if (ids.has(row.teamId) || !row.scopes.length) throw new Error('团队不能重复，且每队至少选择一项能力。')
    ids.add(row.teamId); const seen = new Set()
    for (const value of row.scopes) { const scope = scopeOptions.find(s => s.value === value); if (!scope || seen.has(value) || !scopeAllowed(row.teamId, scope)) throw new Error('当前权限已变化或含不可授予的能力，请检查选择并刷新。'); seen.add(value); union.add(value) }
    teamScopes[row.teamId] = [...seen]
  }
  return { name, expiresInDays: days, teamIds: [...ids], scopes: [...union], teamScopes }
}
async function create() {
  if (creating.value || revokingId.value || !identityLoaded.value || plainToken.value) return
  let data; createError.value = ''; try { data = payload() } catch (err) { createError.value = message(err); return }
  creating.value = true
  try { const issued = await createMcpToken(data); if (!issued?.token) throw new Error('未返回凭证明文，请刷新列表核实并撤销后重新签发。'); plainToken.value = issued.token; issuedName.value = issued.name; issuedExpiry.value = issued.expiresAt; acknowledged.value = false; form.name = ''; rows.value = []; teamToAdd.value = ''; ElMessage.success('凭证已创建，请立即安全保存'); await loadTokens() }
  catch (err) { createError.value = message(err) }
  finally { creating.value = false }
}
async function revoke(token) {
  if (creating.value || revokingId.value || token.revoked || token.legacy) return
  try { await ElMessageBox.confirm('撤销「' + token.name + '」将立即中断此凭证的客户端，关联 MCP 草稿也不能从网页确认。仅影响本人的这张凭证，不影响其他凭证或网页登录。', '撤销个人凭证', { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '取消' }) } catch { return }
  if (creating.value || revokingId.value) return
  revokingId.value = token.id; error.value = ''
  try { await revokeMcpToken(token.id); ElMessage.success('凭证已撤销，关联草稿不能再确认'); await loadTokens() }
  catch (err) { error.value = message(err) }
  finally { revokingId.value = null }
}
async function copySecret() { if (!plainToken.value) return; try { await navigator.clipboard.writeText(plainToken.value); ElMessage.success('凭证已复制，请安全保存') } catch { ElMessage.warning('复制失败，请手动选择凭证明文保存') } }
async function copyExample() { const config = { mcpServers: { mockhub: { type: 'http', url: endpoint.value, headers: { Authorization: 'Bearer <你的个人凭证>' } } } }; try { await navigator.clipboard.writeText(JSON.stringify(config, null, 2)); ElMessage.success('已复制不含密钥的示例') } catch { ElMessage.warning('复制失败，请手动填写 MCP 地址与 Bearer 凭证') } }
function clearSecret() { plainToken.value = ''; issuedName.value = ''; issuedExpiry.value = ''; acknowledged.value = false }
onBeforeRouteLeave(async () => { if (creating.value || revokingId.value) { ElMessage.warning('凭证操作正在进行，请完成后再离开'); return false } if (!plainToken.value) return true; try { await ElMessageBox.confirm('离开后无法再次查看凭证明文，请确认已安全保存。丢失后只能撤销重发。', '关闭一次性凭证', { confirmButtonText: '已保存，离开', cancelButtonText: '返回保存' }); clearSecret(); return true } catch { return false } })
onMounted(() => { reload(); clock = setInterval(() => { now.value = Date.now() }, 30000) })
onBeforeUnmount(() => { ++loadRevision; clearInterval(clock); clearSecret() })
</script>

<style scoped>
.account-tokens{height:100%;min-height:0;display:flex;flex-direction:column;gap:16px;color:var(--app-text-primary);font-size:13px}
.heading,.section-heading,.token-heading,.add-team{display:flex;align-items:center;gap:12px}
.heading,.section-heading{justify-content:space-between}
.heading h1{font-size:26px;letter-spacing:-.7px;font-weight:650;margin:0}
.heading p,.section-heading p,.hint{color:var(--app-text-secondary);font-size:12px;line-height:1.8;margin:8px 0 0}
.badge{display:inline-flex;padding:4px 8px;border-radius:7px;background:var(--app-bg-control);color:var(--app-text-regular);font-size:12px;font-weight:400;line-height:1.5;vertical-align:middle}
.blue{background:var(--app-bg-selected);color:var(--app-text-accent)}
.token-layout{display:grid;grid-template-columns:minmax(0,1fr) 365px;gap:16px;flex:1;min-height:0;overflow:hidden}
.panel{padding:20px;background:var(--app-bg-surface);border-radius:16px;min-width:0}
.token-list{display:flex;flex-direction:column;min-height:0}
h2{font-size:15px;font-weight:600;margin:0}
.list-scroll{overflow:auto;min-height:150px;flex:1;margin-top:16px}
.token-card{padding:20px 0;border-top:1px solid var(--app-bg-hover)}
.token-heading>.filled-icon{color:var(--app-text-secondary);flex:none}
.token-title{flex:1;min-width:0}
.token-title h3{font-size:14px;font-weight:500;margin:0 0 6px;overflow-wrap:anywhere}
.meta{font-size:12px;color:var(--app-text-secondary)}
.scope-list{display:grid;gap:8px;margin:16px 0}
.token-team{display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.token-team>code{background:var(--app-bg-control);padding:6px 8px;border-radius:6px;color:var(--app-text-regular)}
.token-details{display:flex;gap:16px;flex-wrap:wrap;color:var(--app-text-secondary);font-size:12px;line-height:1.8}
code{font:12px/1.6 var(--app-font-mono, ui-monospace, monospace)}
.legacy-note{font-size:12px;color:var(--app-text-warning)}
.protection{display:flex;align-items:flex-start;gap:10px;padding-top:16px;color:var(--app-text-secondary);font-size:12px;line-height:1.8}
.right-panels{display:flex;flex-direction:column;gap:16px;overflow:auto;min-height:0}
.create-form{display:grid;gap:16px;margin-top:20px}
.create-form>label{display:flex;flex-direction:column;gap:8px;font-size:13px;color:var(--app-text-regular)}
.create-form small{font-size:12px;color:var(--app-text-secondary);line-height:1.7}
.scope-editor{display:flex;flex-direction:column;gap:16px;max-height:410px;overflow:auto}
.scope-team{padding:12px;background:var(--app-bg-control);border-radius:10px}
.scope-team .section-heading{font-size:13px;margin-bottom:12px}
.scope-team b{font-weight:500;color:var(--app-text-regular)}
.scope-check{display:flex;align-items:flex-start;gap:8px;font-size:13px;margin:12px 0;cursor:pointer;color:var(--app-text-regular)}
.scope-check input{width:14px;margin:2px 0 0;accent-color:var(--app-accent)}
.scope-check small{display:block;font-size:12px;color:var(--app-text-secondary);margin-top:4px;line-height:1.5}
.scope-check.unavailable{color:var(--app-text-muted);cursor:not-allowed}
.empty-scopes{font-size:12px;color:var(--app-text-secondary);line-height:1.8;margin:0;padding:12px;background:var(--app-bg-control);border-radius:9px}
.boundary ul{padding-left:17px;display:grid;gap:12px;color:var(--app-text-regular);font-size:13px;line-height:1.85;margin:20px 0}
.boundary li::marker{color:var(--app-accent)}
.endpoint{padding:12px;background:var(--app-bg-control);border-radius:9px;display:grid;gap:10px;color:var(--app-text-secondary);font-size:12px}
.endpoint code{color:var(--app-text-regular);overflow-wrap:anywhere}
.notice{padding:12px 14px;border-radius:10px;background:var(--app-bg-selected);color:var(--app-text-accent);line-height:1.8;font-size:13px}
.notice.error{background:var(--el-color-danger-light-9);color:var(--app-text-danger)}
.button{padding:10px 16px;border:0;border-radius:9px;background:var(--app-bg-control);color:var(--app-text-regular);font:inherit;font-size:13px;cursor:pointer;white-space:nowrap}
.button.primary{background:var(--app-primary);color:var(--app-text-inverse)}
.button:hover:not(:disabled){background:var(--app-bg-hover)}
.button.primary:hover:not(:disabled){background:var(--app-primary-hover)}
.button:disabled,.text-button:disabled{opacity:.45;cursor:not-allowed}
.text-button{display:inline-flex;align-items:center;justify-content:center;min-height:28px;padding:4px 6px;border:0;background:none;color:var(--app-text-accent);font:inherit;font-size:13px;cursor:pointer;white-space:nowrap}
.danger{color:var(--app-text-danger)}
input,select{box-sizing:border-box;width:100%;padding:10px 12px;border:1px solid transparent;border-radius:8px;background:var(--app-bg-control);color:var(--app-text-regular);outline:none;font:inherit;font-size:13px}
input:focus,select:focus{border-color:var(--app-accent)}
.add-team select{min-width:0}
.empty{min-height:220px;display:grid;place-content:center;justify-items:center;text-align:center;gap:12px;color:var(--app-text-secondary);font-size:13px}
.empty h3{margin:0;color:var(--app-text-primary);font-size:15px;font-weight:500}
.empty p{margin:0;max-width:350px;line-height:1.8}
.plain-token{display:flex;align-items:center;gap:12px;margin-top:20px;padding:16px;border-radius:10px;background:var(--app-bg-control)}
.plain-token code{flex:1;min-width:0;overflow-wrap:anywhere;line-height:1.9;color:var(--app-text-primary)}
.acknowledge{display:flex;align-items:flex-start;gap:8px;font-size:13px;color:var(--app-text-regular);margin-top:20px}
.acknowledge input{width:15px;margin-top:3px}
:global(.personal-token-dialog){box-shadow:none!important;border-radius:16px}
@media(max-width:1100px){.token-layout{grid-template-columns:minmax(0,1fr) 320px}}
@media(max-width:850px){.account-tokens{height:auto;min-height:100%}.token-layout{display:flex;flex-direction:column;overflow:visible}.token-list{min-height:330px}.list-scroll{max-height:520px}.right-panels{overflow:visible;display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr))}.heading h1{font-size:22px}.scope-editor{max-height:none}}
@media(max-width:540px){.heading{align-items:flex-start}.panel{padding:16px}.plain-token{align-items:flex-start;flex-direction:column}}
</style>
