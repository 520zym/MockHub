import request from './request'

export const getTeams = () => request.get('/teams')

export const createTeam = (data) => request.post('/teams', data)

export const updateTeam = (id, data) => request.put(`/teams/${id}`, data)

export const deleteTeam = (id) => request.delete(`/teams/${id}`)

export const getTeamMembers = (id) => request.get(`/teams/${id}/members`)

export const addTeamMember = (id, data) => request.post(`/teams/${id}/members`, data)

export const removeTeamMember = (id, userId) => request.delete(`/teams/${id}/members/${userId}`)

export const updateMemberRole = (id, userId, data) => request.put(`/teams/${id}/members/${userId}/role`, data)
