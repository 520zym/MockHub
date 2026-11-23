// Guards the readability baseline fixed across the console UI.
// The mockup boards are 1440px designs; copying their 9–10px captions into the product
// made real text unreadable, and several views hardcoded board greys as body colour.
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const SRC = new URL('../src', import.meta.url).pathname
const ROOT = new URL('..', import.meta.url).pathname

// The sidebar monogram is a single glyph inside a 30px badge, not product copy.
const MONOGRAM = /font:\s*600\s+10px/
const ALLOWED_LOW = [{ file: 'components/layout/Sidebar.vue', pattern: MONOGRAM }]

const BANNED_COLOURS = ['#A3AED0', '#929AA5', '#8D98A7', '#7E8C9E', '#9AA1AB', '#929DAC']

function walk(dir) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) out.push(...walk(path))
    else if (/\.(vue|scss|css)$/.test(entry)) out.push(path)
  }
  return out
}

const files = walk(SRC)

function luminance(hex) {
  const value = hex.replace('#', '')
  const channels = [0, 2, 4].map(index => parseInt(value.slice(index, index + 2), 16) / 255)
  const linear = channels.map(channel => (channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4)))
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
}

function contrast(foreground, background) {
  const a = luminance(foreground)
  const b = luminance(background)
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

test('no view or component declares type below the 11px floor', () => {
  const offenders = []
  for (const file of files) {
    const source = readFileSync(file, 'utf8')
    const name = relative(SRC, file)
    for (const match of source.matchAll(/font-size:\s*([0-9.]+)px/g)) {
      if (parseFloat(match[1]) >= 11) continue
      if (ALLOWED_LOW.some(rule => rule.file === name && rule.pattern.test(match[0]))) continue
      offenders.push(name + ': ' + match[0])
    }
    for (const match of source.matchAll(/font:\s*([^;{}]+)/g)) {
      const size = match[1].match(/([0-9.]+)px/)
      if (!size || parseFloat(size[1]) >= 11) continue
      if (ALLOWED_LOW.some(rule => rule.file === name && rule.pattern.test(match[0]))) continue
      offenders.push(name + ': ' + match[0].trim())
    }
  }
  assert.deepEqual(offenders, [], 'readability floor violated:\n' + offenders.join('\n'))
})

test('board-only greys never come back as text colours', () => {
  const offenders = []
  for (const file of files) {
    const source = readFileSync(file, 'utf8')
    for (const colour of BANNED_COLOURS) {
      if (source.toUpperCase().includes(colour)) offenders.push(relative(SRC, file) + ' uses ' + colour)
    }
  }
  assert.deepEqual(offenders, [], 'banned greys reintroduced:\n' + offenders.join('\n'))
})

test('text tokens meet WCAG AA on every surface they are used on', () => {
  const variables = readFileSync(join(SRC, 'styles/variables.scss'), 'utf8')
  const surfaces = ['#FFFFFF', '#EEF1F4', '#F4F6F9']
  for (const token of ['--app-text-secondary', '--app-text-muted', '--app-text-success', '--app-text-danger', '--app-text-warning', '--app-text-accent']) {
    const value = variables.match(new RegExp(token + ':[\\s]*(#[0-9A-Fa-f]{6})'))
    assert.ok(value, token + ' must be defined')
    for (const surface of surfaces) {
      const ratio = contrast(value[1], surface)
      assert.ok(ratio >= 4.5, token + ' ' + value[1] + ' on ' + surface + ' is ' + ratio.toFixed(2) + ':1')
    }
  }
})

test('every text token used by a view is defined', () => {
  const variables = readFileSync(join(SRC, 'styles/variables.scss'), 'utf8')
  const defined = new Set([...variables.matchAll(/(--app-[a-z0-9-]+):/g)].map(match => match[1]))
  const missing = new Set()
  for (const file of files) {
    const source = readFileSync(file, 'utf8')
    for (const match of source.matchAll(/var\((--app-text-[a-z0-9-]+)/g)) {
      if (!defined.has(match[1])) missing.add(relative(SRC, file) + ' -> ' + match[1])
    }
  }
  assert.deepEqual([...missing], [], 'undefined text tokens:\n' + [...missing].join('\n'))
})
