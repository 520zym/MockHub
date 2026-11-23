import request from './request'

/** Read-only selection metadata. Never use mock dispatch for editor previews. */
export function previewApiMatch(payload, { signal } = {}) {
  return request.post('/apis/preview-match', payload, { signal, skipGlobalNetworkError: true })
}
