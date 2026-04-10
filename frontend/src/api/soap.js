import request from './request'

export const uploadWsdl = (formData) => request.post('/soap/wsdl/upload', formData)

export const getWsdlOperations = (fileName) => request.get(`/soap/wsdl/${fileName}/operations`)
