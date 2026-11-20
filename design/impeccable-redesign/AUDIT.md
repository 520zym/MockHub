# 现状审查 · 用 impeccable 判据审 MockHub 现有前端

> 审查对象：`frontend/src`（分支 `feat/20261028-UI优化`，工作区干净）+ `design/liquid-glass` 的最终「扁平」方向。
> 判据：`pbakaus/impeccable` 的 craft floor（`skill/reference/craft-floor.md`）与 critique 评分表。
> 口径：所有数字都来自源码统计或 WCAG 手算，不写"看起来"。本文只记录问题，**不改业务代码**。
> 样本：`views/{ApiList,ApiEdit,LogView,Stats,Login,Settings}.vue`、`styles/{variables,global,element-overrides}.scss`、`components/layout/{AppLayout,Sidebar,Topbar}.vue`、`components/{ResponseTabs,FilledIcon,MonacoEditor}.vue`、`components/match/*`。
> 统计基线：`views + components` 共 220 处裸十六进制色值；`border-radius` 出现 14 种不同数值；`font-size` 出现 19 种不同数值（12px 用了 274 次、13px 139 次）。

---

## 一、12 条具体问题

### 1. 页面结构仍等于「一叠白卡片」，卡片是容器而不是内容
【问题】Stats 的四个 KPI 卡、Settings 的设置卡、ApiEdit 的六张 section 卡、Login 的两个大面板，都是同一种"白底 + 大圆角 + 内距"的容器在承担页面结构；卡片之间没有层级差，只有位置差。
→ 证据：`Stats.vue:27-62`（4 个 `.kpi`，每个带 32px 图标砖，样式 `Stats.vue:238` `border-radius:16px;padding:20px`）、`Settings.vue:26`、`ApiEdit.vue:26/45/51/82/88/192`（6 张 `.section-card`，`ApiEdit.vue:1206` `border-radius:16px;padding:20px`）、`Login.vue:67/73`（`.login-intro` 与 `.login-card` 都是 `border-radius:20px`）。
→ 规则：`skill-ban-identical-card-grids`（卡片的偷懒容器 / 嵌套卡片永远错，另见 `skill-layout-cards-lazy`）
→ 改法：指标改 `.stat-row` 一行读数（无卡片、只留 1px 竖分隔）；页面分区改「页头 hairline 下 1px 线 + 白实面区块」，卡片只保留给可独立移动的对象（返回体、成员邀请）。

### 2. 深度没有"只声明一次"：14 档圆角，且圆角随视口改变
【问题】同一层界面里 20/16/13/12/11/10/9/8/7/6/5/4/3/2px 全在用，控件、卡片、侧栏、胶囊各取一档；`--app-radius-panel` 是 16px，但 `.settings-nav` 用 12px，同一张 `.settings-card` 在 ≤1200px 时又被改成 16px。
→ 证据：`variables.scss:37-40`（`--app-radius-shell:20px / panel:16px / control:10px / small:8px`）＋ 全仓 `border-radius` 直方图 49×10px、48×8px、26×16px、24×9px、24×6px、19×7px、18×12px、9×20px…；`Settings.vue:703` 为 `border-radius:12px`，`Settings.vue:771` 在 `@media (max-width:1200px)` 内改成 `16px`。
→ 规则：`skill-codex-elevation-radius`（深度二选一：边框或阴影；圆角是系统不是散值，另见 `skill-ban-codex-over-round`）
→ 改法：面板一律 0 圆角 + 1px hairline；只有控件保留 2px；圆角值不参与响应式。

### 3. Settings 左侧导航用 3px 彩色左条表示选中
【问题】当前选中项靠 3px 蓝色左条 + 浅底 + 蓝字三重强调，其中彩色左条是明确禁令（>1px 的彩色 border-left）。
→ 证据：`Settings.vue:699` `.nav-item--on::before { left:0; top:8px; bottom:8px; width:3px; border-radius:2px; background:#3568d4; }`
→ 规则：`skill-ban-side-stripe-borders`
→ 改法：选中态用墨色实底 + 反白文字（`.rail__item.is-active` 已给出范式），或 2px 墨色下划线；删掉左条与浅蓝底。

