/**
 * JSON 里的动态变量占位符工具。
 * <p>
 * MockHub 的占位符语法是 `{{name}}` / `{{group.name}}`，在返回体里通常直接写在值的位置
 * （`"id": {{uuid}}`），因此正文本身不是严格 JSON：Monaco 的 JSON 校验会报
 * "Expected comma"、格式化也会失败。这里统一提供"把占位符换成合法 JSON 再判断"的能力，
 * 让编辑器校验、格式化、审计等入口对占位符友好，同时不影响真正的语法错误。
 */

/** 与后端 DynamicVariableUtil 保持一致：{{word}} 或 {{word.word}} */
export const PLACEHOLDER_PATTERN = '\\{\\{(\\w+(?:\\.\\w+)?)\\}\\}'

function placeholderRegex() {
  // 每次新建，避免共享 global 正则的 lastIndex 在多次调用之间串味
  return new RegExp(PLACEHOLDER_PATTERN, 'g')
}

/** 正文里是否包含占位符 */
export function hasPlaceholder(text) {
  return placeholderRegex().test(text || '')
}

/** 把占位符替换成 JSON 合法值（默认 null），用于语法校验 */
export function maskPlaceholders(text, replacement) {
  return String(text || '').replace(placeholderRegex(), replacement === undefined ? 'null' : replacement)
}

/** 占位符换成 null 之后是否仍是合法 JSON */
export function isValidJsonWithPlaceholders(text) {
  try {
    JSON.parse(maskPlaceholders(text))
    return true
  } catch {
    return false
  }
}

/**
 * 格式化 JSON 正文并保留占位符原样。
 * <p>
 * 占位符先替换成唯一字符串哨兵，交给 JSON.stringify 排版，再把哨兵还原成原始
 * 占位符文本（保持用户写的裸占位符，不加引号）。
 *
 * @returns 格式化后的文本；正文不是 JSON 时返回 null
 */
export function formatJsonBody(text) {
  const body = String(text || '').trim()
  if (!body) return null
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    // 下面按占位符方案再试一次
  }
  const placeholders = []
  const masked = body.replace(placeholderRegex(), (match) => {
    placeholders.push(match)
    return '"__MOCKHUB_PLACEHOLDER_' + (placeholders.length - 1) + '__"'
  })
  try {
    const formatted = JSON.stringify(JSON.parse(masked), null, 2)
    return formatted.replace(/"__MOCKHUB_PLACEHOLDER_(\d+)__"/g, (match, index) => placeholders[Number(index)] ?? match)
  } catch {
    return null
  }
}
