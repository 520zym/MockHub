import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isSuperAdmin: (state) => state.user?.globalRole === 'SUPER_ADMIN',
    isTeamAdmin: (state) => {
      return (teamId) => {
        if (state.user?.globalRole === 'SUPER_ADMIN') return true
        return state.user?.teams?.some(t => t.teamId === teamId && t.role === 'TEAM_ADMIN')
      }
    },
    // 用户在任意一个团队中是否为管理员（用于"统计"等不绑定具体团队的入口可见性）
    isAnyTeamAdmin: (state) => {
      if (state.user?.globalRole === 'SUPER_ADMIN') return true
      return state.user?.teams?.some(t => t.role === 'TEAM_ADMIN') || false
    },
    // 用户为管理员身份的团队列表（用于统计页面的团队下拉选择）
    teamAdminTeams: (state) => {
      if (!state.user?.teams) return []
      return state.user.teams.filter(t => t.role === 'TEAM_ADMIN')
    },
    userTeamIds: (state) => state.user?.teams?.map(t => t.teamId) || []
  },

  actions: {
    setLogin(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify(user))
    },

    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },

    updateUser(user) {
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
    }
  }
})
