# MockHub 权限实现差距与改造方案

> 日期：2026-10-29｜状态：实施前评审稿｜本轮只核对代码与编写方案，未修改业务代码、未执行迁移、未提交或推送。
>
> 阅读顺序：先看第 1 节的三个建议，再看第 2 节 P0 风险与第 10 节开发顺序。静态稿通过不等于业务授权已经实现。

## 0. 已确认的产品边界

- 一个账号可属于多个团队，每个团队独立授予角色；保留多对多成员关系。
- 接口归团队所有；成员离队、被移除或账号停用，都不删除其创建的接口。
- 团队只读成员不能修改；可编辑成员可以修改本团队所有人的接口。创建人只用于筛选与审计，不参与授权。
- 不增加“允许其他成员编辑”“仅创建者”等接口级权限开关，也不增加个人私有接口空间。
- 用创建人/修改人、版本记录与回滚、30 天回收站降低误操作风险。
- 全局身份保留 USER / SUPER_ADMIN；团队身份采用 VIEWER / EDITOR / TEAM_ADMIN（存储枚举为本方案建议）。

**必须纠正前文的一点：离队不等于删除账号，更不等于抹掉创建人。** 离队只移除成员关系，创建者信息应保留。真正删除账号时才考虑置空关联 ID，并保留审计中的姓名快照；不能把“人走接口留”理解为“创建人一律变空”。

设计入口：[权限稿总览](index-standalone.html)、[权限稿说明](README.md)、[基础稿总览](../index-standalone.html)。

## 1. 三个实施决策：推荐答案与取舍

以下三项是**推荐待确认**，不是把此次“同意写方案”当成已经授权修改代码。

| 决策 | 推荐答案 | 原因 | 不做什么 |
| --- | --- | --- | --- |
| D1 账号停用 | 做：ACTIVE / DISABLED，优先停用而不是硬删账号 | 离职/泄露时可封禁登录与管理 API，保留团队资产与审计；页面已有此需求 | 不把停用账号变成删除接口，不自动停所有 Mock |
| D2 权限变更多快生效 | 权限变更事务提交后，下一次请求按数据库当前身份校验 | 不等 JWT 8 小时过期；API、AI、MCP 都要执行当前权限检查 | 不依赖前端隐藏按钮，不把短 JWT / refresh token 当必需前提 |
| D3 当前团队存哪里 | 前端按账号、浏览器标签页存展示上下文；请求携带目标团队或资源 ID，后端独立校验 | 切团队不必重新登录；多个标签页可同时开不同团队；URL/请求伪造不能越权 | 不把 currentTeam 放进 JWT 当授权依据，不新增“切团队换 token”流程 |

### D1：停用的完整行为

1. 仅超管可停用/恢复账号；停用前检查该账号是不是系统或某个活跃团队的最后一位**有效**管理员。
2. 停用与 auth_version 递增、凭证撤销在同一事务提交；新登录失败，旧 JWT 下次管理请求失败。
3. MCP 每次请求也检查账号状态。建议停用时直接撤销该账号 MCP 凭证，恢复账号后旧凭证不复活，需要重新创建。
4. 不删除成员关系或接口；恢复后团队关系可保留，但必须重新登录。
5. 停用某成员不会改变团队对外 Mock 的运行。公开文件上传/下载、Mock 运行是独立策略，不能借此宣称已全部被账号停用限制。
6. 账号物理删除建议延后；若保留旧删除接口，也必须先做同样的管理员保护与显式关联清理。

### D2：不需要先搭建一套复杂的刷新令牌系统

建议首期保留现有 8 小时 JWT 体验，增加 user.status、user.auth_version，JWT 带认证版本；每个已认证管理请求读取当前账号状态、当前 globalRole，而不是信任旧 token 的角色快照。

- 登录时查账号状态；旧 JWT 缺少新版认证版本时在升级后统一要求重新登录，不偷偷当成有效新版 token。
- 成员角色以 user_team 当前记录为准；移除/降级不必把所有成员踢出登录，也不依赖 JWT 中的 teamIds。
- 改密码、管理员重置密码、停用、全会话撤销时递增 auth_version；改密码后明确要求重新登录。
- 查询列表、导出、统计的可访问团队集合必须实时取，不能只修详情或保存接口。
- 写入时在事务内重查当前权限；权限更新与资源写入按 SQLite 写事务顺序确定结果。已完成的请求无法撤回，不承诺中断已经发送的响应。
- AI 草稿创建时可校验，**确认落库时必须再校验**；MCP 长连接不能只在建立连接时认证。
- 前端收到降权错误后刷新“我的身份”；仍有读取权可保留未提交草稿供复制，被移出团队则停止拉取并清除该团队缓存，不继续展示服务器数据。已经下载到用户设备的内容无法由权限系统追溯收回。
- AI 草稿预览、持久化对话中的团队数据副本也是读取入口，不能只修最终确认。建议含团队资源的草稿/历史接口按来源 team_id 重查权限，撤权后仅返回不可访问占位，不继续返回旧响应体；混合多团队历史必须按内容来源过滤，否则要限制会话团队范围。

**JWT 与 MCP 的撤销政策要分别实现（以下为建议）：** 本人改密、超管重置密码、停用账号等安全事件，递增 auth_version，并在事务内按 owner 显式撤销 MCP token；仅递增认证版本不会自动废除现有 MCP token。普通网页退出/退出全部网页登录只影响网页会话，不默认中断外部 MCP 客户端；若提供“紧急撤销全部凭证”，则同时撤销两者，按钮需明确标识。无 sid 首期可新增 POST /api/me/revoke-web-sessions 来撤销全部网页登录，不能伪装成单设备撤销。

