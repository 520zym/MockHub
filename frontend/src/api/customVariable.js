/**
 * 自定义动态变量 REST 客户端
 *
 * 封装 /api/teams/:teamId/variables 下的所有端点，统一通过 request 实例发请求
 * （已自动携带 JWT 并拆包 res.data）。
 */
import request from './request'

/** 列出团队全部变量（聚合视图含值与分组） */
export const getTeamVariables = (teamId) =>
  request.get(`/teams/${teamId}/variables`)

/** 创建变量 */
export const createVariable = (teamId, data) =>
  request.post(`/teams/${teamId}/variables`, data)

/** 更新变量（改名/描述） */
export const updateVariable = (teamId, id, data) =>
  request.put(`/teams/${teamId}/variables/${id}`, data)

/** 删除变量（级联清理值、分组、关联） */
export const deleteVariable = (teamId, id) =>
  request.delete(`/teams/${teamId}/variables/${id}`)

/** 批量新增候选值，返回 { inserted, skipped } */
export const batchInsertValues = (teamId, variableId, values) =>
  request.post(`/teams/${teamId}/variables/${variableId}/values/batch`, { values })

/** 单条新增候选值 */
export const addValue = (teamId, variableId, data) =>
  request.post(`/teams/${teamId}/variables/${variableId}/values`, data)

/** 编辑候选值 */
export const updateValue = (teamId, variableId, valueId, data) =>
  request.put(`/teams/${teamId}/variables/${variableId}/values/${valueId}`, data)

/** 删除候选值 */
export const deleteValue = (teamId, variableId, valueId) =>
  request.delete(`/teams/${teamId}/variables/${variableId}/values/${valueId}`)

/** 创建分组（可带初始成员 valueIds） */
export const createGroup = (teamId, variableId, data) =>
  request.post(`/teams/${teamId}/variables/${variableId}/groups`, data)

/** 更新分组（改名/描述/成员） */
export const updateGroup = (teamId, variableId, groupId, data) =>
  request.put(`/teams/${teamId}/variables/${variableId}/groups/${groupId}`, data)

/** 删除分组 */
export const deleteGroup = (teamId, variableId, groupId) =>
  request.delete(`/teams/${teamId}/variables/${variableId}/groups/${groupId}`)
