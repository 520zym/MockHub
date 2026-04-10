import request from './request'

export const getGroups = (teamId) => request.get('/groups', { params: { teamId } })

export const createGroup = (data) => request.post('/groups', data)

export const updateGroup = (id, data) => request.put(`/groups/${id}`, data)

export const deleteGroup = (id) => request.delete(`/groups/${id}`)
