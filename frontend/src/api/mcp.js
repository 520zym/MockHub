import request from './request'

/** 读取 MCP 配置（仅超管） */
export function getMcpSettings() {
  return request.get('/mcp/settings')
}

/** 保存 MCP 配置（仅超管） */
export function saveMcpSettings(data) {
  return request.put('/mcp/settings', data)
}

/** 本人的凭证列表，不含明文或哈希 */
export function getMcpTokens() {
  return request.get('/me/mcp-tokens')
}

/** 本人显式按队签发，明文仅创建响应可见 */
export function createMcpToken(data) {
  return request.post('/me/mcp-tokens', data)
}

/** 仅撤销本人的凭证及其关联草稿授权 */
export function revokeMcpToken(id) {
  return request.delete('/me/mcp-tokens/' + encodeURIComponent(id))
}
