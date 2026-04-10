import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器：自动注入 JWT
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  response => {
    const res = response.data

    // 非标准响应（如文件下载），直接返回
    if (response.config.responseType === 'blob') {
      return res
    }

    if (res.code !== 0) {
      // 40002: Token 过期或无效 → 清 token 跳转登录
      if (res.code === 40002) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
        return Promise.reject(res)
      }
      // 其他业务错误 → 弹出错误提示
      ElMessage.error(res.msg || '操作失败')
      return Promise.reject(res)
    }

    // 成功时直接返回 data 部分（解包）
    return res.data
  },
  error => {
    // HTTP 401 → 清 token 跳转登录
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
      return Promise.reject(error)
    }

    ElMessage.error('网络错误，请检查连接')
    return Promise.reject(error)
  }
)

export default request