### 4. 19 档字号：角色分不清，页面标题四种尺寸
【问题】除了 12/13/14 三个相邻档，还有 11/15/16/17/18/19/20/21/22/23/24/25/26/28/32 与一个 12.5px；同级页面标题在 24/25/26/28 之间漂移，读者无法从字号判断"这是页面标题还是区块标题"。
→ 证据：`font-size` 直方图 19 个值（12px×274、13px×139、14px×33、11px×15、20px×12、26px×11、22px×11、15px×11、24px×10、18px×9…）；标题分别写在 `ApiList.vue:345`（28px）、`Stats.vue:236`（26px）、`LogView.vue:304`（26px）、`ApiEdit.vue:1204`（25px）、`Login.vue:74`（24px）、`global.scss:85`（`clamp(22px,2vw,28px)`）。
→ 规则：`skill-typo-floor`（角色比例必须一眼可辨）
→ 改法：锁死六级 26/20/14/13/12/11；页面标题只有一个 26px，区块标题 14px/600，删除 15–25px 这一整段中间档。

### 5. 间距只有一种节奏：所有关系都被压成 14px / 20px
【问题】`--app-space-gap:14px`、面板内距 20px、行高 46px 三个数覆盖了从"同组字段"到"两次独立决策"的全部关系，导致紧与松没有对比，扫读时所有块等重。
→ 证据：`variables.scss:41-43`（`--app-space-page: clamp(16px,2vw,28px) / --app-space-panel:20px / --app-space-gap:14px`）；`AppLayout.vue:77` 侧栏与主区之间 14px、页面四周 14px；`ApiEdit.vue:1205` 两栏 14px；`ApiList.vue:380` 行高固定 46px。整份样式里没有 4px / 8px / 24px / 32px 的分层。
→ 规则：`skill-layout-spacing-rhythm`（读计算值，紧组 + 宽分隔 + 标题上方大于下方）
→ 改法：引入 4 的倍数阶梯：组内 4/6px、字段间 10px、区块间 24px、页头到底部导语 18px；行高按内容分 32/44/46 三档，不再全局 46。

### 6. 数字不是等宽：计数、占比、延迟在比例字里抖动
【问题】只有 `.app-mono` 打开了 `tabular-nums`，而命中次数、百分比、延迟、时间列大多没有走等宽或 tabular，数字宽度随取值变化，表格列与统计读数无法对齐。
→ 证据：`global.scss:177-180`（tabular 只挂在 `.app-mono`）；`ApiList.vue:108` `<td class="cell-calls">{{ formatCount(row.hitCount) }}</td>` 无 mono/tabular（`.api-grid td` 见 `ApiList.vue:380`）；`Stats.vue:238` `.kpi__value` 无 `font-variant-numeric`；`LogView.vue:94` 延迟只是一段 `.duration-text`。
→ 规则：`skill-craft-browser-surfaces`（浏览器替你画的部分——数字、选区、滚动条、focus ring——也属于设计）
→ 改法：`body` 级 `font-variant-numeric: tabular-nums`，数据单元格统一 `.num`/`.mono`；行高与列宽用等宽数字重算一次。

### 7. 13 列 1484px 的固定宽表格，塞进 1170px 内容区
【问题】接口列表把 13 列写死 `min-width:1484px`，在 1440 画板下必然横向滚动约 350px；为了救场又把"启用/操作"两列 sticky 到右侧，形成"表格里再套一层横向滚动 + 悬浮列"的复合交互，滚到右端时路径列已经不可见。
→ 证据：`ApiList.vue:377` `.api-grid{min-width:1484px;table-layout:fixed}`、`ApiList.vue:378` 列宽合计 1384px＋内距、`ApiList.vue:455-459` `.cell-state{right:136px}` / `.cell-actions{right:0}` 的 sticky hack；容器宽度由 `AppLayout.vue:77`（1440−28 padding−228 侧栏−14 gap = 1170px）与 `ApiList.vue:351` 的 16px 内距决定。
→ 规则：`skill-ban-text-overflow`（真实文案在每个断点跑一遍，别让任何内容溢出）
→ 改法：列数降到 7 列以内（类型并入方法灯、创建人/修改时间下沉到检视器），把"命中次数/最近调用"变成行尾一列紧凑读数；列表只在 `≥1280` 显示次要列。

