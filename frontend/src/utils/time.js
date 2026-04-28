/**
 * 时间工具：相对时间格式化（fromNow）+ 完整时间格式化（formatFull）。
 * 不引入 dayjs，保持依赖最小化。
 *
 * 后端时间字段（createdAt / updatedAt）以 ISO 字符串存储：
 *   - 标准格式：2026-04-28T14:38:41
 *   - 兼容格式：2026-04-28 14:38:41（个别历史数据）
 */

/**
 * 解析后端 ISO 字符串为 Date。
 * 兼容标准 'T' 分隔符与历史空格分隔符；解析失败返回 null。
 */
function parseDate(input) {
  if (!input) return null
  if (input instanceof Date) return input
  // 把空格分隔符替换为 T，提高兼容性
  const normalized = String(input).replace(' ', 'T')
  const d = new Date(normalized)
  return isNaN(d.getTime()) ? null : d
}

/**
 * 相对时间：刚刚 / N 分钟前 / N 小时前 / 昨天 / N 天前 / M-D / YYYY-M-D。
 *
 * @param {string|Date} input  时间字符串或 Date
 * @param {Date} [now]         "现在"基准（测试可注入；默认 new Date()）
 * @returns {string} 相对时间文案；输入无效返回 '-'
 */
export function fromNow(input, now = new Date()) {
  const d = parseDate(input)
  if (!d) return '-'

  const diffMs = now.getTime() - d.getTime()
  const diffSec = Math.floor(diffMs / 1000)

  // 未来时间（钟差或时区错配）：直接显示完整时间，避免"-1 小时前"歧义
  if (diffSec < 0) return formatFull(d)

  if (diffSec < 60) return '刚刚'
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} 小时前`

  // 跨日判断：基于"今天 0 点"，避免 24 小时切换问题（凌晨 1 点看到"23 小时前"算今天但读起来像昨天）
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const dStart = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const diffDays = Math.floor((todayStart - dStart) / 86400000)

  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return `${diffDays} 天前`

  // 同年内简写为 M-D；跨年带上年份
  const m = d.getMonth() + 1
  const day = d.getDate()
  if (d.getFullYear() === now.getFullYear()) {
    return `${m}-${day}`
  }
  return `${d.getFullYear()}-${m}-${day}`
}

/**
 * 完整时间：YYYY-MM-DD HH:mm:ss。
 *
 * @param {string|Date} input 时间字符串或 Date
 * @returns {string} 完整时间字符串；输入无效返回 '-'
 */
export function formatFull(input) {
  const d = parseDate(input)
  if (!d) return '-'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
         `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
