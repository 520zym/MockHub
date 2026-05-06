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
        <el-select
          v-model="selectedTeamId"
          placeholder="选择团队"
          class="team-select"
          @change="handleFilterChange"
        >
          <el-option
            v-if="userStore.isSuperAdmin"
            :value="null"
            label="全部团队"
          >
            <span style="font-weight: 600; color: var(--el-color-primary);">全部团队</span>
          </el-option>
          <el-option
            v-for="team in availableTeams"
            :key="team.id"
            :value="team.id"
            :label="team.name"
          />
        </el-select>
        <el-select
          v-model="windowDays"
          class="days-select"
          @change="handleFilterChange"
        >
          <el-option :value="7" label="最近 7 天" />
          <el-option :value="14" label="最近 14 天" />
          <el-option :value="30" label="最近 30 天" />
        </el-select>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <div class="kpi-grid" v-loading="loadingOverview">
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
    <div class="charts-row">
      <div class="chart-card">
        <div class="chart-card__header">
          <div>
            <div class="chart-card__title">{{ windowDays }} 日调用趋势</div>
            <div class="chart-card__hint">每日调用次数</div>
          </div>
        </div>
        <div ref="trendChartRef" class="chart" v-loading="loadingTrend"></div>
      </div>
      <div class="chart-card">
        <div class="chart-card__header">
          <div>
            <div class="chart-card__title">接口热度 TOP 10</div>
            <div class="chart-card__hint">{{ windowDays }} 日内调用最多的接口</div>
          </div>
        </div>
        <div ref="topChartRef" class="chart" v-loading="loadingTop"></div>
        <p v-if="!loadingTop && topApis.length === 0" class="chart-empty">
          窗口内暂无调用数据
        </p>
      </div>
    </div>

    <!-- 僵尸接口 -->
    <div class="zombie-card">
      <div class="zombie-card__header">
        <div>
          <div class="chart-card__title">
            僵尸接口
            <span class="zombie-badge">{{ zombieTotal }} 个</span>
          </div>
          <div class="chart-card__hint">超过 30 天未被调用的启用接口，建议清理</div>
        </div>
      </div>
      <el-table
        class="soft-table"
        :data="zombies"
        v-loading="loadingZombies"
        empty-text="没有僵尸接口"
      >
        <el-table-column
          v-if="userStore.isSuperAdmin && selectedTeamId === null"
          label="团队"
          width="100"
        >
          <template #default="{ row }">
            <TeamTag :identifier="row.teamIdentifier" />
          </template>
        </el-table-column>
        <el-table-column label="方法" width="90">
          <template #default="{ row }">
            <HttpMethodTag :method="row.method" />
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="240">
          <template #default="{ row }">
            <span class="path-text">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
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
              编辑<el-icon class="btn-icon"><ArrowRight /></el-icon>
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
/**
 * 使用统计页
 *
 * 数据来源：
 * - KPI / 趋势 / TOP-N：实时聚合 request_log，受日志清理窗口约束
 * - 僵尸接口：基于 api_definition.last_called_at，永久累计不受日志清理影响
 *
 * 权限：超管可看「全部团队」与任意单团队；团队管理员仅可看自己 role=TEAM_ADMIN 的团队；
 * 普通成员被路由守卫拦截，看不到本页。
 */
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getOverview, getTrend, getTopApis, getZombies } from '@/api/stats'
import {
  Connection,
  Lightning,
  DataLine,
  Timer,
  ArrowRight
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import HttpMethodTag from '@/components/HttpMethodTag.vue'
import TeamTag from '@/components/TeamTag.vue'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

// ========== 过滤器状态 ==========
// 默认：超管 → 全部团队（null）；团队管理员 → 第一个管理员身份的团队
const selectedTeamId = ref(null)
const windowDays = ref(7)

// 当前用户能在下拉里看到的团队列表
// 超管：全部团队（appStore.teams）
// 团队管理员：仅 role=TEAM_ADMIN 的团队（前端依靠 user.teams 过滤）
const availableTeams = computed(() => {
  if (userStore.isSuperAdmin) {
    return appStore.teams
  }
  const adminTeamIds = new Set(userStore.teamAdminTeams.map(t => t.teamId))
  return appStore.teams.filter(t => adminTeamIds.has(t.id))
})

// ========== 数据状态 ==========
const overview = ref({
  enabledApiCount: 0,
  todayCallCount: 0,
  windowCallCount: 0,
  avgDurationMs: 0
})
const trendData = ref([]) // [{date, count}]
const topApis = ref([])
const zombies = ref([])
const zombieTotal = ref(0)

const loadingOverview = ref(false)
const loadingTrend = ref(false)
const loadingTop = ref(false)
const loadingZombies = ref(false)

// ========== ECharts 实例 ==========
const trendChartRef = ref(null)
const topChartRef = ref(null)
let trendChart = null
let topChart = null

// ========== 数字格式化（千分位） ==========
function formatNumber(n) {
  if (n == null) return '0'
  return Number(n).toLocaleString('zh-CN')
}

// 相对时间："3 分钟前"、"2 小时前"、"5 天前"…
function formatRelativeTime(iso) {
  if (!iso) return '从未调用'
  const t = new Date(iso.replace(' ', 'T')).getTime()
  if (isNaN(t)) return iso
  const diff = Date.now() - t
  if (diff < 60_000) return '刚刚'
  const min = Math.floor(diff / 60_000)
  if (min < 60) return min + ' 分钟前'
  const hr = Math.floor(min / 60)
  if (hr < 24) return hr + ' 小时前'
  const day = Math.floor(hr / 24)
  if (day < 30) return day + ' 天前'
  const month = Math.floor(day / 30)
  if (month < 12) return month + ' 个月前'
  return Math.floor(month / 12) + ' 年前'
}

// ========== 数据加载 ==========
async function loadAll() {
  await Promise.all([
    loadOverview(),
    loadTrend(),
    loadTopApis(),
    loadZombies()
  ])
}

async function loadOverview() {
  loadingOverview.value = true
  try {
    overview.value = await getOverview({
      teamId: selectedTeamId.value,
      days: windowDays.value
    })
  } finally {
    loadingOverview.value = false
  }
}

async function loadTrend() {
  loadingTrend.value = true
  try {
    trendData.value = await getTrend({
      teamId: selectedTeamId.value,
      days: windowDays.value
    })
    await nextTick()
    renderTrendChart()
  } finally {
    loadingTrend.value = false
  }
}

async function loadTopApis() {
  loadingTop.value = true
  try {
    topApis.value = await getTopApis({
      teamId: selectedTeamId.value,
      days: windowDays.value,
      limit: 10
    })
    await nextTick()
    renderTopChart()
  } finally {
    loadingTop.value = false
  }
}

async function loadZombies() {
  loadingZombies.value = true
  try {
    const data = await getZombies({
      teamId: selectedTeamId.value,
      days: 30,
      limit: 20
    })
    zombies.value = data.list || []
    zombieTotal.value = data.total || 0
  } finally {
    loadingZombies.value = false
  }
}

// ========== 图表渲染 ==========
function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const dates = trendData.value.map(p => p.date.slice(5)) // MM-dd
  const counts = trendData.value.map(p => p.count)
  trendChart.setOption({
    grid: { left: 48, right: 24, top: 24, bottom: 36 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1B2559',
      borderColor: 'transparent',
      textStyle: { color: '#fff', fontSize: 12 },
      padding: 10
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#F1F5F9' } },
      axisTick: { show: false },
      axisLabel: { color: '#A3AED0', fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#F1F5F9', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#A3AED0', fontSize: 12 }
    },
    series: [{
      data: counts,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { color: '#6366F1', width: 3 },
      itemStyle: { color: '#6366F1', borderColor: '#fff', borderWidth: 2 },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(99, 102, 241, 0.25)' },
            { offset: 1, color: 'rgba(99, 102, 241, 0.02)' }
          ]
        }
      }
    }]
  })
}