### 8. 两个"主色"同时在用，且 220 处颜色绕过 token
【问题】石墨 `#17191D` 与辅助蓝 `#3568D4` 都承担"主要动作/选中/链接"，颜色不带稳定含义；同时 220 处裸十六进制写死在组件里，换一版配色等于全量重刷。
→ 证据：`variables.scss:30` `--app-primary:#17191D` 与 `variables.scss:33` `--app-accent:#3568D4` 并存；`Settings.vue:699`、`Settings.vue:697`（`#F7F8FA`）、`Stats.vue:205/210-223`（ECharts 里写死 `#3568D4`/`#EDF2FC`/`#17191D`）；`grep -rnoE '#[0-9A-Fa-f]{6}' views components` 命中 220 次。
→ 规则：`skill-color-strategy`（颜色要带含义；一个色只做一件事）
→ 改法：石墨=结构与主操作，朱红=信号（失败/破坏/实时），绿=命中；蓝色整族退场；ECharts 主题从 CSS 变量读取，禁止图内硬编码。

### 9. 两套图标体系并存，并用 unicode 字符冒充图标
【问题】`FilledIcon` 是 24px 实心路径，Element Plus 图标是 1em 描边（stroke≈1），同一行里出现两种光学重量；序号角标、兜底标记、当前态、外链箭头直接用 ①②③…⑨、⭐、✓、↗，还有全角 `＋` 当新建图标。
→ 证据：`FilledIcon.vue:12-15`（`fill="currentColor" stroke="none"`，viewBox 24）vs `Stats.vue:161`/`LogView.vue:244` 的 `@element-plus/icons-vue`；`ResponseTabs.vue:32` `['①','②','③','④','⑤','⑥','⑦','⑧','⑨']`、`ResponseTabs.vue:40` `StarFilled`、`ResponseTabs.vue:45` `<span aria-hidden="true">✓</span>`、`ResponseTabs.vue:13` `↗`、`ApiList.vue:14` `＋ 新建接口`。
→ 规则：`skill-ban-glyph-icons`（图标要画出来，一套描边一套重量）
→ 改法：只留一套 16px / 1.5 描边自绘图标（`_shell.html` 的 sprite 已是这套）；序号用等宽数字，兜底/当前态用图标或纯文字，禁止 unicode 符号。

### 10. 产品的核心机制（条件匹配）被拆在 tab、折叠面板和弹窗三层里
【问题】"这次请求为什么命中这条返回体"是产品的主张，但它需要：切到某个返回体 tab → 在 `v-show` 的条件面板里读规则 → 再点「图解用法」看解释 → 滚到面板底部才看到"试匹配结果"。同屏看不到"条件 → 命中 → 状态码"的因果链，只能靠记忆拼接。
→ 证据：`ResponseTabs.vue:16-62`（tab 上叠了名称/当前态/状态码/条件数/未启用 5 种角标）、`ResponseTabs.vue:70-87`（条件面板 `v-show` 在编辑器下方）、`ResponseTabs.vue:13` 的「图解用法」打开 `MatchGuide.vue`（261 行弹窗）、`ConditionPanel.vue:36-44`（试配结果在规则之后）；编辑页整体结构见 `ApiEdit.vue:192-201`。
→ 规则：`skill-salience-parity`（视觉显著度必须与信息重要度一致）
→ 改法：返回体列表 + 条件摘要 + 编辑器 + 试配同屏三栏（提案 03 的做法），"命中第几条/状态码/延迟"常驻可见，弹窗只留"规则语法帮助"。

### 11. 排障必须打开 640px 模态框，关掉就丢上下文
【问题】请求日志是一张 6 列平表，"为什么是这条返回"藏在「查看详情」的 640px 弹窗里；弹窗盖住列表后无法对照相邻请求，也无法保留筛选条件做连续排查，属于典型的"模态框反射"。
→ 证据：`LogView.vue:97-103`（操作列唯一动作是「查看详情」）、`LogView.vue:128-134`（`<el-dialog width="640px">`）、`LogView.vue:135-166`（请求头/参数/请求体全塞在弹窗内）、`LogView.vue:62-110`（列表 6 列，无命中链路列）。
→ 规则：`skill-reflex-modal-by-reflex`（不需要打断与保护焦点的任务，不要用模态）
→ 改法：改主从：左列表常驻 520px，右检视器 400px 三态（请求/响应/命中链路），选中行不离开列表。

