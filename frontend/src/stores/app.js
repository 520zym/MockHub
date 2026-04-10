import { defineStore } from 'pinia'
import { getTeams } from '@/api/teams'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: false,
    teams: [],
    currentTeamId: null
  }),

  getters: {
    currentTeam: (state) => state.teams.find(t => t.id === state.currentTeamId) || null
  },

  actions: {
    async loadTeams() {
      this.teams = await getTeams()
    },

    setFilter(teamId) {
      this.currentTeamId = teamId
    },

    clearFilter() {
      this.currentTeamId = null
    },

    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    }
  }
})
