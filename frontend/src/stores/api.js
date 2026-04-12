import { defineStore } from 'pinia'

export const useApiStore = defineStore('api', {
  state: () => ({
    listParams: {
      keyword: '',
      method: null,
      enabled: null,
      tagIds: [],
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
        page: 1,
        size: 20
      }
    }
  }
})
