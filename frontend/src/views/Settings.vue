<!--
  Settings.vue — 全局设置页面
  功能：超级管理员配置日志保留策略和 Mock CORS 开关
  视觉：Soft UI 风格卡片式布局
-->
<template>
  <div class="page-settings">
    <!-- 设置卡片 -->
    <div class="settings-card" v-loading="loading">
      <el-form label-position="top" class="settings-form">
        <!-- 日志保留策略 -->
        <div class="settings-section">
          <h3 class="section-title">日志保留策略</h3>
          <p class="section-desc">配置操作日志和请求日志的自动清理规则</p>

          <el-form-item label="保留模式">
            <el-radio-group v-model="form.logRetainMode">
              <el-radio value="count">按条数保留</el-radio>
              <el-radio value="days">按天数保留</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="form.logRetainMode === 'count'" label="保留条数">
            <el-input-number
              v-model="form.logRetainCount"
              :min="100"
              :max="100000"
              :step="100"
              controls-position="right"
            />
            <span class="input-hint">超出部分将自动删除最旧的记录</span>
          </el-form-item>

          <el-form-item v-if="form.logRetainMode === 'days'" label="保留天数">
            <el-input-number
              v-model="form.logRetainDays"
              :min="1"
              :max="365"
              :step="1"
              controls-position="right"
            />
            <span class="input-hint">超出天数的日志将被自动清理</span>
          </el-form-item>
        </div>

        <!-- 分割线 -->
        <div class="section-divider"></div>

        <!-- 服务器地址 -->
        <div class="settings-section">
          <h3 class="section-title">服务器地址</h3>
          <p class="section-desc">用于拼接 Mock URL，为空时自动检测内网 IP</p>

          <el-form-item label="服务器地址">
            <el-input
              v-model="form.serverAddress"
              placeholder="如：http://192.168.1.100:8080"
              clearable
              style="width: 400px"
            />
          </el-form-item>
        </div>

        <!-- 分割线 -->
        <div class="section-divider"></div>

        <!-- Mock CORS -->
        <div class="settings-section">
          <h3 class="section-title">Mock CORS</h3>
          <p class="section-desc">启用后，Mock 接口允许所有跨域请求</p>

          <div class="cors-switch-row">
            <el-switch
              v-model="form.mockCorsEnabled"
              active-text="已启用"
              inactive-text="已禁用"
            />
          </div>
        </div>

        <!-- 保存按钮 -->
        <div class="settings-footer">
          <el-button
            type="primary"
            :loading="saving"
            class="save-btn"
            @click="handleSave"
          >
            保存设置
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, saveSettings } from '@/api/settings'

// ========== 状态 ==========
const loading = ref(false)
const saving = ref(false)

const form = reactive({
  logRetainMode: 'count',
  logRetainCount: 1000,
  logRetainDays: 30,
  mockCorsEnabled: true,
  serverAddress: ''
})

// ========== 数据加载 ==========
async function loadSettings() {
  loading.value = true
  try {
    const data = await getSettings()
    form.logRetainMode = data.logRetainMode || 'count'
    form.logRetainCount = data.logRetainCount || 1000
    form.logRetainDays = data.logRetainDays || 30
    form.mockCorsEnabled = data.mockCorsEnabled !== false
    form.serverAddress = data.serverAddress || ''
  } catch (err) {
    // 拦截器已处理错误提示
  } finally {
    loading.value = false
  }
}

// ========== 保存 ==========
async function handleSave() {
  saving.value = true
  try {
    await saveSettings({
      logRetainMode: form.logRetainMode,
      logRetainCount: form.logRetainCount,
      logRetainDays: form.logRetainDays,
      mockCorsEnabled: form.mockCorsEnabled,
      serverAddress: form.serverAddress
    })
    ElMessage.success('设置已保存')
  } catch (err) {
    // 拦截器已处理错误提示
  } finally {
    saving.value = false
  }
}

// ========== 初始化 ==========
onMounted(() => {
  loadSettings()
})
</script>

<style lang="scss" scoped>
.page-settings {
  padding: 0;
  max-width: 720px;
}

// ========== 设置卡片 ==========
.settings-card {
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  padding: 28px 32px;
}

// ========== 区块 ==========
.settings-section {
  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: #1B2559;
    margin: 0 0 4px;
  }

  .section-desc {
    font-size: 13px;
    color: #A3AED0;
    margin: 0 0 20px;
  }
}

.section-divider {
  height: 1px;
  background: #F1F5F9;
  margin: 24px 0;
}

// ========== 表单项 ==========
.settings-form {
  :deep(.el-form-item__label) {
    font-size: 14px;
    font-weight: 500;
    color: #4A5568;
  }

  :deep(.el-radio__label) {
    color: #4A5568;
  }

  :deep(.el-input-number) {
    width: 200px;
  }
}

.input-hint {
  margin-left: 12px;
  font-size: 13px;
  color: #A3AED0;
}

.cors-switch-row {
  padding: 4px 0;
}

// ========== 保存按钮 ==========
.settings-footer {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #F1F5F9;
}

.save-btn {
  height: 40px;
  padding: 0 32px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 12px;
  border: none;
  background: #6366F1;
  transition: all 0.2s ease;

  &:hover,
  &:focus {
    background: #4F46E5;
    transform: translateY(-1px);
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);
  }

  &:active {
    transform: translateY(0);
  }
}
</style>
