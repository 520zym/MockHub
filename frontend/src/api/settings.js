import axios from 'axios'
import request from './request'

export const getSettings = () => request.get('/settings')

export const saveSettings = (data) => request.put('/settings', data)

/**
 * 获取服务器地址（无需认证，返回非 Result 包装的原始数据）
 * 响应格式：{ address: "http://192.168.x.x:8080" }
 * 注意：该端点不使用 Result 包装，直接用原始 axios 绕过拦截器
 */
export const getServerAddress = () =>
  axios.get('/api/server-address').then(res => res.data)
