/**
 * 通用颜色分配：优先选择当前集合里尚未使用的颜色。
 * 颜色全部来自现有设计 token 的深色系，保证在白底与 15% 透明底上都可读。
 */
export const TEAM_COLORS = [
  '#3568D4', '#0F766E', '#B45309', '#7C3AED', '#BE123C',
  '#0369A1', '#4D7C0F', '#A21CAF', '#C2410C', '#0E7490',
  '#4338CA', '#854D0E', '#9D174D', '#047857', '#6D28D9',
  '#B91C1C', '#1D4ED8', '#A16207', '#065F46', '#7E22CE'
]

export const TAG_COLORS = TEAM_COLORS

function normalize(color) {
  const value = String(color || '').trim().toLowerCase()
  return /^#[0-9a-f]{6}$/.test(value) ? value : ''
}

/**
 * 从 palette 里挑一个未被 used 使用的颜色。
 * used 已覆盖整个 palette 时，选择使用次数最少的颜色，允许重复但不优先重复。
 */
export function pickDistinctColor(used = [], palette = TEAM_COLORS) {
  const counts = new Map()
  for (const raw of used) {
    const color = normalize(raw)
    if (!color) continue
    counts.set(color, (counts.get(color) || 0) + 1)
  }
  const free = palette.find(color => !counts.has(normalize(color)))
  if (free) return free
  let best = palette[0]
  let bestCount = Infinity
  for (const color of palette) {
    const count = counts.get(normalize(color)) || 0
    if (count < bestCount) { best = color; bestCount = count }
  }
  return best
}