**“会话管理页”单独说明：** 当前没有登录会话表或 sid。auth_version 能做全部设备失效，但做不了“只退出这一台设备”。如果实施个人账号稿中的设备会话列表/单设备撤销，需要增加 auth_session 和 JWT sid；这列为独立任务，不能拿 AI 对话记录冒充登录会话。

### D3：展示上下文不等于服务端安全上下文

- 前端 Pinia 保存当前团队；sessionStorage 建议使用带 userId 的键，如 mockhub:&lt;userId&gt;:currentTeamId，不同标签页互不抢团队。
- 页面启动先读取 GET /api/auth/me（拟新增）的当前团队清单，再验证缓存的团队选择；选择失效则回到可访问团队或“尚未加入团队”。
- 团队资源列表/API 列表显式传 teamId；GET/PUT/DELETE /api/apis/{id} 必须先查资源真实 team_id，再鉴权，不接受客户端声称的所属团队。
- groupId、tagIds、responseId、文件引用必须校验属于同一目标团队；禁止用别队子资源 ID 绕过。
- 一期不支持通过通用编辑 DTO 修改 team_id；以后若支持跨团队转移，要单独接口，同时校验来源和目标权限。
- 兼容旧客户端不传 teamId 的列表时，只能实时聚合当前仍有权限的团队。新版团队工作区不默认跨团队聚合。
- teamId（内部 ID）、identifier（公开短标识）、显示名不能混用。Mock URL 继续使用 identifier；切团队不改变接口存储路径。

## 2. 代码核对：真实现状、差距与 P0

### 2.1 应先修的安全问题（P0）

