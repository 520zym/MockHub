<template>
  <div v-if="aiStore.enabled && aiStore.visible && canRead" class="ai-assistant">
    <transition name="ai-pop">
      <section v-if="open" class="ai-chat" role="dialog" aria-label="MockHub 助手" @keydown.esc.stop.prevent="open = false">
        <header class="ai-chat__head">
          <div class="ai-chat__avatar">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="ai-chat__title-wrap">
            <div class="ai-chat__title">
              MockHub 助手
              <span class="ai-beta">Beta</span>
            </div>
            <div class="ai-chat__sub">
              <span class="ai-dot" :class="{ 'ai-dot--muted': !canUseAssistant }"></span>
              {{ canUseAssistant ? (aiStore.provider === 'MOCK' ? '在线 · 内置演示模型' : '在线 · ' + (aiStore.status?.model || 'AI')) : (aiStore.reason || '未就绪') }}
            </div>
          </div>
          <div class="ai-chat__actions">
            <el-button text circle size="small" title="会话历史" :disabled="!canUseAssistant || loading" @click="openHistory">
              <el-icon><Clock /></el-icon>
            </el-button>
            <el-button text circle size="small" title="新会话" @click="clearChat">
              <el-icon><Plus /></el-icon>
            </el-button>
            <el-button text circle size="small" title="收起" @click="open = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </header>

        <div v-if="historyOpen" class="ai-history">
          <div class="ai-history__head">
            <span>会话历史</span>
            <el-button text circle size="small" @click="historyOpen = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <div class="ai-history__list" v-loading="historyLoading">
            <div v-if="historyError" class="ai-history__empty ai-card-error" role="alert">{{ historyError }} <el-button link @click="openHistory">重试</el-button></div>
            <div v-else-if="!historyLoading && !conversations.length" class="ai-history__empty">当前团队还没有历史会话，先聊一句吧。</div>
            <button
              v-for="conv in conversations"
              :key="conv.id"
              type="button"
              class="ai-history__item"
              :class="{ 'ai-history__item--on': conv.id === conversationId }"
              @click="loadConversation(conv.id)"
            >
              <div class="ai-history__title">{{ conv.title || '未命名会话' }}</div>
              <div class="ai-history__preview">{{ conv.preview || '暂无消息' }}</div>
              <div class="ai-history__meta">{{ conv.messageCount || 0 }} 条 · {{ formatHistoryTime(conv.updatedAt) }}</div>
            </button>
          </div>
        </div>

        <div class="ai-chat__ctx">
          <span class="ctx-chip">范围：{{ teamName || '未选择团队' }}</span>
          <span class="ctx-chip"><el-icon><Lock /></el-icon>{{ canWrite ? '可编辑' : '只读' }}</span>
        </div>

        <div ref="bodyRef" class="ai-chat__body">
          <div v-if="!canUseAssistant" class="ai-unavailable">
            <div class="ai-welcome__icon">
              <el-icon><MagicStick /></el-icon>
            </div>
            <div class="ai-welcome__title">AI 助手尚未就绪</div>
            <div class="ai-welcome__desc">{{ aiStore.reason || '当前账号未启用对话助手能力，请联系管理员确认配置。' }}</div>
            <el-button v-if="userStore.isSuperAdmin" type="primary" plain @click="goSettings">
              去配置
            </el-button>
            <div v-else class="ai-unavailable__hint">请联系超级管理员完成配置</div>
          </div>

          <div v-else-if="messages.length === 0" class="ai-welcome">
            <div class="ai-welcome__icon">
              <el-icon><MagicStick /></el-icon>
            </div>
            <div class="ai-welcome__title">有什么可以帮你？</div>
            <div class="ai-welcome__prompts">
              <button
                v-for="prompt in quickPrompts"
                :key="prompt"
                type="button" class="ai-prompt"
                :disabled="loading || historyLoading || !canUseAssistant" @click="send(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </div>

          <div v-for="(msg, idx) in messages" v-if="canUseAssistant" :key="idx" class="ai-msg">
            <template v-if="msg.role === 'user'">
              <div class="ai-msg--user">
                <div class="ai-bubble-user">{{ msg.text }}</div>
              </div>
            </template>
            <template v-else>
              <div class="ai-msg__avatar">
                <el-icon><MagicStick /></el-icon>
              </div>
              <div class="ai-msg__stack">
                <div class="ai-bubble" :class="{ 'ai-bubble--error': msg.error }">{{ msg.reply }}</div>

                <div v-for="(card, cardIdx) in (msg.cards || [])" :key="cardIdx" class="ai-card">
                  <!-- 搜索结果 -->
                  <template v-if="card.type === 'API_LIST'">
                    <div class="ai-card__head">
                      <el-icon><Search /></el-icon>
                      {{ card.title }}
                      <span class="ai-card__right">{{ card.keyword }}</span>
                    </div>
                    <div class="ai-card__body ai-card__body--list">
                      <button
                        v-for="item in card.items"
                        :key="item.apiId"
                        type="button" class="ai-api-row"
                        @click="openApi(item.apiId)"
                      >
                        <span class="ai-method" :class="'ai-method--' + (item.method || 'GET').toLowerCase()">
                          {{ item.method }}
                        </span>
                        <span class="ai-api-path">{{ item.path }}</span>
                        <span class="ai-api-name">{{ item.name }}</span>
                      </button>
                    </div>
                  </template>

                  <!-- 统计 -->
                  <template v-else-if="card.type === 'STATS'">
                    <div class="ai-card__head">
                      <el-icon><DataAnalysis /></el-icon>
                      {{ card.title }}
                      <span class="ai-card__right">只读</span>
                    </div>
                    <div class="ai-card__body">
                      <div class="ai-stats-total">
                        <span class="ai-stats-num">{{ card.total }}</span>
                        <span class="ai-stats-label">次 · 近 7 天</span>
                      </div>
                      <div v-if="card.items.length" class="ai-stat-rows">
                        <div v-for="(item, i) in card.items" :key="i" class="ai-stat-row">
                          <span class="ai-stat-idx" :class="{ 'ai-stat-idx--top': i === 0 }">{{ i + 1 }}</span>
                          <span class="ai-stat-path">{{ item.method }} {{ item.path }}</span>
                          <span class="ai-stat-count">{{ item.count }}</span>
                        </div>
                      </div>
                      <div v-else class="ai-card__empty">暂无调用记录</div>
                    </div>
                  </template>

                  <!-- 待确认操作 -->
                  <template v-else-if="card.type === 'PENDING_ACTION'">
                    <div
                      class="ai-confirm__head"
                      :class="{
                        'ai-confirm__head--done': card.status === 'CONFIRMED',
                        'ai-confirm__head--muted': card.status === 'CANCELLED' || card.status === 'EXPIRED'
                      }"
                    >
                      <el-icon>
                        <Check v-if="card.status === 'CONFIRMED'" />
                        <EditPen v-else />
                      </el-icon>
                      <template v-if="card.status === 'CONFIRMED'">{{ card.actionType === 'UPDATE_API' ? '已更新' : '已创建' }} · {{ card.summary.method }} {{ card.summary.path }}</template>
                      <template v-else-if="card.status === 'CANCELLED'">已取消 · {{ card.summary.method }} {{ card.summary.path }}</template>
                      <template v-else-if="card.status === 'EXPIRED' || cardExpired(card)">已过期 · 草稿已失效</template>
                      <template v-else>待确认操作 · {{ card.actionType === 'UPDATE_API' ? '修改接口' : '创建接口' }}</template>
                      <span v-if="card.status === 'PENDING' && !cardExpired(card)" class="ai-countdown">
                        <el-icon><Clock /></el-icon>
                        {{ expiryText(card) }}
                      </span>
                    </div>
                    <div class="ai-confirm__body">
                      <div class="ai-confirm__grid">
                        <div>
                          <div class="ai-confirm__k">方法 / 路径</div>
                          <div class="ai-confirm__v">
                            <code>{{ card.summary.method }}</code>
                            <code>{{ card.summary.path }}</code>
                          </div>
                        </div>
                        <div>
                          <div class="ai-confirm__k">名称 / 团队</div>
                          <div class="ai-confirm__v">{{ card.summary.name }} · {{ teamLabel(card.summary.teamId) }}</div>
                        </div>
                      </div>
                      <div class="ai-confirm__k" style="margin: 10px 0 6px">{{ card.actionType === 'UPDATE_API' ? '修改后预览' : '响应体预览' }}</div>
                      <div class="ai-confirm__preview-wrap">
                        <pre class="ai-confirm__preview">{{ card.preview || '（无预览）' }}</pre>
                        <button v-if="isLongPreview(card)" type="button" class="ai-confirm__expand" @click="openPreview(card)">
                          展开完整内容
                        </button>
                      </div>
                      <div class="ai-confirm__note">
                        <el-icon><Lock /></el-icon>
                        {{ canWrite ? '确认后写入接口。' : '当前身份只读，不能确认写入。' }}
                      </div>
                      <p v-if="card.error" class="ai-card-error" role="alert">{{ card.error }}</p>
                      <div class="ai-confirm__actions">
                        <template v-if="card.status === 'PENDING' && !cardExpired(card)">
                          <el-button text size="small" :disabled="!canCancelCard(card)" @click="cancelCard(card)">取消</el-button>
                          <el-button size="small" type="primary" :loading="card.busy" :disabled="!canConfirmCard(card)" @click="confirmCard(card)">{{ card.actionType === 'UPDATE_API' ? '确认修改' : '确认创建' }}</el-button>
                        </template>
                        <template v-else-if="card.status === 'CONFIRMED' && card.apiId">
                          <el-button size="small" type="primary" plain @click="openApi(card.apiId)">
                            查看接口
                          </el-button>
                        </template>
                        <span v-else class="ai-confirm__muted">草稿不可再操作</span>
                      </div>
                    </div>
                  </template>
                </div>

                <div v-if="msg.provider" class="ai-msg__meta">
                  由 {{ msg.provider === 'MOCK' ? '内置演示模型' : (msg.model || 'AI') }} 生成
                </div>
              </div>
            </template>
          </div>

          <div v-if="canUseAssistant && loading" class="ai-msg">
            <div class="ai-msg__avatar">
              <el-icon><MagicStick /></el-icon>
            </div>
            <div class="ai-bubble ai-bubble--typing">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>

        <div v-if="canUseAssistant" class="ai-composer">
          <div class="ai-composer__box" :class="{ 'ai-composer__box--focus': composerFocus }" @click="focusComposer">
            <el-input
              ref="composerInput"
              v-model="input"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 5 }"
              resize="none"
              :placeholder="canWrite ? '继续追问，或描述要创建的接口…' : '询问当前团队的接口或匹配逻辑…'"
              :disabled="!canUseAssistant || loading || historyLoading"
              @focus="composerFocus = true"
              @blur="composerFocus = false"
              @keydown.enter.exact.prevent="send()"
            />
            <div class="ai-composer__foot">
              <span class="ai-composer__scope">
                <el-icon><OfficeBuilding /></el-icon>
                {{ teamName || '请先选择团队' }}
              </span>
              <el-button
                type="primary" aria-label="发送消息"
                circle
                size="small"
                :disabled="!canUseAssistant || loading || historyLoading || !input.trim()"
                @click="send()"
              >
                <el-icon><Promotion /></el-icon>
              </el-button>
            </div>
          </div>
          <div class="ai-composer__tip">
            <el-icon><Lock /></el-icon>
            {{ canWrite ? 'AI 生成内容需确认后生效，不会自动保存' : '只读问答不会创建或修改团队资源' }}
          </div>
        </div>
      </section>
    </transition>

    <el-dialog v-model="previewDialogVisible" :title="previewDialogTitle" width="680px" append-to-body>
      <pre class="ai-preview-full">{{ previewDialogContent }}</pre>
    </el-dialog>

    <button v-if="!open" type="button" class="ai-fab" title="MockHub AI 助手" @click="openChat">
      <el-icon><MagicStick /></el-icon>
      <span class="ai-fab__dot"></span>
    </button>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Close, Lock, Search, DataAnalysis, Plus, Check, EditPen, Clock, Promotion, OfficeBuilding } from '@element-plus/icons-vue'
