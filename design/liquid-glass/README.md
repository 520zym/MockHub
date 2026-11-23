# MockHub · Flat 全页面设计稿

最终确认方向：**扁平、纯色分区、实心侧栏图标、无阴影、无模糊、无渐变、无白色描边；用户入口仅位于左下角。**

## 权限方案修订

账号、团队、团队管理员与超级管理员的入口及操作状态，以 [权限方案新稿](permissions/index.html) / [逐页预览](permissions/preview.html) 为准。下列 22 页继续作为内容与视觉基线保留，不代表旧的超管导航或权限边界仍是最终方案。

## 从这里看

- [逐页预览](preview.html)：22 张画板，支持目录、上一页/下一页、键盘左右切换、原尺寸打开。
- [全部页面总览](index.html)：按页面组筛选，也可搜索页面。
- 单页 HTML 均可离线直接打开。截图为真实浏览器渲染，不是图片生成模型的近似稿。

覆盖 13 个实际页面路由、6 类设置，以及全局助手、REST/SOAP 和首次改密等界面状态；设置六类仍是同一路由的不同页签，不虚构业务路由。

## 接口与联调

| 画板 | 页面 | 内容 | 独立页面 / 原图 |
| --- | --- | --- | --- |
| 01 | 接口管理 | 团队接口、筛选与批量操作 | [HTML](01-api-list.html) · [PNG](screens/01-api-list.png) |
| 06 | 新建接口 | 基础信息、默认响应与变量 | [HTML](06-api-new.html) · [PNG](screens/06-api-new.png) |
| 02 | REST 接口编辑 | 多返回体、JSON 与匹配规则 | [HTML](02-api-edit.html) · [PNG](screens/02-api-edit.png) |
| 22 | 匹配规则配置 | 返回体、AND 条件、字段样例与本地试配建议 | [HTML](22-match-rules.html) · [PNG](screens/22-match-rules.png) |
| 20 | SOAP 接口编辑 | WSDL、Operations 与 XML 响应 | [HTML](20-api-soap.html) · [PNG](screens/20-api-soap.png) |
| 03 | 请求日志 | 请求明细、响应与命中链路 | [HTML](03-logs.html) · [PNG](screens/03-logs.png) |
| 12 | 操作日志 | 团队审计与配置变更详情 | [HTML](12-operation-logs.html) · [PNG](screens/12-operation-logs.png) |
| 04 | 对话助手 | 上下文、待确认变更与差异 | [HTML](04-assistant.html) · [PNG](screens/04-assistant.png) |

## 工作区与管理

| 画板 | 页面 | 内容 | 独立页面 / 原图 |
| --- | --- | --- | --- |
| 07 | 文件服务器 | 文件列表、别名标签与预览 | [HTML](07-files.html) · [PNG](screens/07-files.png) |
| 08 | 动态变量 | 变量、候选值与分组三栏 | [HTML](08-variables.html) · [PNG](screens/08-variables.png) |
| 09 | 使用统计 | 调用趋势、TOP10 与闲置接口 | [HTML](09-stats.html) · [PNG](screens/09-stats.png) |
| 10 | 团队管理 | 团队卡片、成员与角色 | [HTML](10-teams.html) · [PNG](screens/10-teams.png) |
| 11 | 用户管理 | 账号列表、团队分配与重置 | [HTML](11-users.html) · [PNG](screens/11-users.png) |
| 13 | AI 草稿确认 | 多返回体预览与确认写入 | [HTML](13-mcp-confirm.html) · [PNG](screens/13-mcp-confirm.png) |

## 全局设置

| 画板 | 页面 | 内容 | 独立页面 / 原图 |
| --- | --- | --- | --- |
| 14 | 设置 · AI 供应商 | API 连接、模型与密钥状态 | [HTML](14-settings-providers.html) · [PNG](screens/14-settings-providers.png) |
| 15 | 设置 · 助手与能力 | 四类能力、权限范围与脱敏 | [HTML](15-settings-assistant.html) · [PNG](screens/15-settings-assistant.png) |
| 16 | 设置 · MCP Server | 接入协议、工具与访问 Token | [HTML](16-settings-mcp.html) · [PNG](screens/16-settings-mcp.png) |
| 17 | 设置 · 基础设置 | 服务器地址与 Mock CORS | [HTML](17-settings-general.html) · [PNG](screens/17-settings-general.png) |
| 18 | 设置 · 日志与保留 | 条数或天数模式与清理影响 | [HTML](18-settings-logs.html) · [PNG](screens/18-settings-logs.png) |
| 19 | 设置 · 文件存储 | 存储摘要、扫描与孤儿清理 | [HTML](19-settings-storage.html) · [PNG](screens/19-settings-storage.png) |

