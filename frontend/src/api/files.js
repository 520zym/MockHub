import request from './request'

export const getFiles = (params) => request.get('/files', { params })

export const getFileDetail = (fileId) => request.get(`/files/${fileId}`)

export const uploadFile = (teamId, file, onUploadProgress) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/files/upload', formData, {
    params: { teamId },
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress
  })
}

export const updateFile = (fileId, data) => request.put(`/files/${fileId}`, data)

export const deleteFile = (fileId) => request.delete(`/files/${fileId}`)

export const batchDeleteFiles = (fileIds) => request.post('/files/batch-delete', { fileIds })

export const getFilePreview = (fileId) => request.get(`/files/${fileId}/preview`)
