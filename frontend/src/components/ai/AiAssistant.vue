<template>
  <div v-if="aiStore.visible" class="ai-assistant">
    <transition name="ai-pop">
      <section v-if="open" class="ai-chat">
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
              <span class="ai-dot" :class="{ 'ai-dot--muted': !aiStore.available }"></span>
              {{ aiStore.available ? (aiStore.provider === 'MOCK' ? '在线 · 内置演示模型' : '在线 · ' + (aiStore.status?.model || 'AI')) : (aiStore.reason || '未就绪') }}
            </div>
          </div>
          <div class="ai-chat__actions">
            <el-button text circle size="small" title="清空对话" @click="clearChat">
              <el-icon><Delete /></el-icon>
            </el-button>
            <el-button text circle size="small" title="收起" @click="open = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </header>

        <div class="ai-chat__ctx">
          <el-select
            v-if="userStore.isSuperAdmin"
            v-model="scopeTeamId"
            size="small"
            class="ai-scope-select"
            placeholder="选择团队"
            filterable
            :loading="teamsLoading"
            @visible-change="onScopeVisible"
          >
            <el-option label="全部团队" value="__ALL__" />
            <el-option v-for="team in assistantTeams" :key="team.id" :label="team.name" :value="team.id" />
          </el-select>
          <span v-else class="ctx-chip">范围：{{ teamName || '未选择团队' }}</span>
          <span class="ctx-chip">
            <el-icon><Lock /></el-icon>
            以你的权限运行
          </span>
          <span v-if="teamsError" class="ai-team-error" @click="loadAssistantTeams(true)">{{ teamsError }}，重试</span>
        </div>

        <div ref="bodyRef" class="ai-chat__body">
          <div v-if="!aiStore.available" class="ai-unavailable">
            <div class="ai-welcome__icon">
              <el-icon><MagicStick /></el-icon>
            </div>
            <div class="ai-welcome__title">AI 助手尚未就绪</div>
            <div class="ai-welcome__desc">{{ aiStore.reason || '请先在全局设置中配置模型服务' }}</div>
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
            <div class="ai-welcome__desc">
              可以查询调用统计、搜索接口、生成响应体或创建接口草稿。
              涉及写操作时会先给你确认卡片。
            </div>
            <div class="ai-welcome__prompts">
              <button
                v-for="prompt in quickPrompts"
                :key="prompt"
                class="ai-prompt"
                @click="send(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </div>

          <div v-for="(msg, idx) in messages" v-if="aiStore.available" :key="idx" class="ai-msg">
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
                        class="ai-api-row"
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
                      <template v-if="card.status === 'CONFIRMED'">已创建 · {{ card.summary.method }} {{ card.summary.path }}</template>
                      <template v-else-if="card.status === 'CANCELLED'">已取消 · {{ card.summary.method }} {{ card.summary.path }}</template>
                      <template v-else-if="card.status === 'EXPIRED' || cardExpired(card)">已过期 · 草稿已失效</template>
                      <template v-else>待确认操作 · {{ card.actionType }}</template>
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
                      <pre class="ai-confirm__preview">{{ previewText(card) }}</pre>
                      <div class="ai-confirm__note">
                        <el-icon><Lock /></el-icon>
                        确认后服务端会重新校验权限并写入，操作记入日志。
                      </div>
                      <div class="ai-confirm__actions">
                        <template v-if="card.status === 'PENDING' && !cardExpired(card)">
                          <el-button text size="small" @click="cancelCard(card)">取消</el-button>
                          <el-button size="small" type="primary" :loading="card.busy" @click="confirmCard(card)">确认创建</el-button>
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

          <div v-if="aiStore.available && loading" class="ai-msg">
            <div class="ai-msg__avatar">
              <el-icon><MagicStick /></el-icon>
            </div>
            <div class="ai-bubble ai-bubble--typing">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>

        <div v-if="aiStore.available" class="ai-composer">
          <div class="ai-composer__box" :class="{ 'ai-composer__box--focus': composerFocus }" @click="focusComposer">
            <el-input
              ref="composerInput"
              v-model="input"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 5 }"
              resize="none"
              placeholder="继续追问，或描述要创建的接口…"
              :disabled="(!teamId && !userStore.isSuperAdmin) || loading"
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
                type="primary"
                circle
                size="small"
                :disabled="(!teamId && !userStore.isSuperAdmin) || loading || !input.trim()"
                @click="send()"
              >
                <el-icon><Promotion /></el-icon>
              </el-button>
            </div>
          </div>
          <div class="ai-composer__tip">
            <el-icon><Lock /></el-icon>
            AI 生成内容需确认后生效，不会自动保存
          </div>
        </div>
      </section>
    </transition>

    <button v-if="!open" class="ai-fab" title="MockHub AI 助手" @click="openChat">
      <el-icon><MagicStick /></el-icon>
      <span class="ai-fab__dot"></span>
    </button>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  MagicStick, Close, Delete, Lock, Search, DataAnalysis,
  Check, EditPen, Clock, Promotion, OfficeBuilding
} from '@element-plus/icons-vue'
import { aiChat, confirmAiAction, cancelAiAction } from '@/api/ai'
import { getTeams } from '@/api/teams'
import { useAiStore } from '@/stores/ai'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const aiStore = useAiStore()
const appStore = useAppStore()
const userStore = useUserStore()
const router = useRouter()

