# 权限与正式 UI 发布核对

## 发布内容

- 全局 USER / SUPER_ADMIN，团队 VIEWER / EDITOR / TEAM_ADMIN；资源属于团队，不因创建者离队而删除。
- ACTIVE / DISABLED 账号；sid 网页会话、认证版本与个人 MCP 分离。普通网页退出不撤销 MCP，改密/重置/停用撤销安全凭证。
- 团队归档冻结管理写入（超管也一样），但不自动停止已启用公共 Mock。
- API 原 revision CAS；历史/回滚、30 天回收站、恢复默认停用、管理员彻底删除与文件引用保护。
- 正式 flat 响应式页面；唯一左下用户入口；当前团队作用域，不提供资源私有/仅创建者编辑开关。
- MCP 明确按队范围，运行时与本人当前权限和实例工具策略取交集；Web 显式确认不会绕过源凭证撤销或过期。
- 完整导出 2.3 包含 SOAP WSDL / FILE 响应附件和标签映射。导入生成新的目标团队文件引用；覆盖以目标 API 原版本为准，不借用源导出 revision。
- 日志 V/E 返回脱敏副本；管理员网页可看本来被授权的原始日志，所有外发 AI prompt 均脱敏。不可重放的私密条件明确 UNKNOWN。

## 升级前

1. 完全停止待升级业务实例并确认写入线程退出，再备份整个数据目录：SQLite 主库及残留 WAL/SHM、Mock 响应文件、WSDL、独立文件服务器和配置。不能停机时须用 SQLite 在线备份与一致性文件快照；不要边写边用普通文件复制代替一致性备份。
2. 在备份副本上试启动新包，确认 schema_version 最新为 11、资源数和团队/成员关系符合预期。不要把本项目隔离 UI 测试目录当成业务数据目录。
3. 核对 SUPER_ADMIN 可登录；旧全局 TEAM_ADMIN 仅归一为 USER，不会自动升级为系统超管。团队旧 MEMBER 迁为 EDITOR。
4. 历史团队若没有真实 ACTIVE TEAM_ADMIN，系统超管须显式指定有效管理员；系统维护身份不伪装成团队成员。
5. 旧不含 sid/auth_version 的 JWT、旧无显式 scope 的 MCP 凭证失效：网页重新登录，个人 MCP 按当前团队范围重新签发。不要复用旧密钥或分享网页 JWT 给 MCP。
6. 新建账号仍需首次改密。首次初始化应先仅绑定 loopback，完成初始管理员改密、配置独立签名密钥及受信任 TLS/反向代理后再开放访问。停用/密码变更后的旧 sid 和个人 MCP 不会因恢复账号而复活。

迁移在事务内执行；失败应拒绝启动，不能跳过安全迁移继续运行。迁移不会自动把未知历史 AI 会话重新归队。

## 安全回退

- 这是前向 schema 与权限语义升级，不能仅替换成旧 JAR 后继续使用新数据库。回退须停止新实例并恢复同一时点的整套数据库、文件、配置备份，再启动相应旧版本。
- 不要手工把 schema_version 改小或把 VIEWER 改成 MEMBER 来骗过旧程序。
- 升级前后备份含原始请求信息和配置密钥，应限制操作系统访问权限并按业务保留政策处理。

## 发布构建

使用已有 Java 8、Maven 3.9.16、Node 26（Vite 8 native binding 需可用的 Node 环境）。不需要修改全局工具链。

