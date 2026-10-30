<template>
  <!-- 使用统计页 -->
  <!-- 顶部 KPI 卡片 + 双图表 + 僵尸接口列表 -->
  <div class="page-stats">
    <!-- 页头：标题 + 团队切换 + 时间窗口 -->
    <div class="stats-header">
      <div class="stats-header__title">
        <h1 class="page-title">使用统计</h1>
        <p class="page-subtitle">查看 Mock 接口的调用情况，识别活跃和僵尸接口</p>
      </div>
      <div class="stats-header__filters">
        <span class="team-context">{{ appStore.currentTeam?.name || '未选择团队' }}</span>
        <el-button :disabled="!canRead" @click="loadAll">刷新</el-button>
        <el-select
          v-model="windowDays"
          class="days-select"
        >
          <el-option :value="7" label="最近 7 天" />
          <el-option :value="14" label="最近 14 天" />
          <el-option :value="30" label="最近 30 天" />
        </el-select>
      </div>
    </div>

    <div v-if="!canRead" class="resource-state" role="status">请选择有访问权限的团队。统计不会自动扩展到全部团队。</div>
    <div v-if="errors.overview" class="resource-error" role="alert">概览加载失败：{{ errors.overview }} <el-button link @click="loadAll">重试</el-button></div>
    <!-- KPI 卡片 -->
    <div v-if="canRead && !errors.overview" class="kpi-grid" v-loading="loadingOverview">
      <div class="kpi">
        <div class="kpi__icon kpi__icon--primary">
          <el-icon><Connection /></el-icon>
        </div>
        <div class="kpi__label">启用接口</div>
        <div class="kpi__value">{{ formatNumber(overview.enabledApiCount) }}</div>
        <div class="kpi__hint">当前过滤范围内</div>
      </div>
      <div class="kpi">
        <div class="kpi__icon kpi__icon--success">
          <el-icon><Lightning /></el-icon>
        </div>
        <div class="kpi__label">今日调用</div>
        <div class="kpi__value">{{ formatNumber(overview.todayCallCount) }}</div>
        <div class="kpi__hint">自然日 00:00 起</div>
      </div>
      <div class="kpi">
        <div class="kpi__icon kpi__icon--warning">
          <el-icon><DataLine /></el-icon>
        </div>
        <div class="kpi__label">{{ windowDays }} 日总调用</div>
        <div class="kpi__value">{{ formatNumber(overview.windowCallCount) }}</div>
        <div class="kpi__hint">含今日</div>
      </div>
      <div class="kpi">
        <div class="kpi__icon kpi__icon--info">
          <el-icon><Timer /></el-icon>
        </div>
        <div class="kpi__label">平均响应耗时</div>
        <div class="kpi__value">
          {{ formatNumber(overview.avgDurationMs) }}
          <span class="kpi__unit">ms</span>
        </div>
        <div class="kpi__hint">{{ windowDays }} 日窗口内</div>
      </div>
    </div>

    <!-- 图表行 -->
    <div v-if="canRead" class="charts-row">
      <div class="chart-card">
        <div class="chart-card__header">
          <div>
            <div class="chart-card__title">{{ windowDays }} 日调用趋势</div>
            <div class="chart-card__hint">每日调用次数</div>
          </div>
        </div>
        <p v-if="errors.trend" class="resource-error" role="alert">趋势加载失败：{{ errors.trend }} <el-button link @click="loadAll">重试</el-button></p>
        <div ref="trendChartRef" class="chart" v-loading="loadingTrend"></div>
      </div>
      <div class="chart-card">
        <div class="chart-card__header">
          <div>
            <div class="chart-card__title">接口热度 TOP 10</div>
            <div class="chart-card__hint">{{ windowDays }} 日内调用最多的接口</div>
          </div>
        </div>
        <p v-if="errors.top" class="resource-error" role="alert">热度加载失败：{{ errors.top }} <el-button link @click="loadAll">重试</el-button></p>
        <div ref="topChartRef" class="chart" v-loading="loadingTop"></div>
        <p v-if="!loadingTop && !errors.top && topApis.length === 0" class="chart-empty">
          窗口内暂无调用数据
        </p>
      </div>
    </div>

    <!-- 僵尸接口 -->
    <div v-if="canRead" class="zombie-card">
      <div class="zombie-card__header">
        <div>
          <div class="chart-card__title">
            僵尸接口
            <span class="zombie-badge">{{ zombieTotal }} 个</span>
          </div>
          <div class="chart-card__hint">超过 30 天未被调用的启用接口，建议清理</div>
        </div>
      </div>
      <p v-if="errors.zombies" class="resource-error" role="alert">僵尸接口加载失败：{{ errors.zombies }} <el-button link @click="loadAll">重试</el-button></p>
      <el-table v-else
        class="soft-table"
        :data="zombies"
        v-loading="loadingZombies"
        empty-text="没有僵尸接口"
      >
        <el-table-column label="方法" width="90">
          <template #default="{ row }">
            <HttpMethodTag :method="row.method" />
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="240">
          <template #default="{ row }">
            <span class="path-text" :title="row.path">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="200">
          <template #default="{ row }">
            <span class="cell-title" :title="row.name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计命中" width="100" align="right">
          <template #default="{ row }">
            <span class="duration-text">{{ formatNumber(row.hitCount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近调用" width="140">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.lastCalledAt"
              :content="row.lastCalledAt"
              placement="top"
            >
              <span class="duration-text">{{ formatRelativeTime(row.lastCalledAt) }}</span>
            </el-tooltip>
            <span v-else class="duration-text">从未调用</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEditApi(row.apiId)">
              {{ canWrite ? '编辑' : '查看' }}<el-icon class="btn-icon"><ArrowRight /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p v-if="!loadingZombies && zombieTotal > zombies.length" class="zombie-card__more">
        共 {{ zombieTotal }} 个僵尸接口，当前展示前 {{ zombies.length }} 条
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getOverview, getTrend, getTopApis, getZombies } from '@/api/stats'
import { Connection, Lightning, DataLine, Timer, ArrowRight } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import HttpMethodTag from '@/components/HttpMethodTag.vue'
import { fromNow } from '@/utils/time'

const router = useRouter(), userStore = useUserStore(), appStore = useAppStore()
const selectedTeamId = computed(() => appStore.currentTeamId || null)
const canRead = computed(() => !!selectedTeamId.value && userStore.identityReady && userStore.hasCapability(selectedTeamId.value, 'read'))
const canWrite = computed(() => canRead.value && appStore.currentTeam?.status !== 'ARCHIVED' && userStore.hasCapability(selectedTeamId.value, 'write'))
const windowDays = ref(7)
const overview = ref({ enabledApiCount: null, todayCallCount: null, windowCallCount: null, avgDurationMs: null })
const trendData = ref([]), topApis = ref([]), zombies = ref([]), zombieTotal = ref(0)
const loadingOverview = ref(false), loadingTrend = ref(false), loadingTop = ref(false), loadingZombies = ref(false)
const errors = reactive({ overview: '', trend: '', top: '', zombies: '' })
const trendChartRef = ref(null), topChartRef = ref(null)
let trendChart = null, topChart = null, observer = null, generation = 0, disposed = false
const loadingRefs = { overview: loadingOverview, trend: loadingTrend, top: loadingTop, zombies: loadingZombies }
function formatNumber(value) { return value == null ? '—' : Number(value).toLocaleString('zh-CN') }
function formatRelativeTime(value) { return value ? fromNow(value) : '从未调用' }
function current(ctx) { return !disposed && canRead.value && ctx.teamId === selectedTeamId.value && ctx.generation === generation && ctx.days === windowDays.value }
function disposeCharts() { trendChart?.dispose(); topChart?.dispose(); trendChart = null; topChart = null }
function clearData() {
  overview.value = { enabledApiCount: null, todayCallCount: null, windowCallCount: null, avgDurationMs: null }
  trendData.value = []; topApis.value = []; zombies.value = []; zombieTotal.value = 0
  for (const key of Object.keys(errors)) { errors[key] = ''; loadingRefs[key].value = false }
  disposeCharts()
}
async function loadPart(key, ctx, request, apply, render) {
  loadingRefs[key].value = true; errors[key] = ''
  try { const data = await request(); if (!current(ctx)) return; apply(data); await nextTick(); if (current(ctx)) render?.() }
  catch (error) { if (current(ctx)) errors[key] = error?.response?.data?.msg || error?.msg || error?.message || '请求失败，请重试。' }
  finally { if (current(ctx)) loadingRefs[key].value = false }
}
async function loadAll() {
  generation++; clearData()
  if (!canRead.value) return
  const ctx = { teamId: selectedTeamId.value, days: windowDays.value, generation }
  await Promise.all([
    loadPart('overview', ctx, () => getOverview({ teamId: ctx.teamId, days: ctx.days }), data => { overview.value = data }),
    loadPart('trend', ctx, () => getTrend({ teamId: ctx.teamId, days: ctx.days }), data => { trendData.value = data || [] }, renderTrendChart),
    loadPart('top', ctx, () => getTopApis({ teamId: ctx.teamId, days: ctx.days, limit: 10 }), data => { topApis.value = data || [] }, renderTopChart),
    loadPart('zombies', ctx, () => getZombies({ teamId: ctx.teamId, days: 30, limit: 20 }), data => { zombies.value = data.list || []; zombieTotal.value = data.total || 0 })
  ])
}
const tooltip = { trigger: 'axis', backgroundColor: '#17191D', borderWidth: 0, textStyle: { color: '#FFFFFF', fontSize: 12 }, padding: 10, extraCssText: 'box-shadow:none;border:0;border-radius:10px;' }
function renderTrendChart() {
  if (!canRead.value || !trendChartRef.value?.clientWidth) return
  trendChart ||= echarts.init(trendChartRef.value)
  trendChart.setOption({ animation: false, aria: { enabled: true }, grid: { left: 12, right: 18, top: 24, bottom: 12, containLabel: true }, tooltip,
    xAxis: { type: 'category', data: trendData.value.map(p => p.date.slice(5)), boundaryGap: false, axisLine: { lineStyle: { color: '#E6E9EF' } }, axisTick: { show: false }, axisLabel: { color: '#5D6470', fontSize: 12 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#E6E9EF', type: 'dashed' } }, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#5D6470', fontSize: 12 } },
    series: [{ type: 'line', data: trendData.value.map(p => p.count), symbol: 'circle', symbolSize: 6, lineStyle: { color: '#3568D4', width: 3 }, itemStyle: { color: '#3568D4' }, areaStyle: { color: '#EDF2FC', opacity: 1 } }]
  }, true)
}
function renderTopChart() {
  if (!canRead.value || !topChartRef.value?.clientWidth) return
  topChart ||= echarts.init(topChartRef.value)
  if (!topApis.value.length) { topChart.clear(); return }
  const reversed = [...topApis.value].reverse()
  topChart.setOption({ animation: false, aria: { enabled: true }, grid: { left: 12, right: 42, top: 10, bottom: 10, containLabel: true }, tooltip,
    xAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false } },
    yAxis: { type: 'category', data: reversed.map(a => a.path || '(已删除)'), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#3B4048', fontSize: 12, fontFamily: 'SFMono-Regular,Menlo,monospace', width: Math.min(160, topChartRef.value.clientWidth * .4), overflow: 'truncate' } },
    series: [{ type: 'bar', data: reversed.map(a => a.callCount), barWidth: 14, label: { show: true, position: 'right', color: '#17191D', fontSize: 12, fontWeight: 500 }, itemStyle: { borderRadius: [0, 8, 8, 0], color: '#3568D4' } }]
  }, true)
}
function handleEditApi(apiId) { if (canRead.value && apiId) router.push('/apis/' + encodeURIComponent(apiId) + '/edit') }
function handleResize() { if (!canRead.value) return; trendChart?.resize(); topChart?.resize(); if (!trendChart) renderTrendChart(); if (!topChart) renderTopChart() }
function observeCharts() { observer?.disconnect(); for (const element of [trendChartRef.value, topChartRef.value]) if (element) observer?.observe(element); handleResize() }
watch([selectedTeamId, canRead, windowDays, () => userStore.user?.id], loadAll, { immediate: true, flush: 'sync' })
watch([trendChartRef, topChartRef], observeCharts, { flush: 'post' })
onMounted(() => { if (typeof ResizeObserver !== 'undefined') observer = new ResizeObserver(handleResize); observeCharts(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => { disposed = true; generation++; observer?.disconnect(); window.removeEventListener('resize', handleResize); disposeCharts() })
</script>

<style lang="scss" scoped>
.page-stats { min-width: 0; color: var(--app-text-primary); }.stats-header { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }.stats-header__title .page-title { margin: 0 0 6px; font-size: 26px; font-weight: 600; color: var(--app-text-primary); letter-spacing: -.025em; }.page-subtitle { margin: 0; font-size: 13px; color: var(--app-text-secondary); }.stats-header__filters { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.stats-header__filters :deep(.el-button) { height: 38px; }.days-select { width: 140px; }.team-context { display: inline-flex; align-items: center; height: 38px; padding: 0 12px; border-radius: 8px; background: var(--app-bg-surface); font-size: 12px; color: var(--app-text-secondary); }
.kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 14px; margin-bottom: 14px; }.kpi { display: grid; grid-template-columns: minmax(0,1fr) 32px; gap: 8px; min-width: 0; background: var(--app-bg-surface); border-radius: 16px; padding: 20px; }.kpi__icon { grid-column: 2; grid-row: 1 / span 2; display: grid; place-items: center; width: 32px; height: 32px; border-radius: 10px; background: var(--app-bg-control); color: var(--app-accent); font-size: 18px; }.kpi__label { grid-column: 1; grid-row: 1; color: var(--app-text-secondary); font-size: 12px; }.kpi__value { grid-column: 1; grid-row: 2; font-size: clamp(22px,2.2vw,30px); font-weight: 500; line-height: 1.3; letter-spacing: -.03em; overflow-wrap: anywhere; }.kpi__unit { font-size: 13px; color: var(--app-text-secondary); margin-left: 4px; }.kpi__hint { grid-column: 1 / -1; font-size: 12px; color: var(--app-text-secondary); }
.charts-row { display: grid; grid-template-columns: minmax(0,1.3fr) minmax(0,1fr); align-items: stretch; gap: 14px; margin-bottom: 14px; }.chart-card, .zombie-card { min-width: 0; background: var(--app-bg-surface); border-radius: 16px; padding: 20px; }.chart-card { display: flex; flex-direction: column; position: relative; }.chart-card__header, .zombie-card__header { margin-bottom: 16px; }.chart-card__title { font-size: 15px; font-weight: 600; }.chart-card__hint { font-size: 12px; color: var(--app-text-secondary); margin-top: 5px; }.chart { flex: 1 1 auto; width: 100%; min-width: 0; min-height: 280px; }.chart-empty { position: absolute; left: 20px; right: 20px; top: 50%; transform: translateY(-50%); margin: 0; pointer-events: none; text-align: center; color: var(--app-text-secondary); font-size: 13px; }.zombie-badge { margin-left: 8px; background: var(--app-bg-control); color: var(--app-text-secondary); font-size: 12px; padding: 3px 8px; border-radius: 8px; }.zombie-card__more { text-align: center; font-size: 12px; color: var(--app-text-secondary); margin: 12px 0 0; }.path-text { font: 13px var(--app-font-mono); color: var(--app-text-regular); }.duration-text { color: var(--app-text-secondary); font-size: 12px; }.btn-icon { margin-left: 4px; font-size: 12px; }.resource-state, .resource-error { padding: 16px 20px; border-radius: 10px; font-size: 13px; margin: 0 0 14px; background: var(--app-bg-surface); color: var(--app-text-secondary); overflow-wrap: anywhere; }.resource-error { color: var(--app-text-danger); background: var(--el-color-danger-light-9); }.cell-title { display: block; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.soft-table :deep(.el-button.is-link) { min-height: 28px; }
@media(max-width:1100px){.kpi-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.charts-row{grid-template-columns:minmax(0,1fr)}}
@media(max-width:600px){.stats-header__title .page-title{font-size:24px}.kpi{padding:16px;grid-template-columns:minmax(0,1fr)}.kpi__icon{grid-column:1;grid-row:1;justify-self:end}.kpi__label{grid-column:1;grid-row:2}.kpi__value{grid-column:1;grid-row:3}.kpi__hint{grid-row:4}.chart-card,.zombie-card{padding:16px}.stats-header__filters{width:100%}.chart{min-height:260px}}
</style>
