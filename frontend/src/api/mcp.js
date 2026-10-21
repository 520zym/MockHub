import request from './request'

/** 读取 MCP 配置（仅超管） */
export function getMcpSettings() {
  return request.get('/mcp/settings')
}

/** 保存 MCP 配置（仅超管） */
export function saveMcpSettings(data) {
  return request.put('/mcp/settings', data)
}

/** Token 列表（仅超管） */
export function getMcpTokens() {
  return request.get('/mcp/tokens')
}

/** 创建 Token，返回明文仅一次（仅超管） */
export function createMcpToken(data) {
  return request.post('/mcp/tokens', data)
}

/** 撤销 Token（仅超管） */
export function revokeMcpToken(id) {
  return request.delete('/mcp/tokens/' + id)
}