function renderTopChart() {
  if (!topChartRef.value) return
  if (!topChart) {
    topChart = echarts.init(topChartRef.value)
  }
  // 数据少于 1 条时不渲染（前端模板会显示 empty 提示）
  if (topApis.value.length === 0) {
    topChart.clear()
    return
  }
  // ECharts 横向柱状图：y 轴是 category，倒序显示 TOP 1 在最上
  const reversed = [...topApis.value].reverse()
  const yLabels = reversed.map(a => a.path || '(已删除)')
  const counts = reversed.map(a => a.callCount)
  topChart.setOption({
    grid: { left: 160, right: 50, top: 10, bottom: 10 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1B2559',
      borderColor: 'transparent',
      textStyle: { color: '#fff', fontSize: 12 },
      padding: 10,
      axisPointer: { type: 'shadow' }
    },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false }
    },
    yAxis: {
      type: 'category',
      data: yLabels,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: '#4A5568',
        fontSize: 12,
        fontFamily: 'SF Mono, Menlo, monospace',
        formatter: (val) => val.length > 22 ? val.slice(0, 22) + '…' : val
      }
    },
    series: [{
      data: counts,
      type: 'bar',
      barWidth: 14,
      label: {
        show: true,
        position: 'right',
        color: '#1B2559',
        fontSize: 12,
        fontWeight: 600
      },
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: {
          type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [
            { offset: 0, color: '#A5B4FC' },
            { offset: 1, color: '#6366F1' }
          ]
        }
      }
    }]
  })
}

