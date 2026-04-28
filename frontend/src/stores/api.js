import { defineStore } from 'pinia'

/**
 * 接口列表筛选条件持久化（页面跳转后回来仍保留筛选）。
 * groupId：null 表示全部分组，'__none__' 表示仅"未分组"，其余值是分组 ID。
 * sortBy/sortDir：默认按修改时间倒序；其他可选 createdAt/name/path（白名单见后端）。
 */
export const useApiStore = defineStore('api', {
  state: () => ({
    listParams: {
      keyword: '',
      method: null,
      enabled: null,
      tagIds: [],
      type: null,
      groupId: null,
      sortBy: 'updatedAt',
      sortDir: 'desc',
      page: 1,
      size: 20
    }
  }),

  actions: {
    setParam(key, value) {
      this.listParams[key] = value
      if (key !== 'page') {
        this.listParams.page = 1
      }
    },

    resetParams() {
      this.listParams = {
        keyword: '',
        method: null,
        enabled: null,
        tagIds: [],
        type: null,
        groupId: null,
        sortBy: 'updatedAt',
        sortDir: 'desc',
        page: 1,
        size: 20
      }
    }
  }
})
