import request from './request'

// AI 能力调用统一走 120s 超时，避免首次生成被默认 30s 截断
const AI_CONFIG = { timeout: 120000, skipGlobalNetworkError: true }

/** 当前用户可见的 AI 状态（是否启用、可用能力） */
export function getAiStatus() {
  return request.get('/ai/status')
}

/** 读取 AI 配置（仅超管） */
export function getAiSettings() {
  return request.get('/ai/settings')
}

/** 保存 AI 配置（仅超管） */
export function saveAiSettings(data) {
  return request.put('/ai/settings', data)
}

/** 内置 AI 供应商预设（仅超管） */
export function getAiProviders() {
  return request.get('/ai/providers')
}

/** 从服务商拉取模型列表（仅超管） */
export function getAiModels() {
  return request.get('/ai/models', { timeout: 20000, skipGlobalNetworkError: true })
}

/** 测试模型连通性（仅超管） */
export function testAiConnection() {
  return request.post('/ai/settings/test', null, AI_CONFIG)
}

/** 生成响应体（REST JSON / SOAP XML） */
export function generateAiResponse(data) {
  return request.post('/ai/generate-response', data, AI_CONFIG)
}

/** 自然语言转条件规则 */
export function generateAiConditions(data) {
  return request.post('/ai/generate-conditions', data, AI_CONFIG)
}

/** 解释一次请求的命中链路 */
export function explainAiMatch(data) {
  return request.post('/ai/explain-match', data, AI_CONFIG)
}

/** 对话助手 */
export function aiChat(data) {
  return request.post('/ai/chat', data, AI_CONFIG)
}

/** 查询待确认操作状态 */
export function getAiAction(actionId) {
  return request.get('/ai/actions/' + actionId)
}

/** 确认待确认操作 */
export function confirmAiAction(actionId) {
  return request.post('/ai/actions/' + actionId + '/confirm', null, AI_CONFIG)
}

/** 取消待确认操作 */
export function cancelAiAction(actionId) {
  return request.post('/ai/actions/' + actionId + '/cancel', null, AI_CONFIG)
}