export const TEAM_ROLE_LABELS = Object.freeze({ VIEWER: '只读成员', EDITOR: '可编辑成员', TEAM_ADMIN: '团队管理员' })

export function normalizeTeamRole(role) {
  if (role === 'MEMBER') return 'EDITOR'
  return Object.hasOwn(TEAM_ROLE_LABELS, role) ? role : null
}

export function roleLabel(team) {
  if (team?.accessMode === 'SYSTEM_MAINTENANCE') return '系统维护身份'
  return TEAM_ROLE_LABELS[normalizeTeamRole(team?.membershipRole ?? team?.role)] || '尚未加入团队'
}

export function teamCapability(user, teamId, action = 'read') {
  if (!['read', 'write', 'manageMembers', 'manageTeam', 'purge', 'overwriteImport', 'batchDelete'].includes(action)) return false
  const team = user?.teams?.find(t => t.teamId === teamId)
  if (!team || user?.status === 'DISABLED') return false
  const archived = team.teamStatus === 'ARCHIVED'
  if (archived && action !== 'read') return false
  if (team.capabilities && Object.hasOwn(team.capabilities, action)) return team.capabilities[action] === true
  // 兼容同一发布切换期间的旧响应，不把不存在的团队或未知角色默认为可写。
  const role = normalizeTeamRole(team.membershipRole ?? team.role)
  const maintenance = user.globalRole === 'SUPER_ADMIN' && team.accessMode === 'SYSTEM_MAINTENANCE'
  if (action === 'read') return !!role || maintenance
  if (action === 'write') return role === 'EDITOR' || role === 'TEAM_ADMIN' || maintenance
  return role === 'TEAM_ADMIN' || maintenance
}

export function safeStoredUser(value) {
  try {
    const user = JSON.parse(value || 'null')
    return user && typeof user === 'object' && !Array.isArray(user) ? user : null
  } catch { return null }
}

export function teamStorageKey(userId) { return userId ? 'mockhub:' + userId + ':currentTeamId' : null }

export function chooseTeam(teams, requestedId) {
  if (!teams?.length) return null
  const requested = teams.find(t => t.id === requestedId)
  return requested?.id || teams.find(t => t.status !== 'ARCHIVED')?.id || teams[0].id
}
