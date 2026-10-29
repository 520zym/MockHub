// Explicit API stub for isolated REAL Vue/Element Plus component tests. This is not backend integration.
import { workspaceInvoke } from './workspace-stub.js'
const snapshots = {
  1: { schemaVersion: 1, config: { name: '用户详情', method: 'GET', path: '/api/user/{id}', enabled: 1, type: 'SOAP', response_mode: 'CONDITION', soap_config: '{"operations":[{"operationName":"GetUser","responseMode":"CONDITION"}]}' }, responses: [{ id: 'response-1', name: '默认返回', response_code: 200, content_type: 'application/json', body_type: 'TEXT', is_active: 1, delay_ms: 0, sort_order: 0, response_body: '{"code":0,"message":"success"}', conditions: null }, { id: 'file-1', name: '下载附件', body_type: 'FILE', is_active: 0, file_name: 'users-v1.csv', file_path: 'team-a/v1.csv', file_size: 320 }], tags: [{ id: 'tag-1', name: '用户', color: '#3568d4' }], group: [] },
  2: { schemaVersion: 1, config: { name: '用户详情', method: 'GET', path: '/api/user/{id}', enabled: 1, type: 'SOAP', response_mode: 'CONDITION', soap_config: '{"operations":[{"operationName":"GetUser","responseMode":"CONDITION"}]}' }, responses: [{ id: 'response-2', name: '默认返回', response_code: 200, content_type: 'application/json', body_type: 'TEXT', is_active: 1, delay_ms: 200, sort_order: 0, response_body: '{"code":0,"message":"用户信息","data":{"name":"MockHub"}}', conditions: '{"conditions":[{"source":"QUERY","path":"status","operator":"EQ","value":"active"}]}' }, { id: 'file-2', name: '下载附件', body_type: 'FILE', is_active: 0, file_name: 'users-v1.csv', file_path: 'team-a/v1.csv', file_size: 320 }], tags: [{ id: 'tag-1', name: '用户', color: '#3568d4' }], group: [] },
  3: { schemaVersion: 1, config: { name: '用户详情与状态', method: 'GET', path: '/api/user/{id}', enabled: 1, type: 'SOAP', response_mode: 'CONDITION', soap_config: '{"operations":[{"operationName":"GetUser","responseMode":"RANDOM"},{"operationName":"GetStatus","responseMode":"CONDITION"}]}' }, responses: [{ id: 'response-3', name: '默认返回', response_code: 200, content_type: 'application/json', body_type: 'TEXT', is_active: 1, delay_ms: 100, sort_order: 0, response_body: '{"code":0,"message":"查询成功","data":{"name":"MockHub","status":"active"}}', conditions: '{"conditions":[{"source":"QUERY","path":"status","operator":"EQ","value":"active"},{"source":"HEADER","path":"x-client","operator":"EQ","value":"web"}]}' }, { id: 'file-3', name: '下载附件', body_type: 'FILE', is_active: 0, file_name: 'users-v2.csv', file_path: 'team-a/v2.csv', file_size: 640 }], tags: [{ id: 'tag-1', name: '用户', color: '#3568d4' }], group: [] }
}
export const fixtureState = { calls: [], conflict: false, deferred: [], holdHistory: false, revisions: { 'api-a': 3, 'api-b': 3 }, role: 'EDITOR', teamId: 'team-a', snapshots, apiStub: true }
window.fixtureState = fixtureState
function metadata(apiId, revision) { return { id: 'rev-' + revision, apiId, teamId: fixtureState.rows?.find(row=>row.id===apiId)?.teamId || fixtureState.trash?.find(row=>row.id===apiId)?.teamId || (apiId === 'api-b' ? 'team-b' : 'team-a'), revision, action: revision === 1 ? 'CREATE' : revision > 3 ? 'ROLLBACK' : 'UPDATE', actorNameSnapshot: ['系统', '陈晨', '李哲', '王琳'][Math.min(revision, 3)], source: revision === 2 ? 'AI' : 'WEB', createdAt: '2026-10-29T0' + Math.min(revision, 8) + ':12:00Z', snapshotSchemaVersion: fixtureState.snapshotSchemaVersion || 1 } }
async function invoke(method, url, body, config) {
  fixtureState.calls.push({ method, url, body, params: config?.params })
  const history = url.match(/^\/apis\/([^/]+)\/revisions(?:\/(rev-\d+))?(\/rollback)?$/)
  if (history) {
    if (fixtureState.holdHistory && method === 'GET') await new Promise(resolve => fixtureState.deferred.push(resolve))
    const [, apiId, id, rollback] = history, latest = fixtureState.revisions[apiId]
    if (method === 'GET' && !id) return Array.from({ length: latest }, (_, index) => metadata(apiId, latest - index))
    if (method === 'GET') { const revision = Number(id.slice(4)), snapshot = structuredClone(snapshots[revision] || snapshots[2]); if (apiId === 'api-b') snapshot.config.name = 'PAY-' + revision; return { ...metadata(apiId, revision), snapshot } }
    if (rollback && method === 'POST') {
      if (fixtureState.role === 'VIEWER') throw { code: 40104, msg: '只读角色不能回滚' }
      if (fixtureState.conflict || body.expectedRevision !== latest) throw { code: 40921, msg: '接口已被其他人修改，请刷新' }
      fixtureState.revisions[apiId]++; snapshots[latest + 1] = structuredClone(snapshots[Number(id.slice(4))]); return { id: apiId, revision: latest + 1 }
    }
  }
  const workspace = await workspaceInvoke(fixtureState, method, url, body, config)
  if (workspace.handled) return workspace.data
  if (url === '/auth/me') return window.fixtureUser
  if (url === '/teams') return (window.fixtureTeams || []).map(team => ({ ...team, apiCount: fixtureState.rows.filter(row => row.teamId === team.id).length }))
  throw new Error('Unimplemented explicit fixture API: ' + method + ' ' + url)
}
export default { get: (url, config) => invoke('GET', url, undefined, config), post: (url, body, config) => invoke('POST', url, body, config), put: (url, body, config) => invoke('PUT', url, body, config), delete: (url, config) => invoke('DELETE', url, undefined, config) }