import request from '@/api/request'
import { useAiStore } from '@/stores/ai'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const aiStore = useAiStore(), appStore = useAppStore(), userStore = useUserStore(), router = useRouter(), route = useRoute()
const teamId = computed(() => appStore.currentTeamId || null)
const teamName = computed(() => appStore.currentTeam?.name || '')
const canRead = computed(() => !!teamId.value && !!appStore.currentTeam && userStore.identityReady && appStore.currentTeam?.status !== 'DISABLED' && userStore.hasCapability(teamId.value, 'read'))
const canWrite = computed(() => canRead.value && appStore.currentTeam?.status !== 'ARCHIVED' && userStore.hasCapability(teamId.value, 'write'))
const canUseAssistant = computed(() => canRead.value && aiStore.enabled && aiStore.visible && aiStore.available)
const open = ref(false), input = ref(''), loading = ref(false), messages = ref([]), bodyRef = ref(null), composerFocus = ref(false), composerInput = ref(null)
const lastApiId = ref(''), conversationId = ref(''), historyOpen = ref(false), historyLoading = ref(false), historyError = ref(''), conversations = ref([])
const previewDialogVisible = ref(false), previewDialogContent = ref(''), previewDialogTitle = ref(''), now = ref(Date.now())
const quickPrompts = computed(() => canWrite.value ? ['最近 7 天哪个接口调用最多？', '找一下用户相关的接口', '帮我创建 GET /api/user/info 的 Mock，返回姓名、头像、等级'] : ['最近 7 天哪个接口调用最多？', '找一下用户相关的接口', '解释当前接口的匹配规则'])
let epoch = 0, disposed = false, clock = null
const pendingRequests = new Set()
function snapshot() { return { teamId: teamId.value, epoch, userId: userStore.user?.id } }
function current(ctx) { return !disposed && canUseAssistant.value && ctx.teamId === teamId.value && ctx.epoch === epoch && ctx.userId === userStore.user?.id }
function errorText(error, fallback) { return error?.response?.data?.msg || error?.msg || error?.message || fallback }
// The existing client still supplies authentication and response unwrapping. Local calls add
// AbortSignal without changing shared API wrappers or leaking context into another conversation.
async function call(method, url, data, ctx) {
  if (!current(ctx)) throw new Error('会话上下文已失效')
  const controller = new AbortController()
  pendingRequests.add(controller)
  try { return await request({ method, url, data, signal: controller.signal, timeout: 120000, skipGlobalNetworkError: true }) }
  finally { pendingRequests.delete(controller) }
}
function clearChat() {
  epoch++
  for (const controller of pendingRequests) controller.abort()
  pendingRequests.clear()
  messages.value = []; input.value = ''; conversationId.value = ''; lastApiId.value = ''; loading.value = false; composerFocus.value = false
  historyOpen.value = false; historyLoading.value = false; historyError.value = ''; conversations.value = []
  previewDialogVisible.value = false; previewDialogContent.value = ''; previewDialogTitle.value = ''
}
watch([teamId, canRead, canWrite, canUseAssistant, () => userStore.user?.id], () => { clearChat(); open.value = false }, { immediate: true, flush: 'sync' })
onMounted(() => { aiStore.load(); clock = window.setInterval(() => { now.value = Date.now() }, 30000) })
onBeforeUnmount(() => { disposed = true; clearChat(); window.clearInterval(clock) })
function goSettings() { if (!userStore.isSuperAdmin) return; open.value = false; router.push('/settings') }
function focusComposer() { if (canUseAssistant.value && !loading.value && !historyLoading.value) composerInput.value?.focus() }
function openChat() { if (!canRead.value || !aiStore.enabled || !aiStore.visible) return; open.value = true; scrollToBottom(); nextTick(() => { if (open.value && canUseAssistant.value) focusComposer() }) }
function normalizeCards(cards, ctx) {
  return (Array.isArray(cards) ? cards : []).filter(Boolean).flatMap(raw => {
    if (raw.type === 'PENDING_ACTION') {
      if (raw.summary?.teamId !== ctx.teamId) return []
      return [{ ...raw, summary: { ...raw.summary }, busy: false, error: '' }]
    }
    if (raw.type === 'API_LIST') return [{ ...raw, items: (raw.items || []).filter(item => !item.teamId || item.teamId === ctx.teamId) }]
    if (raw.type === 'STATS') return [{ ...raw, items: raw.items || [] }]
    return []
  })
}
async function openHistory() {
  if (!canUseAssistant.value || loading.value || historyLoading.value) return
  const ctx = snapshot()
  historyOpen.value = true; historyLoading.value = true; historyError.value = ''
  try {
    const data = await call('get', '/ai/conversations', undefined, ctx)
    if (current(ctx)) conversations.value = (Array.isArray(data) ? data : []).filter(item => item.teamId === ctx.teamId)
  } catch (error) { if (current(ctx)) { conversations.value = []; historyError.value = errorText(error, '会话历史加载失败。') } }
  finally { if (current(ctx)) historyLoading.value = false }
}
async function loadConversation(id) {
  if (!canUseAssistant.value || loading.value || historyLoading.value || !conversations.value.some(item => item.id === id && item.teamId === teamId.value)) return
  const list = conversations.value
  clearChat(); conversations.value = list; historyOpen.value = true; historyLoading.value = true
  const ctx = snapshot()
  try {
    const detail = await call('get', '/ai/conversations/' + encodeURIComponent(id), undefined, ctx)
    if (!current(ctx)) return
    if (detail.teamId !== ctx.teamId) throw new Error('该会话不属于当前团队，已停止加载。')
    conversationId.value = detail.id
    messages.value = (detail.messages || []).map(msg => msg.role === 'user' ? { role: 'user', text: msg.content || '' } : { role: 'assistant', reply: msg.content || '', cards: normalizeCards(msg.cards, ctx), provider: msg.provider, model: msg.model })
    // Reconcile pending history cards before allowing any execution.
    for (const msg of messages.value) for (const card of (msg.cards || [])) {
      if (!current(ctx)) return
      if (card.actionId && card.status === 'PENDING') {
        try {
          const action = await call('get', '/ai/actions/' + encodeURIComponent(card.actionId), undefined, ctx)
          if (!current(ctx)) return
          if ((action.summary?.teamId || action.teamId || ctx.teamId) !== ctx.teamId) { card.status = 'EXPIRED'; card.error = '操作上下文不匹配，不能执行。' }
          else { card.status = action.status; card.apiId = action.resultApiId || card.apiId }
        } catch (error) { if (!current(ctx)) return; if ([40401, 40901].includes(error?.code)) card.status = 'EXPIRED'; else card.error = '操作状态暂无法同步，执行时仍由服务端校验。' }
      }
      if (card.apiId) lastApiId.value = card.apiId
    }
    if (current(ctx)) { historyOpen.value = false; await scrollToBottom() }
  } catch (error) { if (current(ctx)) { messages.value = []; conversationId.value = ''; historyError.value = errorText(error, '会话加载失败。') } }
  finally { if (current(ctx)) historyLoading.value = false }
}
function formatHistoryTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}
async function resolveContextApi(ctx) {
  if (route.name === 'ApiEdit' && route.params?.id) {
    const id = String(route.params.id), api = await call('get', '/apis/' + encodeURIComponent(id), undefined, ctx)
    if (!current(ctx)) return ''
    if (api.teamId !== ctx.teamId) throw new Error('当前页面接口不属于所选团队，请先切换团队或返回接口列表。')
    return id
  }
  return lastApiId.value || ''
}
async function send(preset) {
  const content = (typeof preset === 'string' ? preset : input.value).trim()
  if (!content || !canUseAssistant.value || loading.value || historyLoading.value) return
  const ctx = snapshot()
  messages.value.push({ role: 'user', text: content }); input.value = ''; loading.value = true; scrollToBottom()
  try {
    const contextApiId = await resolveContextApi(ctx)
    if (!current(ctx)) return
    const history = messages.value.slice(0, -1).slice(-8).map(msg => ({ role: msg.role, content: msg.role === 'user' ? msg.text : (msg.reply || '') }))
    const result = await call('post', '/ai/chat', { teamId: ctx.teamId, message: content, conversationId: conversationId.value || undefined, contextApiId: contextApiId || undefined, history }, ctx)
    if (!current(ctx)) return
    conversationId.value = result.conversationId || conversationId.value
    messages.value.push({ role: 'assistant', reply: result.reply || '没有生成回复，请换个说法试试。', cards: normalizeCards(result.cards, ctx), provider: result.provider, model: result.model })
  } catch (error) { if (current(ctx)) messages.value.push({ role: 'assistant', reply: errorText(error, '请求失败，请稍后重试。'), cards: [], error: true }) }
  finally { if (current(ctx)) { loading.value = false; scrollToBottom() } }
}
function ownsCard(card) { return canUseAssistant.value && messages.value.some(msg => (msg.cards || []).includes(card)) && card.summary?.teamId === teamId.value }
function canCancelCard(card) { return ownsCard(card) && !!card.actionId && !card.busy && !historyLoading.value && card.status === 'PENDING' && !cardExpired(card) }
function canConfirmCard(card) { return canCancelCard(card) && canWrite.value && ['CREATE_API', 'UPDATE_API'].includes(card.actionType) }
async function confirmCard(card) {
  if (!canConfirmCard(card)) return
  const ctx = snapshot(), id = card.actionId
  card.busy = true; card.error = ''
  try {
    const result = await call('post', '/ai/actions/' + encodeURIComponent(id) + '/confirm', null, ctx)
    if (!current(ctx) || !canWrite.value || !ownsCard(card)) return
    card.status = 'CONFIRMED'; card.apiId = result.apiId; lastApiId.value = result.apiId || ''; appStore.bumpApiRevision()
    ElMessage.success(card.actionType === 'UPDATE_API' ? '接口已更新' : '接口已创建')
  } catch (error) { if (current(ctx) && ownsCard(card)) { card.error = errorText(error, '确认失败，请稍后重试。'); if (error?.code === 40901) card.status = 'EXPIRED' } }
  finally { if (current(ctx)) card.busy = false }
}
async function cancelCard(card) {
  if (!canCancelCard(card)) return
  const ctx = snapshot()
  card.busy = true; card.error = ''
  try { await call('post', '/ai/actions/' + encodeURIComponent(card.actionId) + '/cancel', null, ctx); if (current(ctx) && ownsCard(card)) { card.status = 'CANCELLED'; ElMessage.success('已取消') } }
  catch (error) { if (current(ctx) && ownsCard(card)) card.error = errorText(error, '取消失败，请稍后重试。') }
  finally { if (current(ctx)) card.busy = false }
}
function openApi(id) { if (!canRead.value || !id) return; lastApiId.value = id; open.value = false; router.push('/apis/' + encodeURIComponent(id) + '/edit') }
function isLongPreview(card) { const text = String(card.preview || ''); return text.length > 420 || text.split('\n').length > 12 }
function openPreview(card) { if (!ownsCard(card)) return; previewDialogContent.value = String(card.preview || ''); previewDialogTitle.value = card.actionType === 'UPDATE_API' ? '修改后完整内容' : '接口完整内容'; previewDialogVisible.value = true }
function cardExpired(card) { return !!card.expiresAt && new Date(card.expiresAt).getTime() <= now.value }
function expiryText(card) { if (!card.expiresAt) return ''; const date = new Date(card.expiresAt); return Number.isNaN(date.getTime()) ? '' : '有效至 ' + new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(date) }
function teamLabel(id) { return id === teamId.value ? teamName.value : '非当前团队' }
async function scrollToBottom() { await nextTick(); if (bodyRef.value && open.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight }
watch(messages, scrollToBottom, { deep: true })
</script>

<style scoped>
.ai-assistant { position: fixed; inset: 0; pointer-events: none; z-index: 2001; color: var(--app-text-primary); }
.ai-fab { pointer-events: auto; position: absolute; right: 40px; bottom: 40px; width: 56px; height: 56px; display: grid; place-items: center; border: 0; border-radius: 16px; background: var(--app-primary); color: #FFFFFF; font-size: 26px; cursor: pointer; box-shadow: 0 10px 24px rgba(23, 25, 29, .18), 0 2px 6px rgba(23, 25, 29, .12); animation: ai-fab-float 5s ease-in-out infinite; will-change: transform; }
.ai-fab:hover { background: var(--app-primary-hover); box-shadow: 0 14px 30px rgba(23, 25, 29, .24), 0 3px 8px rgba(23, 25, 29, .14); }
.ai-fab:focus-visible { outline: 2px solid var(--app-focus-color); outline-offset: 3px; }
.ai-fab__dot { position: absolute; top: 4px; right: 4px; width: 8px; height: 8px; border-radius: 50%; background: #FFFFFF; }
@keyframes ai-fab-float { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
.ai-chat { pointer-events: auto; position: absolute; right: 24px; bottom: 16px; width: min(460px, calc(100vw - 32px)); height: min(824px, calc(100vh - 32px)); height: min(824px, calc(100dvh - 32px)); min-height: 0; display: flex; flex-direction: column; background: var(--app-bg-surface); border: 1px solid var(--app-divider); border-radius: 20px; box-shadow: 0 28px 72px rgba(23, 25, 29, .22), 0 8px 24px rgba(23, 25, 29, .12); overflow: hidden; }
.ai-chat__head { min-height: 68px; padding: 12px 14px; display: flex; align-items: center; gap: 10px; flex-shrink: 0; background: var(--app-bg-surface); border-bottom: 1px solid var(--app-divider); }
.ai-chat__avatar, .ai-msg__avatar { display: grid; place-items: center; flex-shrink: 0; color: #FFFFFF; background: var(--app-accent); border-radius: 10px; }
.ai-chat__avatar { width: 34px; height: 34px; font-size: 19px; }.ai-chat__title-wrap { flex: 1; min-width: 0; }.ai-chat__title { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; font-size: 14px; font-weight: 600; }.ai-beta { background: var(--app-bg-selected); color: var(--app-accent); padding: 2px 6px; border-radius: 8px; font-size: 11px; font-weight: 500; }.ai-chat__sub { display: flex; align-items: flex-start; gap: 5px; font-size: 12px; color: var(--app-text-secondary); margin-top: 3px; overflow-wrap: anywhere; }.ai-dot { flex-shrink: 0; width: 6px; height: 6px; border-radius: 50%; background: var(--app-accent); margin-top: 6px; }.ai-dot--muted { background: var(--app-text-secondary); }.ai-chat__actions { display: flex; align-items: center; gap: 0; flex-shrink: 0; }.ai-chat__actions .el-button + .el-button { margin-left: 0; }
.ai-chat__ctx { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; flex-shrink: 0; padding: 8px 14px; background: var(--app-bg-surface); border-bottom: 1px solid var(--app-divider); }.ctx-chip { display: inline-flex; align-items: center; gap: 4px; min-width: 0; padding: 4px 8px; border-radius: 8px; background: var(--app-bg-control); color: var(--app-text-secondary); font-size: 12px; overflow-wrap: anywhere; }
.ai-chat__body { flex: 1 1 0%; min-width: 0; min-height: 0; display: flex; flex-direction: column; gap: 12px; overflow: auto; overscroll-behavior: contain; padding: 16px 14px; background: var(--app-bg-control); }
.ai-unavailable, .ai-welcome { margin: auto; text-align: center; padding: 20px 10px; width: 100%; max-width: 360px; }.ai-welcome__icon { display: grid; place-items: center; width: 48px; height: 48px; margin: 0 auto 12px; border-radius: 16px; background: var(--app-bg-selected); color: var(--app-accent); font-size: 24px; }.ai-welcome__title { font-size: 16px; font-weight: 600; }.ai-welcome__desc, .ai-unavailable__hint { font-size: 13px; line-height: 1.8; color: var(--app-text-secondary); margin-top: 8px; }.ai-unavailable .el-button { margin-top: 16px; }.ai-welcome__prompts { display: flex; flex-direction: column; gap: 8px; margin-top: 18px; }.ai-prompt { border: 0; border-radius: 10px; padding: 10px 12px; background: var(--app-bg-surface); color: var(--app-accent); font: inherit; font-size: 13px; text-align: left; cursor: pointer; overflow-wrap: anywhere; }.ai-prompt:hover { background: var(--app-bg-selected); color: var(--app-accent-hover); }.ai-prompt:disabled { color: var(--app-text-disabled); background: var(--app-bg-disabled); cursor: not-allowed; }
.ai-history { position: absolute; inset: 0; z-index: 20; display: flex; flex-direction: column; min-height: 0; background: var(--app-bg-surface); }.ai-history__head { display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; min-height: 64px; padding: 12px 14px 12px 18px; font-size: 14px; font-weight: 600; }.ai-history__list { min-height: 0; flex: 1; overflow: auto; overscroll-behavior: contain; padding: 10px; background: var(--app-bg-control); }.ai-history__empty { text-align: center; padding: 40px 16px; font-size: 13px; line-height: 1.8; color: var(--app-text-secondary); }.ai-history__item { display: block; width: 100%; margin-bottom: 8px; padding: 12px; border: 0; border-radius: 10px; text-align: left; background: var(--app-bg-surface); cursor: pointer; color: inherit; }.ai-history__item:hover, .ai-history__item--on { background: var(--app-bg-selected); }.ai-history__title { font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }.ai-history__preview { margin-top: 5px; font-size: 12px; line-height: 1.7; color: var(--app-text-secondary); overflow-wrap: anywhere; }.ai-history__meta { margin-top: 6px; font-size: 12px; color: var(--app-text-secondary); }
.ai-msg { display: flex; gap: 8px; min-width: 0; max-width: 100%; }.ai-msg--user { width: 100%; display: flex; justify-content: flex-end; }.ai-bubble-user { max-width: 88%; padding: 10px 13px; border-radius: 16px 16px 8px 16px; background: var(--app-primary); color: #FFFFFF; font-size: 13px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }.ai-msg__avatar { width: 24px; height: 24px; border-radius: 8px; font-size: 14px; margin-top: 2px; }.ai-msg__stack { display: flex; flex: 1; flex-direction: column; gap: 10px; min-width: 0; }.ai-bubble { padding: 10px 12px; background: var(--app-bg-surface); color: var(--app-text-primary); border: 1px solid var(--app-divider); border-radius: 16px 16px 16px 8px; font-size: 13px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }.ai-bubble--error { color: var(--app-text-danger); background: var(--el-color-danger-light-9); }.ai-msg__meta { padding-left: 2px; font-size: 12px; color: var(--app-text-secondary); overflow-wrap: anywhere; }
.ai-card { min-width: 0; background: var(--app-bg-surface); border: 1px solid var(--app-divider); border-radius: 16px; overflow: hidden; }.ai-card__head, .ai-confirm__head { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; padding: 10px 12px; background: var(--app-bg-selected); color: var(--app-accent); font-size: 13px; font-weight: 500; overflow-wrap: anywhere; }.ai-card__right { margin-left: auto; color: var(--app-text-secondary); font-size: 12px; font-weight: 400; overflow-wrap: anywhere; }.ai-card__body { padding: 10px 12px; }.ai-card__body--list { padding: 6px 10px; }.ai-card__empty { padding: 14px; color: var(--app-text-secondary); font-size: 12px; text-align: center; }.ai-card-error { color: var(--app-text-danger); background: var(--el-color-danger-light-9); border-radius: 8px; padding: 10px; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.ai-api-row { width: 100%; display: flex; align-items: flex-start; flex-wrap: wrap; gap: 6px; border: 0; border-radius: 8px; padding: 8px 4px; background: var(--app-bg-surface); color: inherit; font: inherit; text-align: left; cursor: pointer; }.ai-api-row:hover { background: var(--app-bg-control); }.ai-method { flex-shrink: 0; padding: 2px 6px; border-radius: 8px; background: var(--app-bg-control); color: var(--app-text-regular); font-size: 11px; font-weight: 500; }.ai-method--get { background: var(--el-color-success-light-9); color: var(--app-text-success); }.ai-method--post, .ai-method--patch { background: var(--app-bg-selected); color: var(--app-text-accent); }.ai-method--put { background: var(--el-color-warning-light-9); color: var(--app-text-warning); }.ai-method--delete { background: var(--el-color-danger-light-9); color: var(--app-text-danger); }.ai-api-path { min-width: 0; flex: 1; font: 12px/1.8 var(--app-font-mono); overflow-wrap: anywhere; }.ai-api-name { flex-basis: 100%; font-size: 12px; line-height: 1.7; color: var(--app-text-secondary); overflow-wrap: anywhere; }
.ai-stats-total { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; padding: 2px 0 10px; }.ai-stats-num { font-size: 26px; font-weight: 500; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }.ai-stats-label { font-size: 12px; color: var(--app-text-secondary); }.ai-stat-rows { display: flex; flex-direction: column; gap: 8px; }.ai-stat-row { display: flex; align-items: flex-start; gap: 7px; font-size: 12px; }.ai-stat-idx { width: 18px; height: 18px; display: grid; place-items: center; border-radius: 8px; flex-shrink: 0; background: var(--app-bg-control); color: var(--app-text-secondary); font-size: 11px; }.ai-stat-idx--top { background: var(--app-bg-selected); color: var(--app-accent); }.ai-stat-path { flex: 1; min-width: 0; font: 12px/1.8 var(--app-font-mono); overflow-wrap: anywhere; }.ai-stat-count { margin-left: auto; max-width: 72px; font-size: 12px; font-weight: 500; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.ai-confirm__head--done { color: var(--app-text-success); background: var(--el-color-success-light-9); }.ai-confirm__head--muted { color: var(--app-text-secondary); background: var(--app-bg-control); }.ai-countdown { display: inline-flex; align-items: center; gap: 3px; margin-left: auto; padding: 2px 6px; border-radius: 8px; color: var(--app-text-secondary); background: var(--app-bg-surface); font-size: 12px; font-weight: 400; }.ai-confirm__body { padding: 12px; }.ai-confirm__grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; }.ai-confirm__k { margin-bottom: 5px; font-size: 12px; color: var(--app-text-secondary); }.ai-confirm__v { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 4px; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }.ai-confirm__v code { max-width: 100%; font-family: var(--app-font-mono); overflow-wrap: anywhere; }.ai-confirm__preview-wrap { min-width: 0; background: var(--app-bg-control); border-radius: 10px; padding: 10px; }.ai-confirm__preview { margin: 0; max-height: 160px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 12px/1.8 var(--app-font-mono); color: var(--app-text-regular); }.ai-confirm__expand { display: block; margin: 8px 0 0; padding: 0; border: 0; background: var(--app-bg-control); color: var(--app-accent); font-size: 12px; cursor: pointer; }.ai-preview-full { max-height: 65vh; margin: 0; padding: 14px; border-radius: 10px; background: var(--app-bg-control); color: var(--app-text-regular); overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 12px/1.8 var(--app-font-mono); }.ai-confirm__note { display: flex; align-items: flex-start; gap: 5px; margin-top: 12px; font-size: 12px; line-height: 1.8; color: var(--app-text-secondary); }.ai-confirm__note .el-icon { flex-shrink: 0; margin-top: 4px; }.ai-confirm__actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 6px; margin-top: 12px; }.ai-confirm__actions .el-button + .el-button { margin-left: 0; }.ai-confirm__muted { color: var(--app-text-secondary); font-size: 12px; }
.ai-composer { flex-shrink: 0; padding: 12px 14px; background: var(--app-bg-surface); border-top: 1px solid var(--app-divider); }.ai-composer__box { min-width: 0; padding: 10px 12px; border: 0; border-radius: 10px; background: var(--app-bg-control); cursor: text; }.ai-composer__box--focus { outline: 2px solid var(--app-focus-color); outline-offset: 1px; }.ai-composer__box :deep(.el-textarea__inner) { min-height: 44px !important; padding: 0; border: 0; background: var(--app-bg-control); box-shadow: none; font-size: 13px; line-height: 1.8; }.ai-composer__box :deep(.el-textarea__inner:focus) { outline: none; }.ai-composer__foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 8px; }.ai-composer__scope { display: inline-flex; align-items: center; gap: 4px; min-width: 0; font-size: 12px; color: var(--app-text-secondary); overflow-wrap: anywhere; }.ai-composer__tip { display: flex; align-items: flex-start; justify-content: center; gap: 4px; margin-top: 8px; font-size: 12px; line-height: 1.6; color: var(--app-text-secondary); }.ai-composer__tip .el-icon { margin-top: 3px; flex-shrink: 0; }
.ai-bubble--typing { display: flex; align-items: center; gap: 4px; padding: 14px; }.ai-bubble--typing span { width: 5px; height: 5px; border-radius: 50%; background: var(--app-accent); animation: ai-typing 1s ease-in-out infinite; }.ai-bubble--typing span:nth-child(2) { animation-delay: .15s; }.ai-bubble--typing span:nth-child(3) { animation-delay: .3s; }@keyframes ai-typing { 0%,60%,100% { transform: translateY(0); } 30% { transform: translateY(-3px); } }
.ai-pop-enter-active, .ai-pop-leave-active { transition: opacity .18s ease, transform .18s ease; }.ai-pop-enter-from, .ai-pop-leave-to { opacity: 0; transform: translateY(8px); }
@media(max-width:520px) { .ai-chat { inset: 8px; width: auto; height: auto; box-shadow: 0 18px 48px rgba(23, 25, 29, .20); }.ai-chat__head { padding: 12px; gap: 8px; }.ai-chat__ctx { padding: 8px 12px; }.ai-chat__body { padding: 14px 12px; }.ai-confirm__grid { grid-template-columns: minmax(0,1fr); }.ai-fab { right: 24px; bottom: 24px; } }
@media(prefers-reduced-motion:reduce) { .ai-pop-enter-active,.ai-pop-leave-active { transition: none; }.ai-bubble--typing span { animation: none; }.ai-fab { animation: none; } }
</style>