# UI 设计规范

## 设计风格：Soft UI / Clean Dashboard

整体视觉追求**轻盈、通透、无压迫感**。核心特征：

### 基础调性
- **背景**：页面底层使用柔和渐变色（如浅蓝灰到浅紫灰的 135 度渐变），不使用纯白或纯灰
- **卡片**：白色背景，**无边框**，通过柔和阴影（`box-shadow: 0 2px 12px rgba(0,0,0,0.04)`）制造层次
- **圆角**：大圆角为主，卡片 16px，按钮 10px，输入框 10px，标签胶囊 20px
- **阴影**：极淡、扩散范围大的柔和阴影，禁止使用硬边阴影
- **留白**：宽松大方，卡片内 padding 不小于 20px，元素间距不小于 12px

### 色彩体系
- **主色调**：一个品牌主色（建议深蓝 `#4318FF` 或靛蓝 `#6366F1`），用于导航高亮、主要按钮、关键数据
- **辅助色**：柔和的绿（成功/启用）、琥珀（警告）、玫红（错误/禁用）
- **数据卡片**：每个统计卡片用不同的淡色背景（如浅蓝、浅绿、浅橙），不使用纯白
- **文字**：主文本 `#1B2559`（深蓝黑），次要文本 `#A3AED0`（灰蓝），不使用纯黑

### 导航侧边栏
- 宽度约 220px，背景色白色或极浅色
- 导航项：图标 + 文字，未选中时灰色文字
- **选中项**：主色背景色块（大圆角胶囊形），白色图标和文字
- 导航项间距宽松，每项高度约 44px
- 底部可放用户信息或操作入口

### 表格
- **无边框**：不使用网格线，行与行之间通过间距和悬停效果区分
- 表头：灰色小字，字号略小于正文
- 行高宽松（48~56px），鼠标悬停行变浅色背景
- 状态列使用彩色圆点/标签指示
- HTTP 方法列使用彩色标签（GET 绿色、POST 蓝色、PUT 橙色、DELETE 红色）

### 表单与输入框
- 输入框：浅灰背景色，**无边框**或极淡边框，聚焦时主色描边
- 下拉选择、开关、标签输入等组件保持一致的无边框风格
- 按钮：主要操作用主色实心圆角按钮，次要操作用浅色/透明按钮

### 排版
- 页面标题：大号粗体（24~28px），深色
- 统计数字：超大号粗体（32~40px），使用颜色区分含义
- 正文：14~15px，行高 1.6

---

## Element Plus 主题覆盖要点

使用 Element Plus 时需覆盖以下默认样式以匹配 Soft UI 风格。在 `styles/variables.scss` 或 `styles/element-overrides.scss` 中统一设置：

### CSS 变量清单

```scss
// ========== 主色 ==========
--el-color-primary: #6366F1;          // 靛蓝，用于导航高亮、主按钮
--el-color-primary-light-3: #818CF8;
--el-color-primary-light-5: #A5B4FC;
--el-color-primary-light-7: #C7D2FE;
--el-color-primary-light-9: #E0E7FF;
--el-color-primary-dark-2: #4F46E5;

// ========== 语义色 ==========
--el-color-success: #10B981;          // 绿色，启用/成功状态
--el-color-warning: #F59E0B;          // 琥珀，警告
--el-color-danger: #EF4444;           // 红色，错误/禁用/删除
--el-color-info: #6B7280;             // 灰色，信息/次要操作

// ========== 圆角 ==========
--el-border-radius-base: 10px;        // 按钮、输入框等
--el-border-radius-small: 8px;        // 小型组件
--el-border-radius-round: 20px;       // 胶囊形标签

// ========== 文字颜色 ==========
--el-text-color-primary: #1B2559;     // 主文本（深蓝黑）
--el-text-color-regular: #4A5568;     // 正文
--el-text-color-secondary: #A3AED0;   // 次要文本（灰蓝）
--el-text-color-placeholder: #CBD5E0; // 占位符

// ========== 边框 ==========
--el-border-color: transparent;        // 默认无边框
--el-border-color-light: #F1F5F9;
--el-border-color-lighter: #F8FAFC;

// ========== 背景 ==========
--el-bg-color: #FFFFFF;
--el-bg-color-page: transparent;       // 页面背景用渐变，不用纯色
--el-fill-color-light: #F7F8FA;        // 输入框背景
--el-fill-color-lighter: #FAFBFC;

// ========== 阴影 ==========
--el-box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
--el-box-shadow-light: 0 1px 6px rgba(0, 0, 0, 0.03);
```

### 组件级覆盖样式

