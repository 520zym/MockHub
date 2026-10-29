// Explicit transport stub. These outcomes are fixtures, NOT a JavaScript condition evaluator.
export const fixtureState = { apiStub: true, reason: 'CONDITION_MATCH', calls: [], hold: false, deferred: [], saved: false }
const candidates = [{ responseId: 'alipay', responseName: '支付宝渠道', index: 0, responseCode: 200, bodyType: 'TEXT' }, { responseId: 'sandbox', responseName: '沙箱', index: 1, responseCode: 403, bodyType: 'TEXT' }, { responseId: 'default', responseName: '默认响应', index: 2, responseCode: 200, bodyType: 'TEXT' }]
export default {
  async get(url) { fixtureState.calls.push({ method: 'GET', url }); if (url === '/ai/status') return { visible: true, enabled: true, available: true, capabilities: { conditions: true } }; throw new Error('Unimplemented explicit API stub: GET ' + url) },
  async post(url, body, config) {
    fixtureState.calls.push({ method: 'POST', url, body, config })
    if (url !== '/apis/preview-match') throw new Error('Unimplemented explicit API stub: POST ' + url)
    const reason = fixtureState.reason, saved = fixtureState.saved
    if (fixtureState.hold) await new Promise(resolve => fixtureState.deferred.push(resolve)) // deliberately ignore abort to test the identity fence
    const base = { reason, mode: reason === 'RANDOM_EQUAL_PROBABILITY' ? 'RANDOM' : 'CONDITION', enabledCount: reason === 'NO_ENABLED_RESPONSE' ? 0 : reason === 'SINGLE_ENABLED' ? 1 : 3, usingSavedConfiguration: saved, candidates: [] }
    if (reason === 'RANDOM_EQUAL_PROBABILITY') return { ...base, candidates }
    if (reason === 'NO_MATCH' || reason === 'NO_ENABLED_RESPONSE') return base
    return { ...base, matchedIndex: reason === 'FALLBACK' ? 2 : 0, matchedResponseId: saved ? 'saved-only' : 'alipay', matchedResponseName: saved ? '保存的返回体' : reason === 'FALLBACK' ? '默认响应' : '支付宝渠道', responseCode: 200, bodyType: 'TEXT' }
  }
}
