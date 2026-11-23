import { defineStore } from 'pinia'
import { getTeams } from '@/api/teams'
import { useUserStore } from '@/stores/user'
import { useApiStore } from '@/stores/api'
import { chooseTeam, teamStorageKey } from '@/utils/permissions'

export const useAppStore = defineStore('app', {
  state: () => ({ sidebarCollapsed: false, teams: [], currentTeamId: null, apiRevision: 0 }),
  getters: { currentTeam: state => state.teams.find(t => t.id === state.currentTeamId) || null },
  actions: {
    async loadTeams() {
      const user = useUserStore()
      await user.ensureIdentity()
      const token = user.token
      const teams = await getTeams()
      if (user.token !== token) return
      const memberships = new Map((user.user?.teams || []).map(t => [t.teamId, t]))
      this.teams = (teams || []).filter(t => memberships.has(t.id)).map(t => ({ ...t, ...memberships.get(t.id), name: t.name, status: t.status || 'ACTIVE' }))
      const key = teamStorageKey(user.user?.id)
      const wanted = this.currentTeamId || (key ? sessionStorage.getItem(key) : null)
      this.setFilter(chooseTeam(this.teams, wanted))
    },
    setFilter(teamId) {
      if (teamId != null && !this.teams.some(t => t.id === teamId)) return false
      this.currentTeamId = teamId || null
      const key = teamStorageKey(useUserStore().user?.id)
      if (key) { if (teamId) sessionStorage.setItem(key, teamId); else sessionStorage.removeItem(key) }
      return true
    },
    clearFilter() { this.setFilter(chooseTeam(this.teams, null)) },
    reset() { this.teams = []; this.currentTeamId = null; this.apiRevision = 0; useApiStore().resetParams() },
    toggleSidebar() { this.sidebarCollapsed = !this.sidebarCollapsed },
    bumpApiRevision() { this.apiRevision++ }
  }
})