### 12. 全局状态缺席：跑没跑、地址是什么、键盘怎么走都要回到页面里找
【问题】"服务在运行吗、Mock 基地址是什么、我在哪个团队"只在接口页/编辑页各自请求一次，切到统计或日志页就看不到；同时没有键盘路径（无命令面板、无行聚焦、无快捷键），`keyup` 处理器基本只用于回车提交，密集操作只能鼠标逐行点。
→ 证据：`ApiList.vue:263` 与 `ApiEdit.vue:1168-1174` 分别 `getServerAddress()`（仅这两处）；`Topbar.vue:25` 顶栏只有面包屑 + 搜索 + 刷新，无运行状态/基地址；全仓 `keydown|keyup` 命中 23 处且多为 `@keyup.enter`，`ApiList.vue:93` 的 `.api-grid-scroll` 只有 `tabindex="0"`，行本身不可聚焦。
→ 规则：`skill-floor-shipping`（状态与键盘焦点属于交付底线）
→ 改法：48px 墨色全局状态条（团队 / 基地址可复制 / 运行灯 / 账号）+ 列表行 `tabindex` 与 ↑↓/Enter/Delete 路径；这三件事在每页都可见。

---

## 二、必须保留（重设计不能丢的产品事实）

1. **权限模型**：超管 / 团队管理员 / 普通成员 + 团队级 capabilities（`read`/`write`/`batchDelete`/`overwriteImport`）。看 `ApiList.vue:236-241`、`utils/permissions.js`。归档团队（`status==='ARCHIVED'`）必须只读，写操作入口不能因为视觉简化而消失或变成可点。
2. **团队成员隔离**：每个响应都要按 `teamId` 校验，跨团队数据必须拒绝显示而不是静默展示（`ApiList.vue:276` "接口响应不属于当前团队，已拒绝显示"）；`key` 变化触发整体重建（`ApiList.vue:240-259`）。主从/三栏布局不能把两个团队的数据混进同一个检视器。
3. **多返回体语义**：条件全部 AND、按返回体顺序**首条命中**、唯一一条无条件返回体作兜底、随机模式为**等概率**、条件来源含 Query / JSON Body / Header / SOAP XML Body、数值比较与 Java 正则、不支持 OR/权重/拖拽优先级（见 `design/liquid-glass/README.md:98` 与 `ConditionTable.vue:45-59`）。设计稿不得引入"优先级排序""权重"这类不存在的能力。
4. **离线单 jar**：无 CDN 字体、无外部资源、无网络请求；SQLite 单文件数据（`_shell.html` 页脚 "SQLite · 单文件数据"、`design/impeccable-redesign/README.md` 边界）。截图审查脚本里的 `external`/`net` 两项就是这条的守门员。
5. **Monaco + 5–6MB 大文本**：响应体可能到 5~6MB（`ApiModel.java:54`、`application.yml:7`、`schema.sql:98`），保存响应不回传正文（`ApiControllerSaveResponseTest.java:74`）。编辑器必须是 Monaco 而非 textarea，且保留折叠/弹层/懒挂载策略（`ApiEdit.vue:296-303`、`ResponseTabs.vue:539-543`、`MonacoEditor.vue:213` 100KB 关 minimap）；大文本不能进列表请求。
6. **日志保留策略**：`--log.retain.mode` = `count`（按条数）或 `days`（按天数）（`application.yml:36`、`Settings.vue:242-252`）；操作日志与请求日志是两类（`LogView.vue:22-59` / `:62-110`）；AI 匹配诊断只对 Mock 调用开放、基于请求快照、不改配置（`LogView.vue:222-224`、`:250-251`）。日志页标"共 N 条"时必须与保留策略一致，不能写成"全部日志"。
7. **版本与并发**：`revision` 冲突不自动覆盖，草稿保留并提供"复制草稿/重新加载"（`ApiEdit.vue:17-20`、`ApiList.vue:300`）；回收站保留 30 天、恢复后默认停用（`ApiList.vue:189`）。新设计里的"未保存修改"提示与保存动作必须承载这个语义。
8. **首次登录强制改密**：`firstLogin` 时弹不可关闭的改密流程，改密后撤销旧凭证并重新登录（`Login.vue:18-25`、`:46-61`）。登录画板必须说明这个状态存在（提案 01 用 `.banner--warn` 表达）。
9. **导入/导出与 SOAP**：导入上限 100MB（`utils/api-import.js:22`）、导出 zip/json（`ApiList.vue:328`）、WSDL 上传解析后按 Operation 分别配置返回体、SOAPAction 与 `?wsdl` 托管地址（`ApiEdit.vue:88-128`）。SOAP 不是 REST 的皮肤，必须有独立字段。
10. **AI 写操作必须人工确认**：草稿态不自动写入，确认后才落库（`McpConfirm.vue`、`AiAssistant.vue`）；助手页的"待确认草稿"是产品事实，不是装饰。

