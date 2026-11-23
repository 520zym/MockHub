/** Format JSON / XML body for read-only previews. */
export function formatStructuredBody(value) {
  const raw = String(value == null ? '' : value).trim()
  if (!raw) return ''
  if (raw[0] === '{' || raw[0] === '[') {
    try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
  }
  if (raw[0] === '<') return formatXml(raw)
  return raw
}

/** Simple XML indentation; tolerant of malformed input. */
function formatXml(xml) {
  const padding = '  '
  const compact = xml
    .replace(/\r?\n/g, '')
    .replace(/>\s+</g, '><')
    .replace(/(>)(<)(\/*)/g, '$1\n$2$3')
  let pad = 0
  return compact.split('\n').map(line => {
    let indent = 0
    if (/^<\/\w/.test(line)) pad = Math.max(pad - 1, 0)
    else if (/^<\w[^>]*[^/]>.*$/.test(line) && !/<.+<\/.+>/.test(line)) indent = 1
    const result = padding.repeat(pad) + line
    pad += indent
    return result
  }).join('\n')
}