**表格（`el-table`）**：
- 去除所有边框线（`border: none`，包括单元格边框）
- 去除默认斑马纹，改用行悬停高亮背景色 `#F7F8FF`
- 表头背景透明，表头文字 `--el-text-color-secondary` + 小字号

**按钮（`el-button`）**：
- 主按钮（`--primary`）圆角 `12px`，hover 时微上浮 + 加深阴影
- 次要按钮（`--default`）透明背景 + 文字色为主色，hover 时浅主色背景 `#E0E7FF`

**输入框（`el-input`）**：
- 默认背景色 `#F7F8FA`，无边框
- 聚焦时显示 `2px` 主色边框（`--el-color-primary`），背景变白

**对话框/抽屉（`el-dialog` / `el-drawer`）**：
- 圆角 `16px`
- 阴影加大：`0 8px 40px rgba(0, 0, 0, 0.08)`

**卡片（`el-card`）**：
- 去除默认边框（`border: none`）
- 圆角 `16px`
- 阴影 `0 2px 12px rgba(0, 0, 0, 0.04)`
- 内边距不小于 `20px`

**菜单/侧边栏**：完全自定义样式，不使用 Element Plus 默认菜单组件（或深度覆盖）

---

## 前端路由结构

```
/login                   登录页
/                        主页（重定向到 /apis）
/apis                    接口列表（主页面）
/apis/:id/edit           接口编辑
/apis/new                新建接口
/teams                   团队管理（超级管理员）
/users                   用户管理（超级管理员）
/logs/operation          操作日志
/logs/request            请求日志
/settings                全局设置（超级管理员）
```

前端路由使用 **hash 模式**（`#/`），避免 Spring Boot 静态资源托管刷新 404。

---

## 页面布局

### 全局布局

```
┌──────────────────────────────────────────────────────────────┐
│  侧边栏（220px）              │  顶栏（搜索 + 通知 + 用户头像）│
│  ┌────────────────────┐       │──────────────────────────────│
│  │  MockHub Logo      │       │                              │
│  ├────────────────────┤       │  主内容区                     │
│  │  ● 接口管理        │       │  （各页面内容）               │
│  │    团队管理        │       │                              │
│  │    用户管理        │       │                              │
│  │    操作日志        │       │                              │
│  │    请求日志        │       │                              │
│  │    全局设置        │       │                              │
│  └────────────────────┘       │                              │
└──────────────────────────────────────────────────────────────┘
```

### 接口列表页

```
┌─────────────────────────────────────────────────────────────┐
│  侧边栏（220px）          │  接口管理                        │
│                            │                                 │
│  全局导航                  │  ┌─ 工具栏 ─────────────────┐  │
│  （如上）                  │  │ 搜索框  标签筛选  + 新建  │  │
│                            │  └─────────────────────────┘  │
│  ── 团队筛选树 ──         │                                 │
│  ├─ 所有接口              │  ┌─ 接口表格 ────────────────┐  │
│  ├─ [FE] 前端团队        │  │ 方法  路径  团队  分组      │  │
│  │   ├─ 用户模块         │  │ 状态码  延迟  标签  启用    │  │
│  │   └─ 订单模块         │  │ 操作（编辑/复制/删除）      │  │
│  └─ [BE] 后端团队        │  └──────────────────────────┘  │
│      └─ 未分组            │                                 │
└─────────────────────────────────────────────────────────────┘
```

左侧团队筛选树可以和全局导航共存于同一个侧边栏中——导航在上，团队树在下（接口管理页面时展示）。

### 侧边栏团队筛选树交互规格

**位置**：全局导航菜单下方，仅在接口管理页面（`/apis`）可见。与全局导航共享同一个侧边栏，导航在上、筛选树在下，中间用细分割线分隔。

**数据来源**：
- 团队列表：调用 `GET /api/teams` 获取当前用户可见的团队
- 分组列表：对每个团队调用 `GET /api/groups?teamId=xxx` 获取其下的分组
- 接口计数：从接口列表响应的 `total` 或本地统计中获取

**树结构**：
```
所有接口           ← 点击：清除筛选，显示全部
├─ [FE] 前端团队   ← 点击：筛选该团队全部接口（teamId 筛选）
│   ├─ 用户模块    ← 点击：筛选该分组接口（teamId + groupId 筛选）
│   └─ 订单模块
└─ [BE] 后端团队
    ├─ 支付模块
    └─ 未分组      ← 没有分组的接口归入此虚拟节点（groupId=null 筛选）
```

**选中态**：
- 单选模式，同时只能有一个节点处于选中状态
- 选中节点：主色浅背景（`#E0E7FF`）+ 主色文字（`#6366F1`），左侧带 3px 主色竖条指示器
- 未选中节点：`--el-text-color-regular`（`#4A5568`）文字，hover 时背景变 `#F7F8FA`

