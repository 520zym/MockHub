import request from './request'

export const getOperationLogs = (params) => request.get('/logs/operation', { params })

export const getRequestLogs = (params) => request.get('/logs/request', { params })
