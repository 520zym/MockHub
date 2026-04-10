import request from './request'

export const getTags = (teamId) => request.get('/tags', { params: { teamId } })

export const createTag = (data) => request.post('/tags', data)

export const updateTag = (id, data) => request.put(`/tags/${id}`, data)

export const deleteTag = (id) => request.delete(`/tags/${id}`)
