export function requireRevision(value) {
  if (!Number.isSafeInteger(value) || value < 1) {
    const error = new Error('缺少有效版本，请重新加载接口后再操作')
    error.code = 40920; error.msg = error.message; throw error
  }
  return value
}

export function selectedRevisions(ids, rows) {
  const indexed = new Map(rows.map(row => [row.id, row]))
  return Object.fromEntries(ids.map(id => [id, requireRevision(indexed.get(id)?.revision)]))
}
