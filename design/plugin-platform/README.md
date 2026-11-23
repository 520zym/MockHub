# MockHub 插件平台设计（草案）

> 状态：设计草案，未实现。  
> 日期：2026-11-22  
> 范围：平台级插件 SPI、可选文件存储能力、管理端与发布边界。  
> 说明：本文不包含任何客户名称、域名、接口路径、签名规则、测试数据、凭证或客户截图；客户相关实现只存在于私有仓库和部署配置中。

## 1. 背景

MockHub 目前适合用普通 Mock 接口覆盖的场景：静态响应、条件匹配、多返回体、动态变量、单文件下载。但外部系统里还存在一类无法用普通 Mock 接口表达的服务：

- 请求/响应需要签名、加密、时间戳或防重放；
- 多步流程，需要跨请求保存状态；
- 文件上传、下载、分片、断点续传；
- 协议握手、认证交换、异步回调、主动推送；
- 非 HTTP 协议（TCP、WebSocket、gRPC、消息队列、文件传输等）。

这类需求不能写进核心仓库：客户实现、客户契约、客户数据都不应该进入公开代码库。因此需要一个平台级插件机制：核心提供通用扩展点，客户协议实现放在独立私有仓库，以外部 jar 部署。

## 2. 目标与非目标

### 目标

1. 插件可以拦截 HTTP Mock 请求并返回自定义响应。
2. 插件可以选择使用 MockHub 的统一文件存储，文件出现在现有文件服务器页面，可监控、可管理。
3. 插件与团队绑定，未绑定团队不生效。
4. 插件代码、配置、密钥、测试数据不进入核心仓库。
5. 插件失败不拖垮主程序；插件升级/停用可运维。
6. 文件管理与请求处理是两个可独立使用的能力，不互相绑定。

### 非目标（第一版）

- 不接管非 HTTP 协议；非 HTTP 需求走外置适配器。
- 不做 Web 上传/在线安装插件。
- 不做插件热加载、热更新。
- 不做插件专属管理页面。
- 不允许核心仓库出现客户名称、域名、接口路径和客户数据。
- 不在插件内提供 Spring 容器、数据库连接或跨团队访问能力。

## 3. 术语

| 术语 | 含义 |
| --- | --- |
| 核心（Core） | MockHub 主程序与插件加载器 |
| Plugin API | 独立发布、零第三方依赖的插件接口工件 |
| 插件（Plugin） | 私有仓库构建的外部 jar，实现 Plugin API |
| 插件绑定 | 插件与一个或多个团队的启用关系 |
| 外部适配器 | 非 HTTP/非进程内场景下的独立进程服务 |

## 4. 总体架构

```text
                    ┌────────────────────────────┐
                    │        MockHub Core        │
                    │  ┌──────────────────────┐  │
  HTTP /mock/** ───▶│  │ PluginManager        │  │
                    │  │  ├─ team binding      │  │
                    │  │  ├─ executor/timeout  │  │
                    │  │  └─ error isolation   │  │
                    │  └──────────┬───────────┘  │
                    │             │ Plugin API   │
                    │  ┌──────────▼───────────┐  │
                    │  │ FileBlobStore        │  │
                    │  │ PluginFileStore      │  │
                    │  └──────────────────────┘  │
                    └────────────┬───────────────┘
                                 │ 加载
                 data/plugins/lib/*.jar（外部私有构建）
                                 │
                    ┌────────────▼───────────────┐
                    │ 私有插件仓库（不公开）       │
                    │ 客户协议实现 / 配置 / 测试   │
                    └────────────────────────────┘
```

核心仓库只保留通用接口、加载器、文件存储适配和管理端；客户协议实现放在私有仓库，构建后放入部署目录。

## 5. 模块与发布

### 5.1 独立 API 工件

必须拆出可发布的 mockhub-plugin-api：

