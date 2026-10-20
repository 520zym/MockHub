# MockHub AI 助手 · 设计稿

对应 GitHub Issue [#6 AI功能支持](https://github.com/520zym/MockHub/issues/6) 的界面方案。视觉完全沿用 MockHub 现有 Soft UI 扁平化风格，只额外引入一层紫色渐变作为 AI 的识别色。

- 起始页：[00-overview.html](00-overview.html)
- 预览图：[screens/00-overview.png](screens/00-overview.png)

## 文件结构

```
design/ai-assistant/
├── 00-overview.html            设计总览（原则 / 流程 / 页面索引 / token / 落地建议）
├── 01-assistant-entry.html     全局入口
├── 02-assistant-chat.html      对话助手 + 确认卡片
├── 03-response-generate.html   AI 生成响应体
├── 04-conditions-generate.html AI 生成条件规则
├── 05-settings-ai.html         AI 配置（超管）
├── 06-settings-mcp.html        MCP Server 配置
├── 07-match-explain.html       请求日志 · AI 匹配诊断
├── 08-states.html              组件状态与边界
├── assets/mock.css             设计 token 与组件样式（从 frontend/src/styles 复制）
├── build/                      静态页面生成脚本
└── screens/                    每个页面的 2x 截图
```

## 视觉基线

全部取自现有前端 token，未新增主色：

| Token | 值 | 用途 |
| --- | --- | --- |
| Primary | `#6366F1` | 主按钮、选中态、链接 |
| Primary Soft | `#E0E7FF` | 选中背景、标签 |
| Text | `#1B2559` | 标题与正文 |
| Secondary Text | `#A3AED0` | 说明、表头 |
| Page | `#F5F5F7` + 135° 渐变 | 内容区底色 |
| Radius | 卡片 12 / 输入 10 / 按钮 12 | 与 `variables.scss` 一致 |
| AI Accent | `#6366F1 → #8B5CF6` 渐变 | 悬浮球、头像、AI 按钮描边 |

AI 相关元素只改变“识别色”，不改变排版：正文仍是中性色，AI 按钮是工具栏里的普通按钮 + 渐变描边，不给页面加第二层视觉噪声。

## 页面清单

| # | 场景 | 关键点 |
| --- | --- | --- |
| 01 | 全局入口 | 右下 56px 悬浮球；首次进入一条可关闭提示，不遮挡工具栏 |
| 02 | 对话助手 | 428×824 面板；团队范围 / 权限提示；统计卡与 CREATE_API 确认卡 |
| 03 | 生成响应体 | 右侧 600px 抽屉；描述 + 生成方式 + 字段规模；预览后插入，确认前不覆盖 |
| 04 | 生成条件规则 | 自然语言 → conditions 表格；逐条勾选、字段不存在时警告并默认不勾选 |
| 05 | AI 配置 | 仅超管；Provider / Model / Base URL / Key（只写不回显）/ 连通性测试 / 能力开关 / 使用范围 |
| 06 | MCP Server | 服务地址、8 个工具开关、写操作策略、访问 Token 管理 |
| 07 | 匹配诊断 | 请求详情抽屉；结论、判定链路、修正建议；应用建议前二次确认 |
| 08 | 状态与边界 | 入口、确认卡、加载、失败、未启用、权限受限、长内容折叠 |

## 核心流程

```text
自然语言 / 按钮
      │
      ▼
生成草稿（pending action: PENDING，带 expiresAt）
      │
      ├─ 预览 / 编辑 / 取消 ──▶ 不写入，可重新生成
      │
      ▼
用户点「确认」
      │
      ▼
服务端重新校验权限 + 团队 + 数据合法性
      │
      ├─ 通过 ──▶ 写入 + 审计日志 ──▶ CONFIRMED（展示「查看接口」）
      └─ 失败 ──▶ 返回可读原因，卡片保持可重试
```

设计上刻意区分两种“生成”：

1. **编辑器内生成**（03 / 04）——结果只写入**前端表单草稿**，用户点「插入到编辑器」后仍需正常点「保存接口」。
2. **对话式写操作**（02）——模型不直接调用写接口，只创建 `pending action`，由用户点确认卡片后再落库。

## 组件规格

| 组件 | 规格 |
| --- | --- |
| 悬浮入口 | 56×56，圆角 18，渐变底，右下 28px，生成中显示脉冲环 |
| 对话面板 | 428×824，圆角 16，右下 16px，阴影 `0 24px 64px rgba(27,37,89,.18)` |
| 生成抽屉 | 600px（响应体）/ 760px（诊断），左侧圆角 16，无遮罩，保留左侧编辑器上下文 |
| 确认卡 | 顶部「待确认操作 · CREATE_API」+ 倒计时；字段摘要 + 代码预览 + 取消/预览/编辑/确认 |
| 统计卡 | 3 行排名 + 数值；标注「只读」与统计口径 |
| 规则预览 | 勾选列 + 来源 / 路径 / 运算符 / 值；不存在的字段整行黄色提示且默认取消勾选 |
| 空态 | 未启用 AI 时保留入口，展示说明 + 去配置 / 联系管理员 |

状态枚举建议与 issue 一致：`PENDING / CONFIRMED / CANCELLED / EXPIRED`，默认 12 分钟过期；前端只负责展示，倒计时以服务端 `expiresAt` 为准。

## 与现有代码的衔接

| 设计位置 | 现有文件 | 改动 |
| --- | --- | --- |
| 返回体工具栏「AI 生成」 | `frontend/src/components/ResponseTabs.vue` 的 `.editor-toolbar` | 加一个按钮 + 抽屉组件，复用 `el-drawer` |
| 条件工具条「AI 生成规则」 | `frontend/src/components/match/ConditionPanel.vue` 的 `.toolbar` | 同上，生成结果写入现有 `conditions` JSON |
| 右下角助手 | `frontend/src/components/layout/AppLayout.vue` | 挂一个全局浮层组件，仅在 `ai.enabled` 时渲染 |
| AI 配置 | `frontend/src/views/Settings.vue` | 新增「AI 助手」区块，沿用 `.settings-section` 结构 |
| MCP 配置 | `frontend/src/views/Settings.vue` | 新增「MCP Server」区块，或独立 tab |
| 匹配诊断 | `frontend/src/views/LogView.vue` | 请求详情由 Dialog 升级为 Drawer，右侧加诊断区 |
| 后端 | 新增 `AiProvider / AiService / AiSettings`、`pending_action` 表 | Key 加密存储、响应脱敏、工具白名单、审计日志 |

工具注册层建议与 issue 保持一致：`searchApis / getApiDetail / getApiStats / createApiDraft / applyApiDraft / generateMockResponse / generateConditions / explainMatchResult`，MCP 与网页助手共用同一套。

## 验收对照（issue #6）

- 未配置 AI 时入口有明确提示、不影响现有功能 → 08「未启用 AI」
- 配置后 REST 可生成合法 JSON、SOAP 可生成 XML → 03（JSON 示例；XML 同一抽屉切换 Content-Type）
- 用户确认前不覆盖当前响应体 → 03 抽屉底部「确认前不会写入编辑器」+ 插入按钮
- 失败时有友好提示 → 08「生成失败」
- API Key 不在接口响应 / 日志 / localStorage 泄露 → 05 掩码字段 + 数据安全说明
- 写操作继承权限、团队隔离、可审计 → 02 卡片说明 + 07 诊断只读

## 重新生成

```bash
cd design/ai-assistant
node build/build.mjs          # 生成 9 个 HTML
bash build/shot.sh            # 重新截图（需要 Chrome headless shell）
```

截图脚本默认使用 Playwright 自带的 `chrome-headless-shell`，也可通过 `CHROME` 环境变量指定。
