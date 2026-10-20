import { defineStore } from 'pinia'
import { getAiStatus } from '@/api/ai'

/**
 * AI 状态缓存：入口是否渲染、各能力是否可用。
 * 状态由服务端按当前用户权限计算，前端只负责展示。
 */
export const useAiStore = defineStore('ai', {
  state: () => ({
    status: null,
    loading: false,
    loadedAt: 0
  }),

  getters: {
    enabled: (state) => !!state.status?.enabled,
    available: (state) => !!state.status?.available,
    reason: (state) => state.status?.reason || '',
    provider: (state) => state.status?.provider || '',
    capabilities: (state) => state.status?.capabilities || {}
  },

  actions: {
    /**
     * 拉取状态。默认 60 秒内复用缓存，避免每个组件重复请求。
     */
    async load(force = false) {
      const fresh = Date.now() - this.loadedAt < 60 * 1000
      if (this.status && fresh && !force) {
        return this.status
      }
      if (this.loading) {
        return this.status
      }
      this.loading = true
      try {
        this.status = await getAiStatus()
        this.loadedAt = Date.now()
      } catch (e) {
        this.status = {
          enabled: false,
          available: false,
          reason: 'AI 状态获取失败',
          capabilities: {}
        }
      } finally {
        this.loading = false
      }
      return this.status
    }
  }
})