**团队标识**：
- 使用 `TeamTag` 组件（彩色胶囊标签）显示在团队名称前
- 胶囊标签显示团队 `identifier`（如 `FE`、`BE`），背景色使用团队 `color` 的浅色变体（opacity 0.15），文字使用团队 `color` 原色

**接口计数**：
- 每个节点右侧显示该节点下的接口数量，使用灰色小字（`--el-text-color-secondary`，12px）
- "所有接口"节点显示总接口数
- 团队节点显示该团队下的接口总数
- 分组节点显示该分组下的接口数
- "未分组"节点显示该团队下 `groupId=null` 的接口数

**折叠/展开**：
- 团队节点默认展开
- 点击团队节点左侧的折叠图标可收起/展开分组列表
- 折叠/展开不影响当前选中状态
- 折叠图标使用小三角（`el-icon-arrow-right`），展开时旋转 90 度，带 200ms 过渡动画

**联动行为**：
- 选中节点后，更新 `appStore.currentTeamId` 和 `appStore.currentGroupId`
- 主内容区的接口表格自动响应 store 变化，发起带对应 `teamId` / `groupId` 参数的查询请求
- 选中"所有接口"时，清除 `teamId` 和 `groupId` 筛选
- 选中团队节点时，设置 `teamId`、清除 `groupId`
- 选中分组节点时，同时设置 `teamId` 和 `groupId`
- 选中"未分组"节点时，设置 `teamId`，`groupId` 传特殊值（如空字符串 `""`）表示只查未分组接口

### 接口编辑页

分区式表单布局，每个分区是一个 Soft UI 卡片：

**卡片 1：基本信息**
- 接口名称、所属团队、分组、HTTP 方法、路径
- Mock 地址展示区：显示完整调用地址（如 `http://host:port/mock/FE/api/user/info`），旁边复制按钮

**卡片 2：返回体配置**
- 响应状态码、Content-Type（自动识别 + 手动切换）
- Monaco Editor（占主要面积）：
  - 根据 Content-Type 自动切换语言模式（json / xml / plaintext）
  - 顶部显示"自动识别：JSON ✓"，可点击手动切换
  - 格式化按钮、上传文件按钮
  - 语法错误编辑器内标红，不阻止保存
  - 大文本时禁用 minimap

**卡片 3：标签**
- 标签输入组件：聚焦弹出下拉，展示团队已有标签，模糊搜索，底部"创建新标签"选项

**卡片 4：高级配置**
- 响应延迟（ms）
- 全局响应头覆盖

### SOAP 接口编辑页

在基本信息区额外展示：
- WSDL 上传区：上传后自动解析，列出所有 Operation
- WSDL 托管地址展示 + 复制按钮
- 每个 Operation 展开为独立配置区：
  - Operation 名称、SOAPAction
  - 响应状态码、延迟时间
  - Monaco Editor（xml 模式）编辑返回 XML

---

## 前端源码结构

```
frontend/
├── src/
│   ├── App.vue
│   ├── main.js
│   ├── styles/
│   │   ├── variables.scss        # Soft UI 设计变量（颜色、圆角、阴影等）
│   │   ├── element-overrides.scss # Element Plus 主题覆盖
│   │   └── global.scss           # 全局样式（渐变背景等）
│   ├── views/
│   │   ├── Login.vue
│   │   ├── ApiList.vue
│   │   ├── ApiEdit.vue
│   │   ├── TeamManage.vue
│   │   ├── UserManage.vue
│   │   ├── LogView.vue
│   │   └── Settings.vue
│   ├── components/
│   │   ├── layout/
│   │   │   ├── AppLayout.vue     # 整体布局（侧边栏 + 顶栏 + 内容区）
│   │   │   ├── Sidebar.vue
│   │   │   └── Topbar.vue
│   │   ├── MonacoEditor.vue
│   │   ├── TagInput.vue          # 带下拉联想的标签输入
│   │   ├── TeamTag.vue           # 彩色团队标识
│   │   ├── HttpMethodTag.vue     # 彩色 HTTP 方法标签
│   │   └── CopyButton.vue       # 带反馈的复制按钮
│   ├── router/
│   │   └── index.js
│   ├── api/                      # Axios 封装 + 各模块 API 调用
│   │   ├── request.js            # Axios 实例、拦截器、JWT 注入
│   │   ├── auth.js
│   │   ├── apis.js
│   │   ├── teams.js
│   │   └── ...
│   └── stores/                   # 状态管理（Pinia 或 Vue 3 reactive）
│       ├── user.js               # 当前用户信息 + 权限
│       └── app.js                # 全局状态（当前团队等）
├── package.json
└── vite.config.js
```
