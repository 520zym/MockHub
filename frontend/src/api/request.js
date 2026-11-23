import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({ baseURL: '/api', timeout: 30000 })
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

let lastAuthNotice = 0
function expireSession(message) {
  localStorage.removeItem('token'); localStorage.removeItem('user')
  window.dispatchEvent(new CustomEvent('mockhub:authentication-expired'))
  router.replace('/login').catch(() => {})
  if (Date.now() - lastAuthNotice > 1000) { ElMessage.error(message || '登录已失效，请重新登录'); lastAuthNotice = Date.now() }
}
function permissionChanged(code) {
  if ([40101, 40102, 40104, 40403, 40903].includes(Number(code))) window.dispatchEvent(new CustomEvent('mockhub:permissions-changed', { detail: { code } }))
}
function businessFailure(res) {
  if (Number(res.code) === 40002) expireSession(res.msg)
  else { permissionChanged(res.code); ElMessage.error(res.msg || '操作失败') }
  return Promise.reject(res)
}
request.interceptors.response.use(async response => {
  const res = response.data
  if (response.config.responseType === 'blob') {
    // JSON 导出仍返回 Blob；只有明确的 Result 错误包装才走错误路径。
    if (res instanceof Blob && res.type.includes('json')) {
      try { const body = JSON.parse(await res.text()); if (typeof body.code === 'number' && body.code !== 0) return businessFailure(body) } catch { /* raw export remains a download */ }
    }
    return res
  }
  if (res.code !== 0) return businessFailure(res)
  return res.data
}, error => {
  const status = error.response?.status, body = error.response?.data
  error.isTimeout = error.code === 'ECONNABORTED' || error.message?.includes('timeout')
  error.isNetworkError = !error.response
  error.msg = body?.msg || body?.message
  if (status === 401) expireSession(error.msg)
  else if (status === 403) { permissionChanged(body?.code || 40102); ElMessage.error(error.msg || '当前账号没有此操作权限') }
  else if (status === 409) ElMessage.error(error.msg || '数据已被其他人修改，请刷新后重试')
  else if (error.response) ElMessage.error(error.msg || (status >= 500 ? '服务内部错误，请稍后重试' : '请求未能完成'))
  else if (!error.config?.skipGlobalNetworkError) ElMessage.error(error.isTimeout ? '请求超时，请稍后重试' : '无法连接服务，请检查连接')
  return Promise.reject(error)
})
export default request