- 只包含 com.mockhub.plugin.api 包；
- 零第三方依赖：不依赖 Spring、Jackson、SLF4J、CXF；
- Java 8 字节码；
- 版本独立于产品 CalVer，例如 1.0.0；
- 通过私有 Maven 或 GitHub Packages 发布。

原因：当前产物是 Spring Boot fat jar，类位于 BOOT-INF/classes，不能作为外部插件的编译依赖；不拆工件，私有插件仓库无法构建。

### 5.2 插件仓库

私有插件仓库只依赖 mockhub-plugin-api，构建 shaded jar，规则：

- 不得打包 com.mockhub.plugin.api 包；
- 不得打包 Spring、Jackson、SLF4J、CXF、日志实现等核心已有依赖；
- 第三方依赖必须 shade 并 relocate 到插件私有包名；
- 产物放入 data/plugins/lib/；
- 运行时通过 META-INF/services/com.mockhub.plugin.api.MockHubPlugin 声明实现。

## 6. 插件生命周期与加载

### 6.1 目录

```text
data/
  plugins/
    lib/
      <plugin>.jar
    data/
      <plugin-id>/
        config.json
        storage/...
    SHA256SUMS
```

- lib/ 只放 jar；data/<id>/ 是插件私有运行数据。
- 目录、配置、密钥都不进入核心仓库。

### 6.2 状态机

```text
DISCOVERED → LOADED → INITIALIZED → ACTIVE
                    ├→ FAILED
                    ├→ INCOMPATIBLE
                    └→ DISABLED
```

- 启动时扫描；单个插件失败只标记状态，不阻止主程序启动。
- 重复 id、apiVersion 不匹配、jar 校验失败、初始化异常 → 对应失败状态。
- 插件停用后其历史文件保留，管理端标记来源不可用。
- 第一版停用/升级需要重启；后续可加运行期开关。

### 6.3 类加载与 jar 校验

- 每个插件独立 URLClassLoader；
- 父加载器优先：java.*、javax.*、com.mockhub.plugin.api.*；
- 插件私有包 child-first；
- 加载前校验：
  - 仅接受普通 .jar 文件，拒绝软链与超大文件；
  - class major version 必须为 Java 8（52）；
  - 拒绝包含核心包/Spring/Jackson/SLF4J/CXF 等 denylist 的 jar；
  - 计算 SHA-256，与 SHA256SUMS 对照；默认启用，失败即拒绝加载；
  - 重复 plugin id 全部拒绝并给出明确错误。
- 进程内插件不是安全沙箱：只能部署经过审查的可信插件；真正的隔离只有外置进程方案。

## 7. Plugin API v1

### 7.1 入口接口

```java
public interface MockHubPlugin {
    PluginDescriptor descriptor();

    default void initialize(PluginContext context) throws Exception {}

    boolean supports(PluginRequest request);

    PluginResponse handle(PluginRequest request, PluginContext context) throws Exception;

    default void destroy() {}
}
```

### 7.2 PluginDescriptor

```java
public final class PluginDescriptor {
    private String id;          // 通用 id，小写字母/数字/连字符，永久不变
    private String title;       // 通用显示名
    private String version;     // 插件自己的版本，可用 CalVer
    private int apiVersion;     // 当前为 1
    private long timeoutMs;     // 核心会 clamp 到上限
    private List<String> pathPrefixes; // 可选预过滤，不是路由真相
    private List<String> capabilities; // REQUEST / FILE_STORE
}
```

### 7.3 PluginRequest

```java
String teamId();
String teamIdentifier();
String method();
String path();
String queryString();
String remoteAddress();
Map<String, String> headers();   // 大小写不敏感，只读
InputStream openBody();          // 由核心负责关闭；文件上传使用
long declaredLength();
String bodyText();               // 仅小请求体，按需解码
String requestId();
Instant receivedAt();
```