| 编号 | 源码证据 | 风险 | 建议先做 |
| --- | --- | --- | --- |
| P0-1 | [重置密码控制器:123–137](../../../src/main/java/com/mockhub/system/controller/UserController.java#L123-L137) 直接调用 [用户基础方法:42–60](../../../src/main/java/com/mockhub/system/service/UserServiceImpl.java#L42-L60)；[安全配置:44–45](../../../src/main/java/com/mockhub/common/config/SecurityConfig.java#L44-L45) 只保证登录 | 缺少超管检查。按源码路径，普通已登录账号知道目标用户 ID 即可能重置他人密码，包含超管；这是权限提升风险，不是 UI 问题 | 增加专用 resetPassword Service 方法并做超管校验；不能给共用 updatePassword 一刀切加超管限制而误伤本人改密；补普通成员/团队管理员拒绝、超管允许的测试；接入凭证失效 |
| P0-2 | [接口列表:205–226](../../../src/main/java/com/mockhub/mock/service/ApiServiceImpl.java#L205-L226) 未指定 teamId 时使用 token 的团队快照；[JWT:68–75](../../../src/main/java/com/mockhub/common/util/JwtUtil.java#L68-L75) 带 teamIds/globalRole | 被移出团队后旧 JWT 仍可能列出旧团队接口元数据；旧全局角色也不能当实时授权 | 从数据库重建当前 Principal 与允许团队范围；测试“不带 teamId”的列表和旧超管 token 降级路径 |

本次结论来自静态源码追踪，**没有对运行中的实例尝试重置密码或攻击验证**。修复要独立评审；不要把 UUID 不好猜当权限控制。

### 2.2 已存在的能力：复用，不重造

| 能力 | 当前证据 | 结论 |
| --- | --- | --- |
| 一人多团队 | [成员关联表:34–41](../../../src/main/resources/schema.sql#L34-L41)、[分配请求:8–30](../../../src/main/java/com/mockhub/system/model/dto/AssignTeamsRequest.java#L8-L30) | 沿用 user_team，不新建另一套成员模型 |
| 团队读/管理员检查 | [PermissionChecker](../../../src/main/java/com/mockhub/common/util/PermissionChecker.java#L11-L29)、[实现:36–70](../../../src/main/java/com/mockhub/system/service/PermissionCheckerImpl.java#L36-L70) | 已有 checkTeamAccess / checkTeamAdmin，缺的是明确写能力和一致覆盖 |
| 创建人、修改人字段 | [接口表:60–92](../../../src/main/resources/schema.sql#L60-L92)、[列表创建人回填:228–248](../../../src/main/java/com/mockhub/mock/service/ApiServiceImpl.java#L228-L248) | created_by / updated_by 已有，补 DTO、筛选和全写入口维护即可 |
| 操作日志 | [日志表:140–150](../../../src/main/resources/schema.sql#L140-L150)、[记录/保留策略:81–124](../../../src/main/java/com/mockhub/log/service/LogServiceImpl.java#L81-L124) | 已有摘要，但没有完整可回滚快照；不能把 detail 文本当接口版本仓库 |
| 数据库版本迁移 | [迁移分发:168–223](../../../src/main/java/com/mockhub/common/config/DataSourceConfig.java#L168-L223) | 当前到 V7，继续追加版本，不能只改新库 schema |
| MCP 凭证校验 | [凭证服务:39–95](../../../src/main/java/com/mockhub/mcp/service/McpTokenService.java#L39-L95)、[认证服务](../../../src/main/java/com/mockhub/mcp/service/McpAuthService.java) | 已有随机 token、哈希存储、到期/撤销检查和当前用户/成员读取；缺少个人管理边界、团队/动作 scope、账号停用联动 |

### 2.3 前文需要纠正的技术判断

1. **不是“18 个入口全都只有成员校验”。** API 多数写入是成员校验，但 [分组写入](../../../src/main/java/com/mockhub/mock/service/GroupService.java#L67-L123)、[变量写入](../../../src/main/java/com/mockhub/mock/service/CustomVariableService.java#L144)、[文件编辑/删除](../../../src/main/java/com/mockhub/files/service/FileServerService.java#L122-L148)、[标签更新/删除](../../../src/main/java/com/mockhub/mock/service/TagService.java#L82-L120) 已要求管理员。需要按动作逐项替换，不能全局搜索替换。
2. 团队管理员现在不能自治管理成员：[成员列表/添加/移除/角色变更](../../../src/main/java/com/mockhub/system/service/TeamServiceImpl.java#L221-L275) 全部先检查超管。
3. [登录团队角色拼装:163–179](../../../src/main/java/com/mockhub/auth/service/AuthService.java#L163-L179) 依赖未统一加载的 user.teamRoles。应直接按成员查询拼装，并给前端当前 capabilities。
4. [统计服务:121–140](../../../src/main/java/com/mockhub/stats/service/StatsServiceImpl.java#L121-L140) 当前只允许团队管理员/超管；稿子让只读、可编辑看本队统计，需要改后端和 [前端路由限制](../../../frontend/src/router/index.js#L85-L90)。
5. 当前超管可跨团队维护，[权限实现:36–62](../../../src/main/java/com/mockhub/system/service/PermissionCheckerImpl.java#L36-L62) 直接放行。不能一边画“跨团队维护”，另一页又说超管必须先加入团队才能写。
6. 当前是硬删接口：[删除路径:474–492](../../../src/main/java/com/mockhub/mock/service/ApiServiceImpl.java#L474-L492) 同时删返回体、标签关联并清理文件；没有回收站。
7. [团队删除:193–211](../../../src/main/java/com/mockhub/system/service/TeamServiceImpl.java#L193-L211) 会先阻止含接口或文件的团队删除，不能简单说现在删团队必然级联删接口；归档尚无实现。
8. 数据库声明外键不代表运行时已启用。连接级外键行为要实测并明确配置，不能依赖纸面 ON DELETE SET NULL 来保证删账号后创建人置空。

## 3. 目标权限矩阵（建议用于统一接口与页面）

V=VIEWER 只读；E=EDITOR 可编辑；A=TEAM_ADMIN 团队管理员。超管列是**建议保留的显式系统维护能力**，不是假装成为某团队成员。

| 操作 | V | E | A | SUPER_ADMIN |
| --- | --- | --- | --- | --- |
| 本队接口/规则/分组/标签/变量读取 | ✓ | ✓ | ✓ | 可跨队维护 |
| 接口创建、编辑、启停、复制为新接口 | — | ✓（包括他人创建的） | ✓ | 可跨队维护 |
| 单个接口软删除、回收站恢复 | — | ✓ | ✓ | 可跨队维护 |
| 查看本队回收站、变更历史 | ✓ | ✓ | ✓ | 可跨队维护 |
| 回滚接口内容 | — | ✓ | ✓ | 可跨队维护 |
| 彻底删除回收站接口 | — | — | ✓ | ✓ |
| 本队统计、脱敏请求/操作日志 | ✓ | ✓ | ✓ | 可跨队查看 |
| 成员列表与公开团队信息读取 | ✓ | ✓ | ✓ | ✓ |
| 添加/移除成员、改团队角色、改本队名称/说明 | — | — | ✓ | ✓ |
| 平台账号、全局角色、停用/重置密码、创建团队、全局配置 | — | — | — | ✓ |
| 个人资料/改本人密码/本人凭证 | 本人 | 本人 | 本人 | 本人；另有系统撤销权 |

以下为**建议补齐的危险操作边界**，不冒充用户已逐项确认：

- 合并导入允许 E；覆盖导入与批量软删除仅 A/超管；批量启停/移分组可 E。与现有权限稿保持一致。
- 分组、标签、变量日常 CRUD 建议 E；团队全局响应头整体替换建议 A，因为影响全队所有接口。
- 管理端文件上传、别名/标签编辑建议 E，单个/批量物理删除建议 A；独立文件服务器的回收站不纳入本期接口回收站。
- 只读的“复制内容/导出定义”不同于“复制为新接口”；前者本身不产生服务端写入，但导出也必须限当前团队并脱敏。
- 未加入团队的普通账号只有本人账号功能；不默认创建私人团队，不读取团队目录或团队资源。
- 账号禁用优先于所有角色；未知角色一律拒绝，不能默认为可编辑。

实现建议：保留 checkTeamAccess 表示读取，复用已有 checkTeamAdmin；新增 checkTeamWrite、checkSystemAdmin，并为覆盖导入/批删/清除等危险动作集中定义能力。规则放 Service 或统一策略层，Controller、AI、MCP 复用，不各写一套。

## 4. 鉴权改造清单（按资源和动作）

| 代码入口 | 当前 | 目标与特别校验 |
| --- | --- | --- |
| [API 服务](../../../src/main/java/com/mockhub/mock/service/ApiServiceImpl.java) | 多个 CRUD/启停/批量/复制入口以 checkTeamAccess 为主 | 读取读权限；新增/编辑/启停/复制/恢复/回滚写权限；批删/彻底删管理员；统一落版本号和快照 |
| [API 文件上传端点](../../../src/main/java/com/mockhub/mock/controller/ApiController.java#L194-L208) | 上传只验团队访问 | 改写权限；上传产生的文件引用绑定团队，保存/恢复时不能引用别队文件 |
| [导入导出服务](../../../src/main/java/com/mockhub/mock/service/ImportExportService.java) | 成员校验；merge 跳过同路由，override 更新同团队同 method/path 的接口，未匹配接口不删除；且直接写 Repository | 导出读，合并写，覆盖管理员；复用领域写入留历史与并发校验，不误做“清空整队”；任何将来的删除都走软删除 |
| [分组服务](../../../src/main/java/com/mockhub/mock/service/GroupService.java) | 读成员、写管理员；删分组把接口改为未分组 | 按矩阵调整；删分组仍不删接口，同步被影响接口 revision/历史，恢复时分组不存在则到未分组 |
| [标签服务](../../../src/main/java/com/mockhub/mock/service/TagService.java) | 创建成员、更新/删除管理员 | 统一日常写能力；跨团队 tag ID 禁用；删标签不删接口 |
| [变量服务](../../../src/main/java/com/mockhub/mock/service/CustomVariableService.java) | 读成员、写管理员 | 按矩阵改日常写能力，所有变量值/分组 ID 校验父团队 |
| [全局响应头服务](../../../src/main/java/com/mockhub/mock/service/GlobalHeaderService.java) | 读和整体替换都只验访问 | 读权限/管理员替换；审计注明这是团队共享配置 |
| [文件管理服务](../../../src/main/java/com/mockhub/files/service/FileServerService.java) | 管理上传成员、编辑/删除管理员；另有公开上传 | 管理端与公开上传分开，读/写/危险删除按矩阵；不误改公开接口协议 |
| [团队服务](../../../src/main/java/com/mockhub/system/service/TeamServiceImpl.java) | 成员管理全超管 | 成员列表本队可读；本队成员/团队信息 A；平台建队超管；统一最后管理员保护 |
| [用户服务](../../../src/main/java/com/mockhub/system/service/UserServiceImpl.java) | 平台用户/全团队替换超管，末位团队管理员缺保护 | 保留平台边界；本人资料独立接口；整体分配不下放给团管 |
| [统计](../../../src/main/java/com/mockhub/stats/service/StatsServiceImpl.java)、[日志](../../../src/main/java/com/mockhub/log/service/LogServiceImpl.java) | 统计限管理员；日志按团队查 | 本队成员可读；脱敏在服务端完成；团队为空的系统日志仅超管；不能把所有日志下发后前端过滤 |
| [AI 确认落库](../../../src/main/java/com/mockhub/ai/service/PendingActionService.java#L134-L244) | 已有归属/确认状态/过期处理，仍以成员校验为主 | 保留已有保护，确认时重验当前写权限、真实资源团队、expectedRevision；不要重复造确认队列 |
| [MCP 工具](../../../src/main/java/com/mockhub/mcp/service/McpToolsService.java)、[MCP 认证](../../../src/main/java/com/mockhub/mcp/service/McpAuthService.java) | 已有 token/current user 检查，无资源 scope | 当前角色 ∩ 凭证团队/动作 ∩ 实例工具策略；“只生成草稿”也不能承诺用户有权最终写入 |

所有跨多个资源的操作应先校验**全部目标**再提交；任一目标不被授权时不出现部分成功。若以后提供分项结果，必须单独定义协议，不靠客户端猜测。

## 5. 数据结构与事务建议

### 5.1 核心增量（建议按 V8、V9 分阶段追加）

| 表 | 建议增量 | 注意事项 |
| --- | --- | --- |
| user | status 默认 ACTIVE、auth_version 默认 1；可加停用时间/操作者 | 老数据不自动禁用；缺失用户直接拒绝认证；first_login 继续使用 |
| user_team | role 接受 VIEWER / EDITOR / TEAM_ADMIN | 老 MEMBER 映射 EDITOR，保留现有协作能力；主键(user_id,team_id)不变；新成员默认 EDITOR，降权需明确操作 |
| api_definition | deleted_at、deleted_by、purge_after；revision 默认 1 | 创建人/修改人字段已有，不重复加；新时间统一 UTC/ISO 格式，旧时间的时区要做迁移解析 |
| api_revision（新） | id、api_id、team_id、revision、action、actor_id、actor_name_snapshot、created_at、source、snapshot_json、snapshot_schema_version、restored_from_revision_id | 唯一(api_id,revision)；不提供普通编辑/删除历史接口；团队隔离；快照不保存认证密码或 token |
| operation_log | 保留现有结构，可增 actor 身份模式/请求关联 ID，或放结构化 detail | 记录资源变更和安全事件；摘要日志不是版本仓库 |

枚举映射要在数据库、DTO 校验、登录返回、前端展示、AI/MCP 同一批完成。建议将 MEMBER 作为受限兼容输入临时映射 EDITOR，输出统一新枚举，并写明移除兼容的版本；未知/空角色拒绝授权。老库已有非法角色时中止迁移并给报告，不隐式提权。

### 5.2 回收站：不仅是加一列

1. 软删除在同一事务更新 deleted_at / purge_after（删除时间起 30×24 小时）、revision、历史和审计。删除后接口不再参与 Mock 命中、WSDL 提供、有效列表、统计的活跃接口数或默认导出。
2. 不立即删 api_response、api_tag 或响应文件；否则保留主表也恢复不回来。分组/标签不存在时允许恢复为未分组/剔除已失效标签，并提示。
3. 现有唯一约束是整表 team_id + path + method。需要迁移为“仅未删除接口”的部分唯一索引，并同步路径预检、导入冲突和 repository 查询。
4. 恢复必须再次检查唯一性，数据库约束是最后防线。被同 method/path 新接口占用时返回冲突，可提交 newPath；不覆盖新接口、不自动改团队。
5. 建议恢复后默认**停用**，由有写权限的人确认后启用，避免恢复动作立即改变联调流量；此为待确认细节。
6. 过期或管理员彻底删除后移除接口内容、返回体和完整快照，保留不含响应正文的审计墓碑；“不可恢复”不等于抹去谁删了什么的审计。版本不能成为绕过彻底删除的另一份正文副本。
7. 清理任务每次只处理已到 purge_after 的批次；采用有条件更新/删除与恢复竞争，操作幂等。30 天是恢复资格期限，不承诺后台任务精确到秒清理；已经到期但未物理清理的记录不能再恢复。
8. 文件物理删除在数据库提交后执行，失败可重试。建议增加受控 blob 引用记录：当前接口、回收站接口、仍可回滚的版本、尚未过期的有效草稿、上传中的暂存租约都算引用，GC 不能只查当前响应记录。草稿完成/取消/过期后原子转移或释放引用；上传租约到期且无其他引用才允许清理。独立文件服务器和响应附件不是一套存储，不混删。

### 5.3 版本与回滚：先解决并发覆盖

- 每次成功改变接口配置产生一个新 revision，包括 REST/SOAP 返回体、条件/随机模式、启停、分组/标签变更、AI 确认、导入覆盖、软删除、恢复、回滚。
- 命中次数和最近调用时间属于运行统计，不生成配置版本；否则每次请求都制造历史。
- 版本快照保存完整**可恢复配置聚合**：接口配置、所有响应体与规则、SOAP operation 配置及可恢复文件引用。以一个版本快照供前后对比，不要为每条版本再重复存两份 6MB 正文。
- 写请求带 expectedRevision；服务端用 revision 条件更新，零行更新即冲突。内容、响应体、标签、revision、历史在同一事务提交，出错整笔回滚。
- 回滚是“把历史配置写为一个新版本”，不删除历史；不回滚 team_id、创建者、权限、用户/团队状态、调用计数、日志或删除状态。
- 已删除接口要先通过回收站恢复，不能借回滚接口复活；归档团队若有冻结策略也必须遵守。
- 旧数据迁移仅创建“迁移基线”版本，不能伪造过去的修改人/修改时间；保留原 created_by，操作人注明系统迁移。基线与后续版本不恢复密钥、账号/团队权限或版本外的共享变量当前取值。
- AI 已有草稿过期/确认幂等机制应保留，但它的 claim 只防重复确认，不会检查生成草稿时的接口版本。草稿要存 baseRevision，确认写入使用同一 revision 冲突机制，不用时间字符串替代版本号。claim → API 聚合修改 → 完成状态 → 历史写入需原子提交或有明确可恢复状态机，失败不重复创建资源、不永久卡在 CONFIRMING。
- 版本不是永久备份。建议明确单接口版本数量与存储配额；首次可采用最多 50 个可回滚版本的上限建议，回收站 30 天期间的删除快照必须保留，不受普通历史裁剪影响。更大配额/历史归档另行排期。
- 日志/历史阅读时做服务端敏感字段遮蔽；若真实返回体本身含密钥，应从源数据治理，不声称一个 diff 算法能识别全部秘密。这里的“不可变”指留存窗口内追加、禁止常规代码改写已发布快照；仓储约束并测试 UPDATE/DELETE 边界，淘汰/彻底删除仅走有审计的专用清理流程，不把“不能普通编辑”写成“永久不可删除”。不承诺抗数据库管理员篡改；若需合规级防篡改，外部签名/锚定另行设计。

团队硬删除还要检查回收站接口、历史及待清理文件，不能因活跃接口计数改为不含 trash，就允许删队绕过 30 天保护。导入/导出不是数据库备份：现有包的标签映射/全局响应头恢复需专项核对，外部 file_path 不得直接引用服务器其他团队的文件；不要把本期方案扩成无边界的全量备份产品。

### 5.4 最后一位管理员：按最终状态校验，不能只锁按钮

分别定义两个计数，不混用：

- 系统有效超管：账号 ACTIVE 且 globalRole=SUPER_ADMIN。
- 某团队有效管理员：账号 ACTIVE，且该团队真实 user_team.role=TEAM_ADMIN。

一位已停用管理员不算；一个没有该团队成员关系的全局超管，虽然可维护/救援，也**不计入**该团队管理员人数。

以下入口全部经过同一事务保护：移除成员、团队角色降级、本人离队、整表团队分配、账号停用/删除、全局角色降级、建队首位管理员指派。

- 对所有受影响活跃团队检查修改后的管理员数至少 1；全系统有效超管至少 1。
- 新建团队与首位管理员指派原子完成；迁移发现历史空管理员团队时生成待修复清单，由超管指定，不随机提升普通成员。
- 采用 SQLite 可串行化的写事务/锁定顺序，先建立写事务再读最终计数；不能让两个管理员各自看到“还有另一人”后并发退出。
- 不在已启动的 Spring 事务中随意嵌套裸 BEGIN IMMEDIATE；选用并验证一致的事务配置，busy 重试需重跑整笔校验，不能带着过期计数继续提交。

## 6. 接口改造草案

表中“沿用”表示 endpoint 已存在但行为/校验要改；“新增”均为拟议合同，不表示代码已实现。沿用 Result 的 code/msg/data 包装，不照搬无关项目的协议。

| 类型 | Endpoint | 约定 |
| --- | --- | --- |
| 新增 | GET /api/auth/me | 返回当前 userId/status/globalRole/teams[teamId,identifier,membershipRole,capabilities]；普通用户仅真实成员团队，超管可维护的非成员团队 membershipRole=null，并标 accessMode=SYSTEM_MAINTENANCE，不伪造 TEAM_ADMIN；不依赖老 user 快照 |
| 新增 | PATCH /api/me/profile | 仅改本人显示名；不接收 globalRole、teamRoles、status 作为可写字段 |
| 新增 | PATCH /api/users/{id}/status | 超管；状态变更、管理员保护、auth_version、凭证撤销同事务；返回受影响团队信息 |
| 沿用 | POST /api/users/{id}/reset-password | 超管 Service 校验、初始密码规则、firstLogin、认证版本失效与审计；普通账号不可调用 |
| 沿用 | POST /api/auth/change-password | 校验本人旧密码，提交后旧认证失效，要求重新登录 |
| 沿用 | POST /api/auth/logout | 现状仅本地清 token；首期提供“退出全部会话”语义时必须明确标签；单会话退出依赖下述 sid 任务 |
| 沿用 | POST /api/teams，PUT /api/teams/{id} | 建队增 initialAdminUserId 且校验 ACTIVE，团队标识不可更改；本队元信息由团管可改，全局字段只超管可改 |
| 沿用 | GET/POST /api/teams/{id}/members；DELETE /api/teams/{id}/members/{userId}；PUT /api/teams/{id}/members/{userId}/role | 列表可读；修改本队团管可用，枚举/目标账号/最后管理员检查；不能改目标用户全局角色 |
| 新增 | GET /api/teams/{id}/member-candidates?username=... | 本队团管按完整用户名精确查找 ACTIVE 账号，最小化返回 ID/显示名/是否在队；限流，不开放全平台目录或模糊搜索 |
| 新增 | POST /api/teams/{id}/leave | 本人离队，末位管理员须先交接；不删除资源 |
| 沿用 | POST /api/users/{id}/teams | 仍仅超管整体分配；去重、校验所有团队/角色、最终管理员状态，整笔事务，不先删后失败留空 |
| 沿用 | GET /api/apis?teamId=...&createdBy=me | 创建者筛选在 SQL 层、分页/总数一致；me 服务端解析成当前 userId，不按显示名匹配 |
| 沿用 | GET /api/apis/{id} | 增 createdByName、updatedByName、updatedAt、revision、当前 capabilities；原字段保留 |
| 沿用 | PUT /api/apis/{id}；PUT /api/apis/{id}/toggle；POST /api/apis/batch | 接 expectedRevision；批量给每项版本；授权与冲突有明确全成全败语义；所有成功配置写入留版本 |
| 沿用 | DELETE /api/apis/{id} | 改软删除，需目标 revision；不存在/已删时定义幂等错误，不物理删附件 |
| 新增 | GET /api/apis/trash?teamId=... | 团队隔离分页，返回 deletedByName/deletedAt/purgeAfter/canRestore/canPurge；原详情接口不能绕过 deleted 状态 |
| 新增 | POST /api/apis/{id}/restore | body: expectedRevision、可选 newPath；冲突不覆盖、可提示分组失效 |
| 新增 | DELETE /api/apis/{id}/purge | 团队管理员/超管；仅已删除资源，二次确认由 UI 负责但后端不能依赖确认框鉴权 |
| 新增 | GET /api/apis/{id}/revisions；GET /api/apis/{id}/revisions/{revisionId} | 当前团队读能力；不按“创建人本人”放行；读取历史也必须校验资源归属 |
| 新增 | POST /api/apis/{id}/revisions/{revisionId}/rollback | expectedRevision；只回滚配置，生成新版本；目标版本/资源必须同属一个接口 |

**错误兼容：** 当前 [异常处理:9–21](../../../src/main/java/com/mockhub/common/model/GlobalExceptionHandler.java#L9-L21) 多用 HTTP 200 + 业务 code，现有 40101/40102 并不是 HTTP 状态。第一批保留旧包装，给新增能力分配稳定 machine-readable reason（ACCOUNT_DISABLED、AUTH_REVOKED、TEAM_ACCESS_REVOKED、READ_ONLY、LAST_ADMIN、REVISION_CONFLICT、RESTORE_PATH_CONFLICT），在现有错误码登记后确定数字，不能随意复用已占用 code。若升级为真实 HTTP 401/403/409，必须同步 [Axios 拦截器](../../../frontend/src/api/request.js#L24-L69)，否则会把 403/409 误报“网络错误”。

**兼容与切换：** 先把 revision 作为只读字段提供给客户端；完成 Web/AI/MCP 的写入升级后再强制要求 expectedRevision，旧写客户端应明确报升级要求，不默认为“覆盖最新版本”。若服务仅整包部署，推荐在一个维护窗口原子升级后端与前端，避免长期双协议。

## 7. MCP、个人会话与归档：列清扩展范围

### 7.1 个人 MCP 凭证（单独阶段，不与网页 JWT 混成一种 token）

- 复用现有凭证随机数、哈希、撤销/到期、当前用户查找机制。
- 拟新增 GET/POST /api/me/mcp-tokens、DELETE /api/me/mcp-tokens/{id}，只管理本人 token；实例配置仍沿用超管 /api/mcp/settings。
- 增 token-team scope 关系和动作范围（例如 api:read / api:write），每次有效能力 = 账号当前权限 ∩ 凭证 scope ∩ 实例策略；不能让超管身份把一个只读 scope 放大成全系统。
- 全局管理、成员管理、回收站彻底删除不默认作为 MCP 工具开放。token 原文只在创建时返回一次，日志不记录。
- 有效期上限、旧永久 token 处理需确认。建议升级时统一撤销无 scope 的旧凭证、由原持有人重新签发，不能自动变成全团队全动作 scope。
- 个人 scope 阶段给 ai_pending_action 补 source、originTokenId、目标团队/动作及 baseRevision，保留来源凭证标识，不能只存当前操作者。沿 MCP 授权自动确认时重查来源 token 是否已撤销、过期和 scope；Web 本人独立确认走当前 Web 登录权限并明确确认行为，不把来源 token 赋权给浏览器，也不以换入口自动确认绕过已撤销授权。
- 草稿在签发权限内生成也不能“冻结授权”；确认时要重查 scope、当前团队角色与 revision。MCP scope 不应限制用户在 Web 里按其本人当前权限的正常独立操作，但不能自动确认一个已经超出来源授权的草稿。

### 7.2 单设备会话（要落个人会话页面时必须加）

建议 auth_session(id/sid、user_id、created_at、expires_at、last_seen_at、revoked_at、简化设备说明)，JWT 带 sid。每次认证确认会话未撤销；last_seen 节流更新，避免每个 GET 都写 SQLite。本人 GET /api/me/sessions、DELETE /api/me/sessions/{id}；退出当前会话只撤销当前 sid；退出全部设备还递增 auth_version。数据库不存明文 JWT。

### 7.3 团队归档（现稿矛盾，需确认后再排）

当前稿一处写“归档不自动停 Mock”，另一处写“归档后不占 Mock 路径”。两者冲突，不能一起实施。

建议：归档冻结管理写入、保留 identifier 不复用；是否停止 Mock 是另一个明确开关/操作，默认不因归档中断联调。若用户希望归档等于停服，需明示影响范围并测试，而不是偷偷改变 URL 路由。本期先不新增归档字段/endpoint，不依赖归档解决权限问题。

公开 Mock 路径继续采用 /mock/{teamIdentifier}/**，例如存储 path=/orders/{id}、identifier=SETTLE，则访问 /mock/SETTLE/orders/123。创建团队不意味着给所有接口路径强制加 /api；后端继续以 [Mock 分发控制器](../../../src/main/java/com/mockhub/mock/controller/MockDispatchController.java#L54-L69) 为准。

## 8. 迁移、上线和回退

1. 使用脱敏测试库副本验证；正式变更前暂停管理写入，做 SQLite 一致性备份与相关文件目录备份。WAL 模式不能只复制正在写的主数据库文件而忽略 WAL；用数据库备份机制或停写后完整备份。
2. 盘点旧 MEMBER/TEAM_ADMIN、非法角色、缺失团队/用户引用、无有效管理员团队、重复 method/path、无效时间和丢失附件；出可处理报告，不猜测修复。
3. 通过现有版本化框架追加 V8 权限/账号字段，V9 回收站/版本（具体序号实施时按最新基线调整）；role 默认值、检查约束如需重建 SQLite 表，保留全部列/索引并做行数/内容校验。
4. partial unique index 迁移需要修改 [初始化索引声明](../../../src/main/resources/schema.sql#L90-L95) 的执行顺序：当前启动会先执行 schema，再迁移，再执行 schema，不能保留旧唯一索引定义让它每次启动重建回来。
5. [当前迁移失败会继续启动](../../../src/main/java/com/mockhub/common/config/DataSourceConfig.java#L216-L222)，对权限版本迁移必须改成失败停止服务；关键约束/索引建立失败同样中止，不能留下半升级鉴权。依赖新列的索引应移入对应迁移事务，再建立并验证，不能简单把迁移前的“老库缺新列”异常一律改成致命错误，导致老库永远到不了升级阶段。
6. 明确每个连接的 foreign_keys 与 busy_timeout 配置，先处理历史孤儿数据再启用外键并做 foreign_key_check；不要直接全局开启后忽略既有删除路径的兼容性。不能只给一个启动连接设置 PRAGMA 就假设连接池其余连接相同。SQLITE_BUSY 有界重试整笔事务，重新读权限/版本，不能只重试最后一次写。
7. 启动后核对 schema 版本、索引定义、角色映射、管理员计数、接口/响应/标签/文件数据；验证旧 token 被统一要求重新登录。
8. 以普通成员、只读、团队管理员、超管四组账号做回归，通过后再开放写入口。
9. 发布回退采用配套旧 jar/前端与升级前数据库、文件备份一起恢复。含删除数据/新历史的版本不建议直接“降字段”或只回退 jar；上线后新增的数据需另行导出或前向修复，回退前停写。

## 9. 设计稿必须同步统一的细节

这些是此次代码核对发现的设计冲突，列成任务；本轮不擅自重绘或冒充已修好。

| 位置 | 现象 | 推荐统一口径 |
| --- | --- | --- |
| [新建账号稿](p18-user-create.html) 与 [跨团队维护稿](p09-system-maintenance.html) | 一处说超管不自动有团队编辑权，另一处提供跨团队维护 | 保留超管显式维护身份与审计，不强制伪造成员关系；最终由产品确认 |
| [回收站稿](p15-recycle-bin.html) | 场景是可编辑成员却展示可操作的“彻底删除”；文字又说仅管理员 | E 仅恢复；A/超管才显示彻底删除；按服务端 capabilities 驱动 |
| [权限说明](README.md#L16-L20) 与 [回收站稿](p15-recycle-bin.html) | 把“离队”写成“创建人变空” | 离队保留创建人；账号硬删除另作匿名化/快照处理 |
| [只读列表稿](p02-workspace-viewer.html) | 因“只读不能创建”而隐藏“我创建的” | 只读成员可能是以前的编辑者，有历史创建资源；该筛选是读取，可以保留；无结果用空状态 |
| [变更记录稿](p16-api-history.html) | USER 团队标题与示例正文混入支付字段，缺少真实 revision 冲突态 | 示例与实际 team/API 一致；补冲突提示，不假装回滚永远成功 |
| [个人账号稿](p04-account.html) | 设备会话看起来可撤销，但没有登录会话后端 | 未做 sid 阶段前明确不可用或隐藏，不能只清本地 token 冒充服务端撤销 |
| [团队创建稿](p17-team-create.html) 与 [权限说明](README.md#L79) | 归档后的 Mock 行为不一致 | 归档/停服拆开确认，暂缓实现 |

另外：静态设计稿的 1440×900 等比缩放只是看稿工具。正式 Vue 页面要做真实布局/合理滚动和窗口适配，**不能把整站 scale 缩放、隐藏溢出当生产响应式实现**。

## 10. 实施顺序与工作量（粗估，不是承诺交期）

假设：熟悉现有 Spring Boot / SQLite / Vue 的开发者；保留当前离线单 jar 形态，不新增 Redis/SSO；已有组件可复用。人天是总投入，不等于并行后的自然天，正式排期需拆任务和小规模验证。

| 阶段 | 内容 | 依赖 | 粗估投入 |
| --- | --- | --- | --- |
| P0 | 密码重置缺鉴权、JWT 旧权限路径、必要回归测试 | 无；建议先独立安全修复 | 1–2 人天 |
| P1 | 角色迁移、账号停用、当前身份/能力、实时鉴权、管理员保护 | 三个推荐决策与危险动作边界确认 | 4–6 人天 |
| P2 | 团队成员自治接口、个人资料、前端团队上下文/路由/错误处理、稿子语义统一 | P1 | 3–5 人天 |
| P3 | 聚合写事务、CAS/revision、基线快照、blob 引用/GC、AI 确认原子性、现有 MCP 写路径统一 | P1；回收站/历史的共同前置 | 4–6 人天 |
| P4 | 回收站/恢复冲突/清理、历史读取/回滚及 UI 接入，Mock/导出/统计联动 | P3 完成，不先用无 CAS 的旧写入口上线 | 3–5 人天 |
| P5 | 旧库升级/回退演练、权限与并发/REST/SOAP/文件回归 | 上述阶段 | 3–5 人天 |
| 可选扩展 | 个人 MCP scope、单设备会话、对应 UI 接入 | P1，凭证策略另确认 | 合计另加 4–7 人天 |

核心范围合计约 **18–29 人天**；MCP/单设备扩展后约 **22–36 人天**。估算已包含聚合事务、CAS/文件引用基础及现有导入写入口接入鉴权/版本；不含导入导出格式的全量纠错或补全备份语义，发现影响本期的数据完整性缺陷时先修阻断问题并重新估算，不用粗估挤掉基础工作。不含 22 个基础页面全量视觉重构、团队归档、SSO、公开文件上传策略调整。基础 UI 样式与组件可以在 API 合同冻结后并行，但不建议先把 22 页全部接上旧鉴权，再整体重接一次。

### 验收清单（必须是业务测试，不是截图检查）

- [ ] 普通账号和团队管理员调用他人密码重置均被拒绝；超管允许；旧凭证按策略失效。
- [ ] 用旧 JWT 被移出/降级/停用/全局降级后，下一请求（含不传 teamId 的列表、导出、统计）不保留旧权限。
- [ ] 核心阶段：V 不能通过直接调用管理 API、管理上传、AI 确认、批量端点写入；E 能修改队友接口但不能管理成员/平台账号。现有 MCP 仍限超管签发，也必须遵守当前账号状态及领域写入校验；不冒充已提供 V/E 个人 token。
- [ ] 个人 MCP 扩展阶段：V/E 的个人 token 按当前权限与 token scope 交集校验，撤销/到期/退队后草稿自动确认不得继续执行。
- [ ] 同一个人在 A 队管理员、B 队只读；不能利用 A 队权限、伪造 teamId 或 B 队资源 ID 写 B 队。
- [ ] 本队管理员不能读取全用户目录、改全局角色或整体覆盖其他团队成员关系。
- [ ] 最后一个有效团队管理员/超管不能被移除、降级、停用；两人同时离队、整表分配也不绕过保护。
- [ ] 离队/停用不删除接口、不清空创建人；本人创建筛选按 userId 且分页总数准确。
- [ ] 软删除停止 Mock/WSDL 与正常导出，不删恢复所需响应体/文件；恢复遇路径占用返回冲突；过期后不可恢复。
- [ ] 恢复/清理竞争只成功一方；清理重复运行安全，文件清理失败可重试，不误删历史仍引用的文件。
- [ ] 多人同时保存、回滚、AI 确认时 revision 冲突明确，不后写覆盖前写；写历史失败则配置回滚。
- [ ] 回滚覆盖 REST/SOAP/条件/随机/文件，保留权限、归属和调用统计；不能通过历史复活彻底删除的正文。
- [ ] 旧 MEMBER 数据完整映射为 EDITOR，历史管理员关系不丢；升级中断可恢复，失败不带旧/半成品权限启动。
- [ ] 前端 401/403/409（或兼容业务码）不再统一报网络错误；降权可保留允许的本地草稿，撤权清理团队缓存。
- [ ] Mock 公开调用与管理权限分离验证，不因某位创建者停用中断团队联调；公开上传限制另有明确范围。

可复用现有测试基线：[数据库迁移测试](../../../src/test/java/com/mockhub/common/config/SchemaVersionMigrationTest.java)、[API Repository 测试](../../../src/test/java/com/mockhub/mock/repository/ApiRepositoryTest.java)、[统计测试](../../../src/test/java/com/mockhub/stats/service/StatsServiceImplTest.java)、[AI 更新测试](../../../src/test/java/com/mockhub/ai/service/AiChatServiceUpdateTest.java)、[MCP Token 测试](../../../src/test/java/com/mockhub/mcp/service/McpTokenServiceTest.java)、[文件清理测试](../../../src/test/java/com/mockhub/mock/service/MockFileStorageServiceOrphanTest.java)。这些已有测试不等于以上新规则已被覆盖；本轮未运行或修改业务测试。

## 11. 本轮结论与下一步

设计足以支撑需求讨论，但不能直接宣称所有权限细节完全一致或后端已就绪。本方案把已确认规则、现状证据、建议、待确认项分开。

**建议 P0 安全修复作为独立任务优先处理，不应等待三个产品决策；本轮尚未获得业务代码修改指令。D1/D2/D3 确认后做 P1 权限基础与能力合同；随后接团队 UI，先完成事务/CAS/文件引用基础，再上线回收站/历史。** 不再用静态按钮的显示/隐藏代替服务端授权，也不边做页面边临时发明权限规则。
