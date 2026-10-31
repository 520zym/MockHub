import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  hasPlaceholder,
  maskPlaceholders,
  isValidJsonWithPlaceholders,
  formatJsonBody
} from '../src/utils/json-placeholders.js'

test('hasPlaceholder 识别两种合法语法，忽略普通花括号', () => {
  assert.equal(hasPlaceholder('{{uuid}}'), true)
  assert.equal(hasPlaceholder('{{ path.id }}'), false) // 语法不支持空格，与后端一致
  assert.equal(hasPlaceholder('{{path.id}}'), true)
  assert.equal(hasPlaceholder('{"a": 1}'), false)
  assert.equal(hasPlaceholder(''), false)
  assert.equal(hasPlaceholder(null), false)
})

test('maskPlaceholders 把裸占位符换成 null，保留字符串里的占位符', () => {
  assert.equal(maskPlaceholders('{"id": {{uuid}}}'), '{"id": null}')
  assert.equal(maskPlaceholders('{"name": "{{userName}}"}'), '{"name": "null"}')
  assert.equal(maskPlaceholders('{"a": 1}'), '{"a": 1}')
})

test('isValidJsonWithPlaceholders 只对占位符宽容，不放过真实错误', () => {
  assert.equal(isValidJsonWithPlaceholders('{"id": {{uuid}}, "n": 1}'), true)
  assert.equal(isValidJsonWithPlaceholders('{"id": {{uuid}},}'), false) // 尾逗号仍是错误
  assert.equal(isValidJsonWithPlaceholders("{'a': 1}"), false)
  assert.equal(isValidJsonWithPlaceholders('not json'), false)
})

test('formatJsonBody 排版裸占位符 JSON 并保留占位符原样', () => {
  const formatted = formatJsonBody('{"id":{{uuid}},"data":{"t":{{timestamp}},"list":[1,2]}}')
  assert.equal(formatted, [
    '{',
    '  "id": {{uuid}},',
    '  "data": {',
    '    "t": {{timestamp}},',
    '    "list": [',
    '      1,',
    '      2',
    '    ]',
    '  }',
    '}'
  ].join('\n'))
})

test('formatJsonBody 不变动普通 JSON 和重复占位符', () => {
  assert.equal(formatJsonBody('{"a":1}'), '{\n  "a": 1\n}')
  const twice = formatJsonBody('{"a":{{uuid}},"b":{{uuid}}}')
  assert.equal(twice, '{\n  "a": {{uuid}},\n  "b": {{uuid}}\n}')
})

test('formatJsonBody 对非 JSON 正文返回 null', () => {
  assert.equal(formatJsonBody('not json'), null)
  assert.equal(formatJsonBody(''), null)
  assert.equal(formatJsonBody(null), null)
  assert.equal(formatJsonBody('<xml/>'), null)
})