~~~sh
# 在 frontend 目录
node --test tests/*.test.mjs
node node_modules/vite/bin/vite.js build

# 回仓库根目录，JAVA_HOME 指向 Java 8
mvn -o test
mvn -o -DskipTests package
~~~

前端生产文件输出至后端静态资源目录，必须先构建前端再打包 JAR；不是 frontend/dist。既有 Monaco/ECharts 大 chunk 警告不等于构建失败。

## 隔离验证（不能指向生产）

验证实例必须仅绑定 loopback，并使用仓库 target 下全新 ui-smoke.* 临时目录。先运行账号壳基线，再运行编辑/MCP 生命周期测试；脚本会核对数据目录及本次临时凭证归属。

~~~sh
node frontend/tests/browser-shell-smoke.mjs --isolated --state=target/ui-smoke-state.json
node frontend/tests/browser-editor-smoke.mjs --isolated --state=target/ui-smoke-state.json
node frontend/tests/browser-mcp-confirm-smoke.mjs --isolated --state=target/ui-smoke-state.json
node frontend/tests/live-lifecycle-mcp-smoke.mjs --isolated --state=target/ui-smoke-state.json
~~~

- 最终内嵌前端 JAR 在全新隔离数据库上完成 92 项真实检查；后端 539、前端 278 项单元/集成检查均通过，生产构建与打包成功。
- 浏览器脚本使用真实 MockHub HTTP、Vue、Element Plus、Monaco 与富文本编辑器，不替换业务 API。其它环境可用 CHROME 指定已有本地 Chromium。
- 组件 fixture 脚本明确使用 API stub，只证明组件协议、状态和布局；不能把它们宣称为后端或真实外部模型调用。
- 基线 shell 的“无团队”用例要求全新库；不要在创建了生命周期测试团队后再次把同一库当全新库测试。
- 测试报告/截图/本地凭证均被忽略，不提交测试数据库、token 明文、依赖或缓存。用完须停止隔离实例与浏览器。

## 上线验收

- VIEWER 能读本队资源、按“我创建的”筛选，不能通过直接 HTTP 写入；EDITOR 可修改队友接口/分组/标签，不能管理成员或彻底删除。
- 团管不能把最后一个有效团管移除/停用；最后一个有效系统超管受同样保护。跨队 ID、空团队和未知角色不能变成全库权限。
- 两个编辑者保存同一基线，后到者 40921，草稿保留，不能自动重取新版本强盖。
- 回收站 API 不可公共调用。路径冲突恢复 40922，不覆盖替代接口；恢复后默认停用。回滚不恢复创建者/归属或运行计数。
- 历史/回收站/有效草稿引用和有效上传租约保护文件；外队和未知附件引用拒绝。
- WSDL 上传需要真实同队写权限，原始文件名仅展示。新文件随机同队安全 key；任意路径/符号链接/危险 XML/外队引用拒绝，公开 ?wsdl 只读实际路由团队文件。
- 完整 SOAP ZIP 导入他队时有新的目标 WSDL key，文件/规则/操作/标签保留；导入中失败不会留下部分 API/历史或删除旧 WSDL。
- MCP token 只见显式团队与能力；超管 owner 也不能越过 token 范围。普通网页退出 MCP 仍可用；安全改密/撤销后旧 MCP 401。
- 源 MCP token 撤销/过期/owner 停用后，关联草稿即使走 Web 显式确认也被拒绝。DRAFT_ONLY 不等于禁止人类确认；自动 apply 仍需原/现 APPLY 能力及 DIRECT 策略。
- 离队后私有 AI 标题/preview/messages/cards 不可读；不能将 Team A conversationId 搭配 Team B 续聊。超管不因此获得别人私人会话内容。
- 375/768/1440 页面可自然滚动，无画板缩放、文档横溢、渐变或装饰阴影；长文本/完整正文不被伪截断为“完整”。

## 明确边界

- 有效旧 WSDL 及其保留历史文件不会被本次升级冒进删除。独立上传成功但未保存 API 的 WSDL 可能仍占空间；本次不宣称新增了其完整租约/崩溃恢复/GC。导入回滚只清理本次新 key，文件系统清理为安全 best-effort。
- 原来未开放的“从请求日志导入规则”入口继续明确禁用，不伪造成功；真实请求样例试配已由同一服务端 matcher 提供。
- 未使用真实第三方模型账号做付费测试；provider 协议/脱敏使用本地 mock HTTP 验证。配置好真实 provider 后，由业务人员做一条非敏感请求的连接验收。
- 此仓库验证不等于已替换正在运行的业务实例。部署和真实数据目录切换需按上述备份与验收流程执行。