---

## 三、本提案的风险与代价（诚实版）

1. **信息密度换实现成本**：主从 + 三栏意味着同一份数据要在列表、检视器、编辑器三处复用，且每处都要处理"权限不足/加载失败/版本冲突"三种状态。现前端已经有 `ApiList.vue`（460 行模板）、`ApiEdit.vue`（1219 行）级别的复杂度，新结构只会更高；静态稿看不到这部分成本。
2. **表格主从在窄屏退化**：`split__detail` 默认 400px、≤1360px 压到 360px。1440 下列表只剩 ~700px，13 列的问题被"减少列"掩盖，1280 以下仍需折叠成抽屉——否则只是把 640px 弹窗换成 400px 常驻面板，窄屏依旧要横滚。提案只承诺 ≥1280，这条必须写进验收。
3. **0 圆角 + 高密度会缩小可点区域**：行高 44–46px、`.btn--sm` 26px、`.chip` 20px、8px 图标按钮 26px。鼠标场景可接受，缩放 200%、触控板误触与弱视用户会明显吃亏；需要用 focus ring、行级命中区（整行可点）来补偿，不能只靠视觉。
4. **字体是系统栈，不是自有面孔**：离线单 jar 不能引 CDN 字体，中文走 PingFang/微软雅黑。跨平台（macOS/Windows）字宽、字重、行高会有 3–5% 差异，等宽数字在 Windows 上更宽；若要"更锐的拉丁/数字"，需打包 ~40KB 子集等宽字体，涉及授权与体积预算，本轮未做。
5. **审查脚本只证明 1440×900 首屏**：`build/audit.json` 的 overflow/clipped 只覆盖截图视口，滚动区内部、弹层、`prefers-reduced-motion`、真实 Monaco 与 5–6MB 文本都不在证据里。设计稿里"编辑器"与"试配结果"是演示数据，真实匹配以服务端为准（`ConditionPanel.vue:37-44` 的"按已保存配置试配，未提交草稿不参与"必须保留）。
6. **视觉语言的迁移成本被低估**：`element-overrides.scss` 已有 735 行用于压平 Element Plus（15 处 `box-shadow:none`、5 处 `!important`）。换成 0 圆角 + hairline 体系后，这层覆盖大部分要重写，且 Element Plus 的 hover/active 内建色仍会漏出来；不重写就会在新体系里看到旧圆角。

---

## 四、不属于问题（已修的，别重新开会）

- 对比度：`variables.scss:13-24` 的说明文字色已经按 4.5:1 压深（实测 `#5D6470` on `#EEF1F4` = 5.26:1，`#626B76` = 4.77:1，`#5D6470` on `#EDF2FC` = 5.31:1）。新稿不要为了"更淡"把这层又退回去。
- 阴影/玻璃/渐变：`--el-box-shadow: none` 全家桶 + `global.scss:100` 已经清干净，`liquid-glass` 那版解决的就是"去装饰"。本提案不应被理解为"再加一层风格"。
- 浏览器表面：`global.scss:44-47`（`::selection`）、`:55-72`（滚动条）、`:39-42`（`focus-visible` 2px）已经做了，属于干净的部分；第 6 条的问题只是"数字没用 tabular"，不是"没做浏览器表面"。
