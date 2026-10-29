import request from './request'

export const uploadWsdl = (formData) => request.post('/soap/wsdl/upload', formData)

export const getWsdlOperations = (teamId, fileName) => request.get('/soap/wsdl/operations', { params: { teamId, fileName } })