## 登录与安全

| 画板 | 页面 | 内容 | 独立页面 / 原图 |
| --- | --- | --- | --- |
| 05 | 登录 | 内网账号与密码登录 | [HTML](05-login.html) · [PNG](screens/05-login.png) |
| 21 | 首次登录 · 修改密码 | 不可跳过的初始密码更新 | [HTML](21-password.html) · [PNG](screens/21-password.png) |

## 统一视觉规则

| 用途 | 颜色 |
| --- | --- |
| 页面背景 | `#EEF1F4` |
| 主要区域 | `#FFFFFF` |
| 控件 / 代码底 | `#F4F6F9` / `#F7F8FA` |
| 选中状态 | `#EDF2FC` |
| 主操作 / 当前导航 | `#17191D` |
| 信号色 | `#3568D4` |

- 保留少量成功绿、警告琥珀、错误红；图表使用实色线条与柱形，不用渐变。
- 卡片圆角 16px，侧栏 20px，按钮与输入框 10px，标签 8px。
- 同一套 228px 侧栏、56px 顶栏，14px 间距；1440×900 桌面画板。
- 当前为超级管理员视角；普通成员并非接口只读。变量、文件改删、分组与标签管理等权限均依据项目源码保留。
- 不新增右上角个人中心，也不重复左下角用户入口。

## 文件分工

- [原有基础样式](assets/glass.css) 保留早期版本的选择器，最终使用其中 flat 覆盖层；目录沿用旧名，不表示仍采用玻璃样式。
- [全套共享组件](assets/pages.css)、[公共壳与生成器辅助](build/ui.mjs)、[本地导航](assets/preview.js) 服务新增页面。
- [工作区](build/workspace-pages.mjs)、[组织与草稿](build/admin-pages.mjs)、[设置](build/settings-pages.mjs)、[新建与SOAP/改密](build/flows-pages.mjs) 可重复生成。
- [页面清单](pages.json) 为预览、截图及覆盖核对的共同清单。
- [四组总览](boards/) 由实际截图组合而成；[早期方向归档](archive/) 不在当前画板目录中。

## 重新生成与检查

```bash
# 在仓库根目录执行；需要 Node.js 和本机 Chromium
bash design/liquid-glass/build/shot.sh

# 可自行指定浏览器
CHROME=/path/to/chrome-headless-shell bash design/liquid-glass/build/shot.sh
```

[截图脚本](build/render.mjs) 使用 Chrome DevTools Protocol pipe，无需启动 HTTP 服务。它输出 22 张 2x 页面原图、4 张总览和画廊预览，并记录 [浏览器检查报告](build/audit.json)：检查可见阴影/渐变/模糊、顶部重复用户、导航和内容溢出、脚本异常及资源加载错误。

## 匹配规则完整工作区

[匹配规则稿](22-match-rules.html) 为接口编辑状态，不新增业务路由：左侧返回体、中央 AND 条件、右侧请求样例/字段树。保留只读类型标签、唯一无规则兜底、按返回体顺序首条命中和等概率随机，不虚构 OR、类型下拉、拖拽优先级或权重随机。从日志导入仍标为暂未开放。

带“建议新增 · 本地演示”的样例试配是新增设计建议，**不表示当前产品已有即时测试 API**，也不是日志页现有 AI 匹配诊断。演示代码只验证简化场景；Java 正则、精确数值及真实请求结果仍以服务端为准。

## 边界

- 这是静态设计稿和少量本地状态演示；**没有修改业务前端、没有连接后端、不会保存真实配置、写入接口或删除文件。**
- 所有账号、计数、Token 和密钥状态都是演示；不包含真实凭证。
- 已对 1440×900 画板检查布局；这不是移动端或全响应式业务实现。
- 设置中的“文件存储”维护 Mock 文件及孤儿文件，与独立“文件服务器”管理页分开。
- 本轮未自动提交或推送；沿用当前 UI 优化分支供审阅。
