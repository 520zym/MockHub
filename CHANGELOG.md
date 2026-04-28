# Changelog

本项目遵循 [Semantic Versioning](https://semver.org/)。

## [1.4.4] - 2026-04-28

### 新增

- **接口分组**：团队下可按业务维度建分组管理接口，支持拖拽排序
  - 编辑页加分组下拉选择器，支持「+ 新建分组...」就地创建（仅团队管理员/超管）
  - 列表页加分组筛选下拉、分组列展示、「管理分组」入口（弹窗内拖拽排序、改名、删除）
  - 删除分组时把组内接口的 `group_id` 置空（保留接口本体），避免误删
  - 数据迁移：`migrateV2` 兜底已存库的 `api_definition.group_id` 列
- **批量操作**：列表多选后批量启用 / 禁用 / 删除 / 移动到分组
  - 单一 `POST /api/apis/batch` 入口（action: enable/disable/delete/move-group）
  - 后端按团队聚合权限校验，IN 子句批处理 SQL，写一条按团队聚合的汇总操作日志
  - 前端跨页选择保留（`reserve-selection` + `row-key`），操作完成自动清空
  - 跨团队所选时拒绝批量移动分组（分组与团队强绑定）
- **路径冲突实时预检**：编辑页 path / method 变化 debounce 400ms 调
  `GET /api/apis/check-path`，输入框右侧三态图标（loading / 对勾 / 叉），
  冲突时拦截保存并展示「已存在同路径接口：xxx」
- **未保存修改离开提示**：编辑页双快照（form + globalHeader）dirty 检测，
  路由切走 / 关 tab / 刷新均会拦截，二次确认后离开
- **SOAP operation 独立接口描述**：每个 operation 可填写独立描述（多行 autosize），
  与接口级 description 分离，便于标注单个 operation 的用途 / 参数 / 注意事项
- **WSDL documentation 自动填充描述**：上传 WSDL 时从 `<wsdl:documentation>` 自动
  提取描述文本填入 operation.description；已有非空描述不被重复上传覆盖

### 改进

- **接口列表自定义排序**：表头点击按修改时间 / 名称 / 路径升降序，状态持久化到 store
  - 后端排序字段走白名单 Map 防 SQL 注入，sortDir 大小写不敏感非法值兜底 DESC
- **操作列固定右侧**：启用开关与操作按钮固定右列，横向滚动也始终可见
- **侧边栏折叠**：顶栏左上角加显眼的折叠 / 展开按钮
- **修改时间列**：改用 `yyyy-MM-dd HH:mm:ss` 完整格式（删除原相对时间）
- **创建人列回填**：列表批量预取用户字典回填 `createdByName`，避免 N+1 查询
- **分组列显示统一**：未分组 / 跨团队分组找不到 / 分组已删除一律显示 `-`，
  不再出现「已删除」字样
- **禁用状态可视化**：列表中禁用接口整行 opacity 0.55，hover 恢复

### 性能

- **SOAP 编辑页大响应体滑动不再卡顿**（之前 3 × 1MB 响应体场景下掉帧明显）：
  - `MonacoEditor` 关闭 `automaticLayout` 100ms 轮询，改 ResizeObserver + rAF
    事件驱动触发 layout，实例数量与轮询数解耦
  - SOAP operation 卡片默认全部折叠，展开才 `v-if` 挂载 ResponseTabs / Monaco，
    页面顶部提供"全部展开 / 全部折叠"按钮；折叠态展示描述首行预览
  - 响应体 > 500KB 时降级为只读预览卡，点"全文编辑"弹 fullscreen Dialog 承载
    Monaco（草稿副本独立，确认才回写），避免大文本在长页面嵌入 Monaco 时的
    重排成本传染到父页面滚动

### 修复

- **EP 2.x sticky 列叠字**：横向滚动时 fixed-right 列与左侧普通列表头叠字，
  通过 `.el-table-fixed-column--right` 显式白底 + 高 z-index + 调低 sortable 列
  z-index 解决
- **SQLite JDBC setObject(null) NPE**：批量更新分组到 NULL 时驱动会 NPE，
  改用 SQL 字面量 `SET group_id = NULL` 分支拼接

### 权限

- **GroupService 写操作收紧**：创建 / 更新 / 删除 / 排序分组要求团队管理员或超管，
  普通成员只读（与标签管理一致）

### 内部

- **引入 `schema_version` 表**：后续 DB 迁移按版本号幂等执行，v1.4.3 老库无缝
  升级到 v1.4.4
  - `DataSourceConfig.executeMigrations` 重构为 `if (current < N) migrateVN`
    分发器，每个 migrateVN 写入 schema_version 记录
  - 整个迁移过程事务包裹，任一步失败全部回滚，杜绝半迁移状态
  - 原有 `api_definition.description` 列补建、REST/SOAP 响应迁移逻辑收拢进
    `migrateV1`，保持幂等
  - 覆盖 4 种启动场景的单元测试：全新库 / v1.4.3 老库 / 已跑过
    soap-mock-enhancement 的本地库 / 二次启动
- **ApiRepositoryTest 新增 20 个用例**：覆盖排序白名单、SQL 注入兜底、批量
  方法（findByIds / batchUpdateEnabled / batchUpdateGroup / batchDeleteByIds）
  及空 ids 边界场景

## [1.4.2] - 2026-04-13

### 新增

- 响应体编辑器「插入变量」按钮与 `{{` 补全建议（内置 5 个变量 + 自定义变量）
- **自定义动态变量**：团队级维护命名的值集合，支持按分组组织
  - 顶级菜单「动态变量」，三栏布局（变量 / 候选值 / 分组）
  - 候选值支持批量粘贴导入，值可同时属于多个分组（M:N）
  - Mock 响应体中 `{{pet}}` 从全部值随机、`{{pet.mammal}}` 从指定分组随机
  - 无法解析的自定义占位符 fail-fast → HTTP 500 + `{code:50101,...}` 统一错误格式
  - 团队隔离，超管全团队可写 / 团队管理员本团队可写 / 普通成员只读
- 编辑器内 `{{xxx}}` 占位符不再被 JSON/XML 语法检查标红（拦截 `setModelMarkers`
  过滤落入占位符区间的诊断 marker，保留其他真实语法错误）

## [1.4.1] - 2026-04-12

### 修复

- 接口管理列表的标签筛选支持多选（命中任一标签即返回）
- 接口名称列宽度过窄,改为随容器自适应（最小 180px）
- 接口编辑页响应体自动识别 Content-Type 后,格式标签会同步切换；格式化按钮按当前选中的格式（JSON / XML / Text）分别处理,修复 XML 内容被强制按 JSON 格式化的问题

### 其他

- 前端构建产物（`src/main/resources/static/assets/`）不再入库,改由构建流程生成

## [1.4.0] - 2026-04-11

### 首次公开发布

- REST Mock：支持 GET / POST / PUT / DELETE / PATCH，自定义状态码、响应头、响应体
- SOAP Mock：上传 WSDL 自动解析 Operation，独立配置每个操作的返回 XML
- 团队隔离：多团队独立管理接口，Mock 路径按团队标识隔离
- 路径参数：支持 `/api/user/{id}` 风格路径匹配，响应体引用 `{{path.id}}`
- 动态变量：`{{timestamp}}`、`{{uuid}}`、`{{date}}`、`{{datetime}}`、`{{random_int}}` 等
- 多返回体：单个接口可配置多个响应体，支持切换活跃返回体
- 接口描述：支持富文本描述接口用途和说明
- Monaco Editor：内置代码编辑器，JSON / XML / 纯文本语法高亮和格式化
- WSDL 托管：上传的 WSDL 通过 `/wsdl/{fileName}` 直接访问
- 全局响应头：团队级别公共响应头，接口级别可覆盖
- 导入导出：按团队导出接口定义，支持合并或覆盖导入
- 操作日志 / 请求日志：支持按条数或天数自动清理
- 权限控制：超级管理员 / 团队管理员 / 普通成员 三级权限
- 用户管理：密码修改、超管重置密码
- CORS 支持：Mock 接口默认允许跨域
- SQLite 嵌入式存储：单文件数据库，零外部依赖
