import request from './request'

export const getUsers = () => request.get('/users')

export const createUser = (data) => request.post('/users', data)

export const updateUser = (id, data) => request.put(`/users/${id}`, data)

export const deleteUser = (id) => request.delete(`/users/${id}`)

export const assignTeams = (id, data) => request.post(`/users/${id}/teams`, data)
