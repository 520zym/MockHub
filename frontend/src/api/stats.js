import request from './request'

/**
 * 统计页面 API 客户端
 *
 * 所有方法接受 params 对象（teamId、days、limit），由 axios 拼成 query string；
 * teamId 留空表示跨团队聚合（仅超管允许，后端会做权限校验）。
 */

// KPI 总览：启用接口数 / 今日调用 / 窗口内总调用 / 平均响应耗时
export const getOverview = (params) => request.get('/stats/overview', { params })

// 调用趋势：返回连续 N 天的每日调用次数（缺失日已填 0）
export const getTrend = (params) => request.get('/stats/trend', { params })

// 接口热度 TOP-N
export const getTopApis = (params) => request.get('/stats/top-apis', { params })

// 僵尸接口列表 + 总数（{ list, total }）
export const getZombies = (params) => request.get('/stats/zombies', { params })
