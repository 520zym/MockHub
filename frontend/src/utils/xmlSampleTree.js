/** 将 XML 示例转换为字段树，路径与后端 BODY 的点分元素路径保持一致。 */
export function parseXmlSampleTree(text) {
  if (/<!DOCTYPE/i.test(text)) {
    throw new Error('XML 示例不支持 DOCTYPE，请移除后再导入')
  }
  const doc = new DOMParser().parseFromString(text, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length || !doc.documentElement) {
    throw new Error('无法解析为 XML，请检查标签和命名空间声明')
  }
  return buildXmlNode(doc.documentElement, '')
}

function buildXmlNode(element, parentPath) {
  const key = element.localName
  // 当前匹配器不支持转义点号或同名元素索引，避免生成无法准确定位的条件。
  if (key.includes('.')) {
    throw new Error('XML 元素名含点号，暂不支持自动生成字段路径')
  }
  const path = parentPath ? `${parentPath}.${key}` : key
  const children = Array.from(element.children)
  if (!children.length) {
    // XML 文本保留空白和前导零，与后端 getTextContent() 的值一致。
    const value = element.textContent || ''
    const valueType = /^(?:0|[1-9]\d*)(?:\.\d+)?$|^-(?:0|[1-9]\d*)(?:\.\d+)?$/.test(value)
      ? 'NUMBER' : 'STRING'
    return { path, key, kind: 'leaf', value, valueType }
  }
  const names = new Set()
  for (const child of children) {
    if (names.has(child.localName)) {
      throw new Error(`XML 路径 ${path} 下有同名元素 ${child.localName}，暂不支持自动生成索引条件`)
    }
    names.add(child.localName)
  }
  return {
    path, key, kind: 'object', size: children.length,
    children: children.map(child => buildXmlNode(child, path))
  }
}
