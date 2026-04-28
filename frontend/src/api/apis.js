import request from './request'

export const getApis = (params) => request.get('/apis', { params })

export const getApiDetail = (id) => request.get(`/apis/${id}`)

export const createApi = (data) => request.post('/apis', data)

export const updateApi = (id, data) => request.put(`/apis/${id}`, data)

export const deleteApi = (id) => request.delete(`/apis/${id}`)

export const copyApi = (id) => request.post(`/apis/${id}/copy`)

export const toggleApi = (id) => request.put(`/apis/${id}/toggle`)

export const importApis = (formData) => request.post('/apis/import', formData)

export const exportApis = (teamId) => request.get('/apis/export', { params: { teamId }, responseType: 'blob' })

/**
 * 批量操作接口
 * @param {Object} payload { action: 'enable'|'disable'|'delete'|'move-group', ids: [], targetGroupId? }
 */
export const batchApis = (payload) => request.post('/apis/batch', payload)

/**
 * 路径冲突预检（编辑页实时校验用）
 * @param {Object} params { teamId, method, path, excludeId? }
 * @returns {Promise<{conflict: boolean, name: string|null}>}
 */
export const checkApiPath = (params) => request.get('/apis/check-path', { params })