- 请求体不能静默截断；超过上限直接 413。
- 大请求体落临时文件，插件通过 openBody() 读取。
- 不向插件暴露 HttpServletRequest、Spring 容器、数据库连接。

### 7.4 PluginResponse

```java
PluginResponse.status(200)
    .header("X-Custom", "value")  // 走核心白名单
    .contentType("text/xml; charset=UTF-8")
    .body(bytes)                  // 小响应
    .build();

// 或流式响应（文件下载）
PluginResponse.status(200)
    .contentType(...)
    .stream(contentLength, out -> { ... })
    .build();
```

- 响应头白名单：禁止 Content-Length、Transfer-Encoding、Connection、Set-Cookie 等由核心控制的头；
- 头名/头值校验，拒绝 CRLF；
- 响应体大小、流式总时长有上限；
- 流式响应必须定义关闭责任、客户端断开处理、异常后不可再改状态码；
- 流式写出失败记录 requestId 与中止原因。

### 7.5 PluginContext

```java
String pluginId();
Path dataDirectory();          // data/plugins/data/{id}
String configJson();           // 读取 config.json 的字符串，不绑定 Jackson
PluginLogger logger();          // API 内置日志接口
PluginFileStore files(String teamId); // 仅当 request.teamId() 匹配
PluginStateStore state();      // 第一版可只提供目录，后续再抽象 key-value
```

- API 不暴露 Jackson JsonNode、SLF4J、Spring Bean、DataSource；
- 插件可自行解析 configJson()，但配置中的密钥由部署侧管理；
- files(teamId) 只接受当前请求团队，其他值拒绝。

## 8. 请求处理链路

在 Mock 分发入口调整：

1. 读取/缓存请求体（小 body 内存，大 body 临时文件）；
2. 解析团队标识；
3. 判断插件绑定团队；
4. 插件按 pathPrefixes 预过滤，再调用 supports（带短超时）；
5. 命中则 handle；未命中继续原有 Mock 匹配；
6. 原有接口匹配、SOAP、文件返回、动态变量、日志保持不变。

约束：

- 插件拦截点在 WSDL 分支之前，绑定团队下插件优先；未命中再走核心 WSDL。
- 插件绑定团队下，插件异常默认 fail-closed：返回 502/503/504，不静默回落到旧 Mock。
- 插件未绑定团队不参与任何请求。
- 归档团队不允许插件写入文件，按只读或 409 处理。
- 插件请求使用独立 requestId，贯穿日志、响应头与错误体。

## 9. 团队绑定与路由

- 绑定配置在 data/plugins/data/<id>/config.json：

```json
{
  "teams": ["TEAM_CODE"],
  "enabled": true,
  "allowPublicDownload": false,
  "maxFileBytes": 104857600
}
```

- teams 为空 = 不启用；
- 插件 id 永久不变；改名视为新插件；
- 多个插件声明同一路径前缀时启动告警，按更长前缀优先；无法确定时拒绝启用；
- 绑定团队不存在/已归档时启动告警；
- 插件不可用时，已绑定路径返回明确 503，不回落旧 Mock。

## 10. 文件管理设计（FILE_STORE 能力）

### 10.1 统一存储

插件文件复用 MockHub 文件服务器底层存储，不另建目录：

- 复用 data/file-server 文件内容存储；
- 复用现有软删除、物理删除重试、下载统计机制；
- 现有用户上传文件与插件文件在同一张表、同一页面展示，通过来源区分。

### 10.2 数据库变更

file_server_file 增加：

```sql
source_type    TEXT NOT NULL DEFAULT 'USER'   -- USER / PLUGIN
source_id      TEXT                           -- 插件 id；用户上传为 NULL
source_title   TEXT                           -- 插件显示名快照
metadata_json  TEXT                           -- 受控键值 JSON，<= 8 个 key
plugin_read_count INTEGER NOT NULL DEFAULT 0
plugin_read_bytes INTEGER NOT NULL DEFAULT 0
```