const open = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref([])
const bodyRef = ref(null)
const composerFocus = ref(false)
const composerInput = ref(null)
const scopeTeamId = ref('__ALL__')
const assistantTeams = ref([])
const teamsLoading = ref(false)
const teamsError = ref('')

const quickPrompts = [
  '最近 7 天哪个接口调用最多？',
  '找一下用户相关的接口',
  '帮我创建 GET /api/user/info 的 Mock，返回姓名、头像、等级'
]

const teamId = computed(() => {
  if (userStore.isSuperAdmin) {
    return scopeTeamId.value && scopeTeamId.value !== '__ALL__' ? scopeTeamId.value : null
  }
  return appStore.currentTeamId || assistantTeams.value[0]?.id || appStore.teams[0]?.id || null
})
const teamName = computed(() => {
  if (!teamId.value) return userStore.isSuperAdmin ? '全部团队' : ''
  return assistantTeams.value.find((team) => team.id === teamId.value)?.name || ''
})

async function loadAssistantTeams(force) {
  if (teamsLoading.value) return
  if (!force && assistantTeams.value.length) return
  teamsLoading.value = true
  teamsError.value = ''
  try {
    const data = await getTeams()
    assistantTeams.value = Array.isArray(data) ? data : []
    if (assistantTeams.value.length && appStore.teams.length === 0) {
      appStore.teams = assistantTeams.value
    }
    if (userStore.isSuperAdmin && scopeTeamId.value !== '__ALL__'
      && !assistantTeams.value.some(team => team.id === scopeTeamId.value)) {
      scopeTeamId.value = '__ALL__'
    }
  } catch (e) {
    if (assistantTeams.value.length === 0 && appStore.teams.length) {
      assistantTeams.value = appStore.teams
    }
    teamsError.value = '团队列表加载失败'
  } finally {
    teamsLoading.value = false
  }
}

function onScopeVisible(visible) {
  if (visible) {
    loadAssistantTeams(true)
  }
}

onMounted(async () => {
  aiStore.load()
  assistantTeams.value = appStore.teams
  await loadAssistantTeams(true)
  if (userStore.isSuperAdmin) {
    const preferred = appStore.currentTeamId
    scopeTeamId.value = preferred && assistantTeams.value.some(team => team.id === preferred)
      ? preferred
      : '__ALL__'
  }
})

onBeforeUnmount(() => {
  // 组件销毁无需额外清理，消息仅保存在内存
})

function goSettings() {
  open.value = false
  router.push('/settings')
}

function focusComposer() {
  if (!loading.value) {
    composerInput.value?.focus()
  }
}

function openChat() {
  open.value = true
  if (!assistantTeams.value.length) {
    loadAssistantTeams()
  }
  scrollToBottom()
}

function clearChat() {
  messages.value = []
  input.value = ''
}

