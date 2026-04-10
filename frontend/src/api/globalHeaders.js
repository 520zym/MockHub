import request from './request'

export const getGlobalHeaders = (teamId) => request.get('/global-headers', { params: { teamId } })

export const saveGlobalHeaders = (teamId, data) => request.put('/global-headers', data, { params: { teamId } })
