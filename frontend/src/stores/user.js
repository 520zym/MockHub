import { defineStore } from 'pinia'
import { getCurrentUser, endSession } from '@/api/auth'
import { safeStoredUser, teamCapability } from '@/utils/permissions'

let identityRequest = null
export const useUserStore = defineStore('user', {
  state: () => ({ token: localStorage.getItem('token') || '', user: safeStoredUser(localStorage.getItem('user')), identityReady: false }),
  getters: {
    isLoggedIn: state => !!state.token,
    isSuperAdmin: state => state.identityReady && state.user?.globalRole === 'SUPER_ADMIN',
    hasCapability: state => (teamId, action = 'read') => state.identityReady && teamCapability(state.user, teamId, action),
    canWriteTeam: state => teamId => state.identityReady && teamCapability(state.user, teamId, 'write'),
    isTeamAdmin: state => teamId => state.identityReady && teamCapability(state.user, teamId, 'manageMembers'),
    isAnyTeamAdmin: state => state.identityReady && state.user?.teams?.some(t => teamCapability(state.user, t.teamId, 'manageMembers')) || false,
    teamAdminTeams: state => (state.user?.teams || []).filter(t => teamCapability(state.user, t.teamId, 'manageMembers')),
    readableTeams: state => (state.user?.teams || []).filter(t => teamCapability(state.user, t.teamId, 'read')),
    userTeamIds: state => (state.user?.teams || []).filter(t => teamCapability(state.user, t.teamId, 'read')).map(t => t.teamId)
  },
  actions: {
    setLogin(token, user) {
      this.token = token; this.user = user; this.identityReady = true
      localStorage.setItem('token', token); localStorage.setItem('user', JSON.stringify(user))
    },
    clearSession() {
      this.token = ''; this.user = null; this.identityReady = false; identityRequest = null
      localStorage.removeItem('token'); localStorage.removeItem('user')
      window.dispatchEvent(new CustomEvent('mockhub:session-cleared'))
    },
    async logout() {
      try { if (this.token) await endSession() } finally { this.clearSession() }
    },
    updateUser(user) { this.user = user; this.identityReady = true; localStorage.setItem('user', JSON.stringify(user)) },
    async refreshIdentity() {
      if (!this.token) throw new Error('未登录')
      if (identityRequest) return identityRequest
      const token = this.token
      const pending = getCurrentUser().then(user => {
        if (this.token === token) this.updateUser(user)
        return user
      }).finally(() => { if (identityRequest === pending) identityRequest = null })
      identityRequest = pending
      return pending
    },
    async ensureIdentity() { if (!this.identityReady) await this.refreshIdentity(); return this.user }
  }
})