新增 plugin_registry：

```sql
plugin_id, title, version, api_version, status,
last_loaded_at, last_error, updated_at
```

新增 request_log.plugin_id，target_type 使用 PLUGIN。

迁移要求：

- 新库通过 schema.sql 建列；老库通过 schema v13 迁移；
- 旧数据 source_type=USER；
- 增加索引：(team_id, source_type, source_id, uploaded_at)、request_log(plugin_id)；
- 迁移测试覆盖 v12 → v13、旧文件行、插件文件行；
- 升级后不支持回退旧版本（破坏性列变更）。

### 10.3 PluginFileStore

```java
public interface PluginFileStore {
    PluginStoredFile put(PluginFilePut put);
    PluginStoredFile stat(String fileId);
    InputStream open(String fileId);
    PluginFilePage list(int page, int size);
    void delete(String fileId);
}
```

- 实例由 PluginContext 创建并绑定 (teamId, pluginId)；
- 所有方法强制过滤 team_id + source_type=PLUGIN + source_id=pluginId；
- stat/open/delete 越权统一返回 404，不提供存在性探测；
- put 支持 clientRef 幂等键，唯一索引 (team_id, source_id, client_ref)；
- put 使用流式写入，临时文件 → 原子 move → 数据库 insert，失败清理；
- Content-Type 由服务端按扩展名白名单决定，插件声明值只作为元数据参考；
- 文件名清洗复用现有文件服务器规则；
- open() 记录插件读取次数/字节；
- 不支持跨团队、跨插件访问；
- 不复用带 SecurityContext 的 FileServerService 公开方法，底层抽出无安全上下文的 FileBlobStore。

### 10.4 文件服务器页面调整

- 顶部来源分段：全部 / 本地上传 / 插件，插件下拉包含已停用插件；
- 来源列显示「插件: 标题」，tooltip 显示 plugin id；
- 文件详情增加：来源、插件状态、metadata 键值表、HTTP 下载次数、插件读取次数/字节；
- 权限矩阵：
  - 团队成员：查看、下载；
  - 团队管理员：删除、批量删除、清空某插件全部文件；
  - 超级管理员：跨团队清理；
  - 插件文件的别名/标签第一版只读；
- 删除：二次确认、按来源分组提示、操作日志；第一版无回收站；
- v1 不允许在 UI 上传到插件来源；
- 插件停用/卸载后文件保留，来源显示「插件不可用」；
- 提供「清理该插件全部文件」入口（显示数量与占用，二次确认）。

### 10.5 下载权限

- 默认：插件文件走团队鉴权下载，UI 不提供公开链接复制；
- 插件配置 allowPublicDownload=true 时允许匿名 UUID 链接，并在详情页明确提示风险；
- 如外部系统需要长期无登录下载，应使用带过期时间的签名链接（后续能力），不能默认公开；
- 现有 /files/{uuid} 对 USER 文件的行为保持不变，对 PLUGIN 文件按上述策略区分。

### 10.6 日志与统计

- 插件请求日志默认只记：plugin_id / team / method / path / status / duration / requestId / bodyBytes；
- 默认不落 headers、query、body，避免客户协议正文/签名进入核心库；
- 插件日志独立保留策略，不计入普通 API 调用统计；统计页单独展示插件流量；
- 文件 UI 的删除/清空记操作日志，actor 类型为 PLUGIN 来源；
- 插件自动写入文件不逐条记操作日志，避免冲掉人工审计记录；
- /api/plugins 提供每插件请求数、错误数、超时数、文件数、占用空间、最近错误。

### 10.7 配额与资源

- 每插件/每团队可配置文件数、总字节上限；
- 写入前检查，超限返回明确错误；
- /api/plugins 展示占用并支持阈值告警；
- 磁盘剩余空间纳入健康检查。

## 11. 非 HTTP 服务

第一版插件只覆盖 HTTP(S) 请求。以下场景不在插件内实现：