// ========== 事件处理 ==========
function handleFilterChange() {
  loadAll()
}

function handleEditApi(apiId) {
  router.push(`/apis/${apiId}/edit`)
}

function handleResize() {
  if (trendChart) trendChart.resize()
  if (topChart) topChart.resize()
}

// ========== 初始化 ==========
onMounted(async () => {
  // 团队列表（appStore 缓存）
  if (appStore.teams.length === 0) {
    await appStore.loadTeams()
  }

  // 默认选中：超管 → 全部团队（null）；团队管理员 → 第一个管理员身份的团队
  if (!userStore.isSuperAdmin) {
    const firstAdminTeam = userStore.teamAdminTeams[0]
    selectedTeamId.value = firstAdminTeam ? firstAdminTeam.teamId : null
  }

  await loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
  if (topChart) {
    topChart.dispose()
    topChart = null
  }
})
</script>

<style lang="scss" scoped>
.page-stats {
  padding: 0;
}

// ========== 页头 ==========
.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.stats-header__title .page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1B2559;
  margin: 0 0 4px;
}

.stats-header__title .page-subtitle {
  font-size: 13px;
  color: #A3AED0;
  margin: 0;
}

.stats-header__filters {
  display: flex;
  gap: 12px;
  align-items: center;
}

.team-select {
  width: 180px;
}

.days-select {
  width: 140px;
}

// ========== KPI 卡片 ==========
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.kpi {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px 24px;
  position: relative;
  min-height: 116px;
}

.kpi__icon {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.kpi__icon--primary { background: #E0E7FF; color: #6366F1; }
.kpi__icon--success { background: #DCFCE7; color: #15803D; }
.kpi__icon--warning { background: #FEF3C7; color: #B45309; }
.kpi__icon--info { background: #E0E7FF; color: #6366F1; }

.kpi__label {
  font-size: 13px;
  color: #A3AED0;
  font-weight: 500;
  margin-bottom: 8px;
}

.kpi__value {
  font-size: 30px;
  font-weight: 700;
  color: #1B2559;
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.kpi__unit {
  font-size: 18px;
  color: #A3AED0;
  font-weight: 500;
  margin-left: 2px;
}

.kpi__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #A3AED0;
}

// ========== 图表卡片 ==========
.charts-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px 24px;
}

.chart-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.chart-card__title {
  font-size: 16px;
  font-weight: 600;
  color: #1B2559;
}

.chart-card__hint {
  font-size: 12px;
  color: #A3AED0;
  margin-top: 2px;
}

.chart {
  width: 100%;
  height: 280px;
}

.chart-empty {
  text-align: center;
  color: #A3AED0;
  font-size: 14px;
  margin: -240px 0 60px;
  pointer-events: none;
}

// ========== 僵尸接口卡片 ==========
.zombie-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px 24px 8px;
}

.zombie-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.zombie-badge {
  display: inline-block;
  margin-left: 8px;
  background: #FEE2E2;
  color: #B91C1C;
  font-size: 12px;
  font-weight: 700;
  padding: 2px 10px;
  border-radius: 20px;
}

.zombie-card__more {
  text-align: center;
  font-size: 12px;
  color: #A3AED0;
  margin: 12px 0 4px;
}

// ========== 通用 ==========
.path-text {
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 13px;
  color: #4A5568;
}

.duration-text {
  color: #A3AED0;
  font-size: 13px;
}

.btn-icon {
  margin-left: 4px;
  font-size: 12px;
}

// ========== 表格（沿用 LogView 的扁平表格风格） ==========
.soft-table {
  --el-table-border-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: #F7F8FF;

  :deep(th.el-table__cell) {
    border-bottom: 1px solid #F1F5F9 !important;
  }
  :deep(td.el-table__cell) {
    border-bottom: 1px solid #F8FAFC !important;
  }
  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }
}
</style>
