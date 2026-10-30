# 可读性与布局细节基线

本文是 2026-10-30 全站几何审计后的统一整改标准。审计对象是真实打包应用 + 真实 Chromium，逐页在 1440 / 768 / 375 宽度下量测渲染结果，共发现 965 处 <12px 文字、766 处对比度不足、41 处主信息被省略号截断、若干行内卡片高度不齐。

## 根因

设计稿是 **1440×900 的桌面画板**，稿中的 9–10px 说明文字是画板标注尺寸，不是产品字号。实现按 1:1 抄进了产品，在真实屏幕上过小；同时存在把画板用浅灰（如 `#A3AED0`，白底仅 2.2:1）直接当正文色的情况。

## 必须遵守

### 字号

| 用途 | 最小 |
| --- | --- |
| 正文、表格内容、卡片信息 | 13px（默认 14px） |
| 说明、提示、次要信息 | 12px |
| 表头、等宽角标、最密处 | 11px |
| 徽标首字母（monogram，非文本信息） | 10px |

禁止出现 9px / 10px 的信息性文字。层级用字重与颜色区分，不要靠继续缩小字号。

### 颜色与对比度

- 只使用 token：`--app-text-primary` / `--app-text-regular` / `--app-text-secondary` / `--app-text-muted` / `--app-text-disabled`。
- 语义色作文字时用文字专用档位：`--app-text-success`（5.3:1）、`--app-text-danger`（6.3:1）、`--app-text-warning`、`--app-text-accent`；原 `--el-color-success` 等只用于填充与描边。
- 正文对比度：白底与页面底 `#EEF1F4` 上均 ≥ 4.5:1。
- 禁止在视图内新增裸 hex 浅灰（`#A3AED0` `#929AA5` `#8D98A7` `#7E8C9E` 等）；已有的一律替换为 token。

### 间距与对齐

- 间距走 4px 栅格；卡片内边距 16px 或 20px；页面块间距 14px 或 16px。
- 同一行内的卡片必须等高：grid 行使用 `align-items: stretch`，内部内容区 `flex: 1`；图表/统计卡片的高度由容器决定，不随数据变化。
- 表格主信息列（接口名、路径、文件名、目标名）在 1440 下不得被省略号截断超过自身 1/3；确实需要截断时必须提供 `title` 悬浮全文。

### 触控

- 桌面点击目标 ≥ 28×28px，移动 ≥ 36×36px。纯图标按钮用 min-width/min-height 保证，不要只靠图标本身尺寸。

## 责任边界

- 共享层（`frontend/src/styles/*.scss`、`components/layout/*`、`ApiList.vue`）由 Lead 统一修改，其它人不要改。
- 每个视图只改自己文件内的 `<style>` 与必要文案层级，不改交互逻辑、不改接口调用、不改权限判断。

## 验收

```bash
# 1. 静态自检：不得再有 <12px 字号与裸 hex 浅灰
grep -nE 'font-size:\s*(9|9\.5|10|10\.5)px' frontend/src/views/<你的文件>.vue
grep -nE '#A3AED0|#929AA5|#8D98A7|#7E8C9E|#969CA5' frontend/src/views/<你的文件>.vue

# 2. 全站几何审计（需隔离实例，Lead 统一跑）
node frontend/tests/browser-layout-audit.mjs --isolated --state=target/ui-smoke-state.json --pages=/settings
```

审计脚本本身也是交付物：它把“布局没做好”变成可复现、可回归的量化检查，而不是靠人眼逐页找。

## 整改结果（同一套隔离实例，1440 / 768 / 375 三档宽度 + 6 个弹窗抽屉）

| 规则 | 整改前 | 整改后 |
| --- | --- | --- |
| 过小字号（<11px） | 965 | 0 |
| 对比度不足（正文 <4.5:1） | 766 | 0 |
| 文字溢出 / 被裁剪 | 296 | 0 |
| `white-space:nowrap` 强行换行 | 543 | 0 |
| 点击目标过小 | 177 | 24 |
| 主信息被省略号截断 | 41 | 0 |
| 同排卡片高度不齐 | 22 | 0 |
| 按钮与输入框高度不齐 | 33 | 6 |
| 页面级横向溢出 | 0 | 0 |

剩下 30 处分布在 Element Plus 的 `el-radio-button` 与几个图标按钮上，属于组件库内部盒模型，已在报告中保留跟踪。

```bash
# 复现（需隔离实例，脚本自己造数据、自己截图、自己写报告）
node frontend/tests/browser-layout-audit.mjs --isolated --state=target/ui-smoke-state.json --dialogs
# 回归（并入常规前端测试，随 node --test tests/*.test.mjs 一起跑）
node frontend/tests/typography-baseline.test.mjs
```

### 关键做法

- **系统性修 token，而不是逐页打补丁**：`--app-text-secondary` 由 4.1:1 压深到 5.26:1，新增 `--app-text-muted` 与 `--app-text-success/danger/warning/accent` 文字档位，视图里的裸 hex 一律归零。
- **字号下限入规范并加测试**：任何 <11px 的声明会让 `typography-baseline.test.mjs` 失败，说明文字统一 12px，层级改由字重与颜色承担。
- **几何缺陷靠量测发现**：图标与文字是否同一中线、标签与输入框是否对齐、同排卡片是否等高、工具栏控件高度是否一致，都是脚本量出来的，不靠肉眼。
- **第三方编辑器单独划界**：Monaco 与 wangEditor 自带排版与配色，只调其外观 token，不强行改内部字号。