- TCP 自定义协议；
- WebSocket / gRPC / 消息队列 / 文件传输协议；
- 需要长期运行的后台进程、主动推送、定时任务。

这些场景使用独立外置适配器进程：私有仓库、独立部署、独立生命周期；MockHub 插件机制不为此扩张。如果未来需要，单独设计传输层扩展，不与 HTTP 插件混在一起。

MQ 作为第一个外部适配器场景，单独见 [MQ Mock Worker 设计（自部署）](../external-adapters/mq-worker.md)：使用真实 Broker + 自部署 Worker，MockHub 只做可选控制面，核心不引入 MQ 客户端。

## 12. 管理端与可观测性

### 12.1 接口

GET /api/plugins（仅超级管理员）：

- id、title、version、apiVersion、status、错误原因；
- 绑定团队数、文件数、占用空间、最近写入时间；
- 请求数、错误数、超时数、最近错误；
- 不返回 jar 绝对路径、配置内容、堆栈。

启动日志打印：loaded/failed/incompatible、版本、hash、绑定团队数。

### 12.2 页面

- 设置页第一版只读展示插件状态与入口；
- 不提供在线安装、配置编辑、上传 jar；
- 文件服务器页面提供来源筛选与「插件」入口；
- 日志页暂不新增复杂筛选，先通过 /api/plugins 查看错误。

## 13. 安全与信任边界

- 进程内插件与核心同信任等级，没有 Java 沙箱；只能部署可信插件；
- 插件不获得 DataSource、Spring Context、跨团队文件访问；
- 团队绑定是强制隔离手段，不是可选项；
- 文件访问三重过滤；越权返回 404；
- 响应头白名单、CRLF 校验、响应大小/时长上限；
- 每插件独立有界线程池、并发信号量、超时 clamp、拒绝 503；
- supports 独立短超时，异常视为不匹配；
- 插件 jar SHA-256 默认启用 allowlist；
- 插件配置/密钥不写入核心仓库，日志不落客户协议正文；
- 非 HTTP、不可信插件 → 外置适配器。

## 14. 版本与兼容

- Plugin API 使用整数 apiVersion（当前 1），核心维护支持列表；
- API 工件版本独立（例如 1.x），插件依赖固定大版本；
- 核心升级时保留 API v1 兼容窗口，破坏性变更提升 apiVersion；
- 插件自身版本可用 CalVer；
- 插件 id 永久不变，文件 source_id 依赖它；
- 数据库迁移只升不降，发布说明必须写明。

## 15. 交叉评审结论

本设计经过三轮独立评审（架构、安全与运维、产品与文件管理），结论一致：

需修改后实施，不建议直接按初稿编码。

已并入本稿的 P0 结论：

| 评审发现 | 本稿调整 |
| --- | --- |
| fat jar 不能作为插件编译依赖、没有 API 工件 | 拆出零依赖 mockhub-plugin-api 并发布 |
| 插件全局生效，客户插件可读其他团队流量 | 强制团队绑定，未绑定不启用 |
| 复用带登录权限的文件服务会导致插件链路抛未登录 | 抽出无安全上下文的 FileBlobStore |
| 插件文件经匿名公开链接下载，团队权限不成立 | 默认团队鉴权下载，公开下载需显式配置 |
| 超时/线程池缺失，插件故障会拖垮核心 | 每插件有界执行器、并发信号量、超时 clamp、fail-closed |
| 请求体截断、内存放大，文件上传不可用 | 小 body 内存、大 body 临时文件、超限 413 |
| 类加载/jar 信任规则缺失 | denylist、Java 8 校验、SHA-256、重复 id 拒绝 |
| ?wsdl 被 Controller 提前截走 | 插件拦截前移到 WSDL 分支之前 |
| 文件来源筛选看不到已停用插件 | 增加 plugin_registry 与 source_title 快照 |
| 插件日志会冲掉审计、污染统计 | 只记元数据、独立保留、统计口径分离 |
| 迁移细节不完整 | 明确 schema v13、索引、迁移与测试 |
| 前端改动被低估 | 按接口/页面契约列出范围 |

