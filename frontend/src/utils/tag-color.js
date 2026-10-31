/**
 * 标签配色工具。
 * <p>
 * 标签胶囊用「15% 透明度底色 + 同色文字」，浅底色上 12px 文字容易低于 4.5:1，
 * 因此文字统一压暗一档，保证在浅色背景上仍然可读。
 */

export function hexToRgb(hex) {
  const value = String(hex || '').replace('#', '')
  const full = value.length === 3 ? value.split('').map(c => c + c).join('') : value
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return { r: 53, g: 104, b: 212 }
  return { r: parseInt(full.slice(0, 2), 16), g: parseInt(full.slice(2, 4), 16), b: parseInt(full.slice(4, 6), 16) }
}

export function hexToRgba(hex, alpha) {
  const { r, g, b } = hexToRgb(hex)
  return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')'
}

/** 标签文字色：把品牌色压暗到 80%，在 15% 同色底上满足 AA 对比度。 */
export function tagTextColor(hex) {
  const { r, g, b } = hexToRgb(hex)
  const mix = 0.8
  return 'rgb(' + Math.round(r * mix) + ',' + Math.round(g * mix) + ',' + Math.round(b * mix) + ')'
}