async function send(preset) {
  const content = (preset || input.value).trim()
  if (!content || loading.value) return
  if (!teamId.value && !userStore.isSuperAdmin) {
    ElMessage.warning('请先在侧边栏选择团队')
    return
  }
  messages.value.push({ role: 'user', text: content })
  input.value = ''
  loading.value = true
  scrollToBottom()
  try {
    const history = messages.value.slice(0, -1).slice(-8).map((msg) => ({
      role: msg.role,
      content: msg.role === 'user' ? msg.text : (msg.reply || '')
    }))
    const res = await aiChat({
      teamId: teamId.value,
      message: content,
      history
    })
    messages.value.push({
      role: 'assistant',
      reply: res.reply || '没有生成回复，请换个说法试试。',
      cards: res.cards || [],
      provider: res.provider,
      model: res.model
    })
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      reply: e?.msg || '请求失败，请稍后重试',
      cards: [],
      error: true
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

async function confirmCard(card) {
  if (!card.actionId || card.busy) return
  card.busy = true
  try {
    const res = await confirmAiAction(card.actionId)
    card.status = 'CONFIRMED'
    card.apiId = res.apiId
    ElMessage.success('接口已创建')
  } catch (e) {
    ElMessage.error(e?.msg || '确认失败，请稍后重试')
    if (e?.code === 40901) {
      card.status = 'EXPIRED'
    }
  } finally {
    card.busy = false
  }
}

async function cancelCard(card) {
  if (!card.actionId || card.busy) return
  card.busy = true
  try {
    await cancelAiAction(card.actionId)
    card.status = 'CANCELLED'
    ElMessage.success('已取消')
  } catch (e) {
    ElMessage.error(e?.msg || '取消失败，请稍后重试')
  } finally {
    card.busy = false
  }
}

function openApi(apiId) {
  if (!apiId) return
  open.value = false
  router.push('/apis/' + apiId + '/edit')
}

function previewText(card) {
  const lines = String(card.preview || '').split('\n')
  if (lines.length <= 5) return card.preview
  return lines.slice(0, 5).join('\n') + '\n…'
}

function cardExpired(card) {
  if (!card.expiresAt) return false
  return new Date(card.expiresAt).getTime() < Date.now()
}

function expiryText(card) {
  if (!card.expiresAt) return ''
  const date = new Date(card.expiresAt)
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return '有效至 ' + hh + ':' + mm
}

function teamLabel(id) {
  const team = assistantTeams.value.find((item) => item.id === id)
    || appStore.teams.find((item) => item.id === id)
  return team ? team.name : '当前团队'
}

async function scrollToBottom() {
  await nextTick()
  if (bodyRef.value) {
    bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  }
}

watch(messages, () => {
  scrollToBottom()
}, { deep: true })

watch(() => aiStore.available, (available) => {
  if (!available) open.value = false
})
</script>

<style scoped>
.ai-assistant {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 2001;
}

.ai-fab {
  pointer-events: auto;
  position: absolute;
  right: 28px;
  bottom: 28px;
  width: 56px;
  height: 56px;
  border: none;
  border-radius: 18px;
  background: var(--ai-gradient, linear-gradient(135deg, #6366F1, #8B5CF6));
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.38), 0 2px 6px rgba(99, 102, 241, 0.25);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.ai-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 34px rgba(99, 102, 241, 0.45);
}

.ai-fab__dot {
  position: absolute;
  right: -2px;
  top: -2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #22C55E;
  border: 2.5px solid #fff;
}

.ai-chat {
  pointer-events: auto;
  position: absolute;
  right: 24px;
  bottom: 16px;
  width: 428px;
  height: min(824px, calc(100vh - 32px));
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 64px rgba(27, 37, 89, 0.18), 0 2px 8px rgba(27, 37, 89, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-chat__head {
  height: 62px;
  padding: 0 10px 0 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid #F1F5F9;
  flex-shrink: 0;
}

.ai-chat__avatar {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: var(--ai-gradient, linear-gradient(135deg, #6366F1, #8B5CF6));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  flex-shrink: 0;
}

.ai-chat__title-wrap {
  flex: 1;
  min-width: 0;
}

.ai-chat__title {
  font-size: 14px;
  font-weight: 600;
  color: #1B2559;
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.ai-beta {
  font-size: 10.5px;
  font-weight: 700;
  color: var(--ai-accent, #8B5CF6);
  background: var(--ai-soft, #F5F3FF);
  border: 1px solid var(--ai-border, #E7E3FD);
  border-radius: 6px;
  padding: 0 6px;
  line-height: 18px;
}

.ai-chat__sub {
  font-size: 11.5px;
  color: #A3AED0;
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 1px;
}

.ai-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10B981;
}

.ai-dot--muted {
  background: #CBD5E1;
}

.ai-scope-select {
  width: 150px;
}

.ai-scope-select :deep(.el-input__wrapper) {
  background: #fff;
  border-radius: 7px;
  box-shadow: 0 0 0 1px #EDF0F5 inset;
  min-height: 26px;
}

.ai-unavailable {
  margin: auto;
  text-align: center;
  padding: 24px 16px;
  max-width: 340px;
}

.ai-unavailable__hint {
  font-size: 11.5px;
  color: #A3AED0;
  margin-top: 10px;
}

.ai-chat__actions {
  display: flex;
  align-items: center;
  gap: 0;
  color: #A3AED0;
}

.ai-chat__ctx {
  height: 34px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  background: #FBFBFE;
  border-bottom: 1px solid #F1F5F9;
  flex-shrink: 0;
  font-size: 11.5px;
  color: #7B87A8;
}

.ai-team-error {
  margin-left: auto;
  color: #EF4444;
  font-size: 10.5px;
  cursor: pointer;
  white-space: nowrap;
}

.ctx-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  background: #F7F8FA;
  color: #4A5568;
  font-size: 11px;
}

.ai-chat__body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 14px 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #FDFDFF;
}

.ai-welcome {
  margin: auto;
  text-align: center;
  padding: 20px 12px;
  max-width: 340px;
}

.ai-welcome__icon {
  width: 48px;
  height: 48px;
  border-radius: 15px;
  margin: 0 auto 12px;
  background: var(--ai-soft, #F5F3FF);
  border: 1px solid var(--ai-border, #E7E3FD);
  color: var(--ai-accent, #8B5CF6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.ai-welcome__title {
  font-size: 14px;
  font-weight: 600;
  color: #1B2559;
}

.ai-welcome__desc {
  font-size: 11.5px;
  color: #A3AED0;
  line-height: 1.75;
  margin-top: 4px;
}

.ai-welcome__prompts {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
}

.ai-prompt {
  border: 1px solid #EDF0F5;
  background: #fff;
  border-radius: 9px;
  padding: 7px 10px;
  font-size: 11.5px;
  color: #4A5568;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.ai-prompt:hover {
  border-color: #C7D2FE;
  color: #4F46E5;
  background: #FBFCFF;
}

.ai-msg {
  display: flex;
  gap: 8px;
  max-width: 100%;
}

.ai-msg--user {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.ai-bubble-user {
  max-width: 300px;
  background: #6366F1;
  color: #fff;
  padding: 9px 13px;
  border-radius: 12px 12px 4px 12px;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-msg__avatar {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: var(--ai-gradient, linear-gradient(135deg, #6366F1, #8B5CF6));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
  margin-top: 2px;
}

.ai-msg__stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 344px;
  min-width: 0;
}

.ai-bubble {
  background: #fff;
  border: 1px solid #F1F5F9;
  padding: 9px 12px;
  border-radius: 12px 12px 12px 4px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #1B2559;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-bubble--error {
  border-color: #FEE2E2;
  background: #FEF2F2;
  color: #B91C1C;
}

.ai-bubble--typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 14px;
}

.ai-bubble--typing span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #C7D2FE;
  animation: ai-typing 1s ease-in-out infinite;
}

.ai-bubble--typing span:nth-child(2) { animation-delay: 0.15s; }
.ai-bubble--typing span:nth-child(3) { animation-delay: 0.3s; }

@keyframes ai-typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-3px); opacity: 1; }
}

.ai-card {
  background: #fff;
  border: 1px solid #EDF0F5;
  border-radius: 12px;
  overflow: hidden;
}

.ai-card__head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #FBFBFE;
  border-bottom: 1px solid #F1F5F9;
  font-size: 12px;
  font-weight: 600;
  color: #4A5568;
}

.ai-card__head .el-icon {
  color: #A3AED0;
}

.ai-card__right {
  margin-left: auto;
  font-size: 11px;
  font-weight: 400;
  color: #A3AED0;
}

.ai-card__body {
  padding: 8px 12px;
}

.ai-card__body--list {
  padding: 4px 8px;
}

.ai-api-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 4px;
  border: none;
  border-bottom: 1px solid #F8FAFC;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}

.ai-api-row:last-child {
  border-bottom: none;
}

.ai-api-row:hover {
  background: #FAFBFF;
}

.ai-method {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 5px;
  background: #F7F8FA;
  color: #4A5568;
  flex-shrink: 0;
}

.ai-method--get { background: rgba(16, 185, 129, 0.12); color: #059669; }
.ai-method--post { background: rgba(99, 102, 241, 0.12); color: #4F46E5; }
.ai-method--put { background: rgba(245, 158, 11, 0.12); color: #D97706; }
.ai-method--delete { background: rgba(239, 68, 68, 0.12); color: #DC2626; }
.ai-method--patch { background: rgba(139, 92, 246, 0.12); color: #7C3AED; }

.ai-api-path {
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  color: #1B2559;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-api-name {
  margin-left: auto;
  font-size: 10.5px;
  color: #A3AED0;
  flex-shrink: 0;
}

.ai-stats-total {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 2px 0 6px;
}

.ai-stats-num {
  font-size: 22px;
  font-weight: 700;
  color: #1B2559;
  font-variant-numeric: tabular-nums;
}

.ai-stats-label {
  font-size: 10.5px;
  color: #A3AED0;
}

.ai-stat-rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-stat-row {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  padding: 3px 0;
}

.ai-stat-idx {
  width: 16px;
  height: 16px;
  border-radius: 5px;
  background: #F7F8FA;
  color: #A3AED0;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-stat-idx--top {
  background: #E0E7FF;
  color: #4F46E5;
}

.ai-stat-path {
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 10.5px;
  color: #1B2559;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-stat-count {
  margin-left: auto;
  font-weight: 600;
  color: #1B2559;
  font-variant-numeric: tabular-nums;
}

.ai-card__empty {
  text-align: center;
  font-size: 11px;
  color: #A3AED0;
  padding: 10px 0;
}

.ai-confirm__head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.07), rgba(139, 92, 246, 0.03));
  border-bottom: 1px solid #E7E3FD;
  font-size: 12px;
  font-weight: 600;
  color: #6D28D9;
}

.ai-confirm__head--done {
  background: #ECFDF5;
  border-bottom-color: #DCF5EC;
  color: #0F9D6E;
}

.ai-confirm__head--muted {
  background: #F7F8FA;
  border-bottom-color: #F1F5F9;
  color: #A3AED0;
}

.ai-countdown {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 10.5px;
  font-weight: 500;
  color: #A3AED0;
  background: #fff;
  border: 1px solid #F1F5F9;
  border-radius: 6px;
  padding: 0 6px;
  height: 20px;
}

.ai-confirm__body {
  padding: 10px 12px;
}

.ai-confirm__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px 12px;
  font-size: 12px;
}

.ai-confirm__k {
  font-size: 11.5px;
  color: #A3AED0;
}

.ai-confirm__v {
  color: #1B2559;
  font-weight: 500;
  margin-top: 1px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-confirm__v code {
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 400;
}

.ai-confirm__preview {
  margin: 8px 0 0;
  padding: 8px 10px;
  background: #FAFBFD;
  border: 1px solid #F1F5F9;
  border-radius: 8px;
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 10.5px;
  line-height: 1.7;
  color: #4A5568;
  max-height: 124px;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-all;
}

.ai-confirm__note {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  font-size: 10.5px;
  color: #A3AED0;
  line-height: 1.6;
  margin-top: 6px;
}

.ai-confirm__note .el-icon {
  margin-top: 2px;
  flex-shrink: 0;
}

.ai-confirm__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 8px;
}

.ai-confirm__muted {
  font-size: 10.5px;
  color: #A3AED0;
}

.ai-msg__meta {
  font-size: 10.5px;
  color: #CBD5E0;
  padding-left: 2px;
}

.ai-composer {
  flex-shrink: 0;
  padding: 11px 14px 10px;
  border-top: 1px solid #F1F5F9;
  background: #fff;
}

.ai-composer__box {
  border: 1px solid #E6EAF2;
  border-radius: 12px;
  padding: 8px 10px 6px 12px;
  background: #fff;
  cursor: text;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ai-composer__box--focus {
  border-color: #6366F1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.08);
}

.ai-composer__box :deep(.el-textarea__inner) {
  min-height: 44px !important;
  padding: 2px 0;
  box-shadow: none;
  background: transparent;
  font-size: 13px;
  line-height: 1.65;
  cursor: text;
}

.ai-composer__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.ai-composer__scope {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10.5px;
  color: #A3AED0;
}

.ai-composer__tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 10px;
  color: #CBD5E0;
  margin-top: 5px;
}

.ai-pop-enter-active,
.ai-pop-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.ai-pop-enter-from,
.ai-pop-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.99);
}

@media (max-width: 520px) {
  .ai-chat {
    right: 8px;
    left: 8px;
    width: auto;
    bottom: 8px;
    height: calc(100vh - 16px);
  }
}
</style>