已删除或推迟的过度设计：

- 通用 PluginResourceAdmin 和「扩展资源」页面：第一版不做；
- 插件专属管理 UI、在线配置编辑：第一版不做；
- 非 HTTP 传输层：第一版不做，走外置适配器；
- 插件热加载：第一版不做。

## 16. 待确认决策

1. 信任边界：客户插件代码是否都经过审查、接受与核心同进程运行？不接受则改用外置适配器。
2. 团队绑定：是否强制显式绑定团队（推荐强制）。
3. 下载权限：插件文件默认团队鉴权下载；是否允许按插件配置公开 UUID 链接？
4. 大文件：是否存在单文件超过 10MB 的场景？如有，文件插件必须走流式/临时文件方案。
5. UI 上传：是否确认第一版不允许从 UI 上传到插件来源？
6. API 工件发布：是否同意把 mockhub-plugin-api 发布到私有 Maven 或 GitHub Packages？
7. 删除可逆性：插件文件删除是否保持二次确认 + 操作日志、删除即不可恢复，还是需要回收站/宽限期？

## 17. 实施阶段（尚未开始）

1. Phase 0：独立 mockhub-plugin-api 工件、PluginManager 骨架、类加载/jar 校验、团队绑定、执行器与错误语义、契约测试。
2. Phase 1：dispatch 插件拦截（含 WSDL 前）、请求体流式缓存、管理接口、日志与统计口径。
3. Phase 2：FileBlobStore 抽取、schema v13、PluginFileStore、文件服务器来源筛选/详情/权限/清理、配额。
4. Phase 3：私有仓库实现第一个外部服务插件，验证 HTTP 请求、文件写入、页面管理、停用/清理全链路。
5. Phase 4：按需增加流式优化、运行期禁用/回滚、插件指标面板、非 HTTP 外置适配器规范。

## 18. 验收与测试策略

- API 工件合约快照测试：接口方法/签名变化必须显式更新；
- 坏 jar、缺 descriptor、apiVersion 不匹配、初始化异常 → 插件失败但主程序启动正常；
- 重复 id、denylist 包、非 Java 8 字节码、软链 jar → 拒绝加载；
- 团队绑定：未绑定团队不命中；绑定团队命中；归档团队拒绝写入；
- 超时、异常、supports 异常、线程池拒绝 → 明确 502/503/504；
- 请求体：小 body、大 body、超限 413、中文/无 charset；
- 文件：跨团队/跨插件越权 404、幂等 put、删除后 404、下载权限、统计口径；
- 迁移：v12 旧库升级、新库建表、索引存在；
- 前端：来源筛选、详情、删除确认、移动端；
- 仓库边界：CI 关键词扫描，核心仓库不出现客户名称/域名/接口路径/凭证。

## 19. 仓库边界与脱敏规则

核心仓库中禁止出现：

- 客户名称、简称、产品名、项目代号；
- 客户域名、内网地址、端口、接口路径；
- 客户签名规则、字段值、测试账号、密钥；
- 客户截图、日志片段、真实响应报文。

允许出现：

- 通用术语：插件、外部服务、文件服务、适配器；
- 示例插件（echo 等）与通用占位配置；
- 不指向具体客户的协议描述。

客户插件实现、配置、测试数据、部署脚本全部放在私有仓库或部署环境；核心仓库只保留本设计文档与通用结构。

## 20. 附录：通用配置示例

```json
{
  "teams": ["TEAM_CODE"],
  "enabled": true,
  "allowPublicDownload": false,
  "maxFileBytes": 104857600
}
```

配置中的 TEAM_CODE 是占位符，不代表任何真实团队。
