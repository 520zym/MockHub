# MQ Mock Worker 设计草案（自部署外部适配器）

> 状态：设计草案，未实现。  
> 日期：2026-11-22  
> 关联：MockHub 插件平台设计（design/plugin-platform/README.md）  
> 说明：本文不包含任何客户名称、域名、IP、账号、队列地址、消息内容或客户截图；示例中的标识均为占位符。

## 1. 背景

MockHub 当前的核心能力是 HTTP Mock：静态响应、条件匹配、多返回体、动态变量、文件下载。消息队列（MQ）属于非 HTTP 协议，无法用普通 Mock 接口表达，也不适合塞进进程内 HTTP 插件。

当前对 MQ 的具体要求尚未确定：

- 不知道具体 MQ 类型（RabbitMQ / Kafka / MQTT / IBM MQ / RocketMQ 等）；
- 不知道主要场景是请求-响应还是发布-订阅；
- 不知道 Broker 由谁提供、部署在哪里；
- 明确的一点是：谁使用谁部署，数据和凭据留在使用方环境。

因此本文不绑定具体 Broker，也不假设单一使用模式，而是设计一个**自包含、可自部署、协议可插拔的 MQ Mock Worker**。它不依赖 MockHub 也能独立运行；后续可选择接入 MockHub 做统一监控。

## 2. 核心取舍

### 方案对比

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| 在 MockHub 核心内实现 MQ 协议 | 核心引入 AMQP/Kafka 客户端与 Broker 语义 | 不采用：依赖重、协议复杂、客户数据进核心 |
| 作为进程内 HTTP 插件 | 插件挂在 /mock/** 下 | 不适用：MQ 不是 HTTP 请求 |
| 独立 MQ Mock Worker（本方案） | 独立进程连接真实 Broker，按规则消费/发布 | 采用 |

### 关键原则

1. MQ 协议由 Worker 负责，MockHub 核心不引入任何 MQ 客户端依赖。
2. Worker 自包含、可自部署；Broker 凭据和消息数据留在使用方环境。
3. 默认不依赖 MockHub；需要统一监控时再可选接入。
4. 不模拟 Broker 协议本身；连接真实 Broker，模拟的是业务消息行为。
5. Worker 代码保持通用，客户规则/消息不进核心仓库。

## 3. 总体架构

```text
使用方自己的环境
┌────────────────────────────────────┐
│ MQ Mock Worker（独立进程 / 容器）   │
│                                    │
│  ┌─────────────┐  ┌─────────────┐  │
│  │ BrokerDriver│  │ Scenario    │  │
│  │ (AMQP/Kafka │  │ Engine      │  │
│  │  /MQTT/...) │  │             │  │
│  └──────┬──────┘  └──────┬──────┘  │
│         │                │          │
│  ┌──────▼────────────────▼──────┐   │
│  │ Local Store（SQLite）        │   │
│  │ 消息 / 场景 / 事件 / 配置    │   │
│  └──────────────┬───────────────┘   │
│                 │                   │
│  ┌──────────────▼───────────────┐   │
│  │ Local Admin API / Web UI     │   │
│  └──────────────────────────────┘   │
└─────────────────┬──────────────────┘
                  │ 真实 MQ 协议
          ┌───────▼────────┐
          │ 真实 Broker     │
          │ （使用方提供）  │
          └────────────────┘

可选：
MQ Mock Worker ──HTTPS/Token──▶ MockHub（注册 / 心跳 / 上报事件 / 拉规则）
```

## 4. 部署模型

- 交付形态：单 jar + 配置文件，或 Docker 镜像；使用方自行部署到 Broker 同网络。
- 配置来源：本地配置文件 + 环境变量；Broker 账号/密码/证书只通过环境变量或本地密钥文件提供。
- 默认监听：本地管理端口，默认只绑定 127.0.0.1；需要远程管理时显式配置监听地址与访问令牌。
- 数据目录：本地 SQLite + 可选附件目录，使用方自行备份。
- 升级/回滚：Worker 独立升级，不影响 MockHub；版本号可单独使用 CalVer。
- 不需要中心服务器即可运行；MockHub 集成是可选增强。

## 5. 运行模式

Worker 支持三种模式，可在配置中选择：

1. REQUEST_REPLY：消费请求消息，按规则发布响应。
2. PUBLISH：主动向队列/主题发布模拟消息（手动/定时/接口触发）。
3. BOTH：同时支持以上两种。

### REQUEST_REPLY

- 订阅请求队列/主题；
- 收到消息后执行匹配；
- 命中场景后按配置发布响应；
- 支持从原消息取 replyTo、correlationId、messageId；
- 支持延迟、错误、丢弃、重复、NACK、超时等行为；
- 未命中时可配置：忽略、记录、返回默认响应、或拒绝。

### PUBLISH

- 管理端或定时任务触发；
- 向指定队列/主题发布一条或多条消息；
- 支持模板变量、批量发送、间隔、重复次数；
- 适合模拟服务端主动通知、事件推送、消费者联调。

## 6. BrokerDriver SPI

Worker 内部按 Broker 实现驱动，核心只依赖统一接口：

```java
public interface BrokerDriver extends AutoCloseable {
    String kind();
    void connect(BrokerConfig config) throws Exception;
    void subscribe(Subscription subscription, MessageHandler handler);
    void publish(OutboundMessage message) throws Exception;
    List<BrokerResource> resources() throws Exception;
    void healthCheck() throws Exception;
    void close();
}
```

### 统一消息模型

```java
public final class InboundMessage {
    String resource;          // 队列/主题名称（相对名称，不是客户地址）
    String messageId;
    String correlationId;
    String replyTo;
    Map<String, String> headers;
    Map<String, String> properties;
    byte[] body;
    Instant receivedAt;
}
```

- body 按原始字节处理；文本解析由场景引擎决定；
- Worker 不记录 Broker 的连接串、账号、证书到事件表；
- 驱动实现可以支持 AMQP、Kafka、MQTT、IBM MQ 等，但第一版只做接口和测试用内存驱动。

### 驱动生命周期

- 启动时加载配置并 connect；
- 断线自动重连，指数退避，记录连接状态；
- 订阅失败/权限不足时进入 DEGRADED 状态并暴露错误；
- close() 必须释放消费者、连接、线程池；
- 驱动内部线程池必须有界，禁止无界队列。

## 7. 场景与规则模型

### 场景字段

```json
{
  "id": "scenario-001",
  "name": "成功响应",
  "enabled": true,
  "mode": "REQUEST_REPLY",
  "resource": "mock.request.queue",
  "match": {
    "headers": { "messageType": "ORDER_QUERY" },
    "properties": {},
    "body": {
      "jsonPath": "$.orderId",
      "exists": true
    }
  },
  "behavior": "NORMAL",
  "response": {
    "resource": "mock.reply.queue",
    "useReplyTo": true,
    "correlationIdFromMessage": true,
    "contentType": "application/json",
    "bodyTemplate": "{"code":0,"message":"ok"}"
  },
  "delayMs": 0,
  "priority": 0
}
```

### 行为枚举

| 行为 | 说明 |
| --- | --- |
| NORMAL | 正常返回响应 |
| ERROR | 返回错误响应 |
| DROP | 不返回，模拟消费失败 |
| DUPLICATE | 重复投递 |
| NACK | 拒绝消息，可配置是否重新入队 |
| TIMEOUT | 延迟超过客户端超时 |
| POISON | 返回畸形/不可解析消息 |

### 匹配能力（按阶段）

- 第一阶段：headers 精确/存在匹配、body 长度/文本包含、JSONPath 存在/等值；
- 第二阶段：JSONPath 比较、正则、XPath（XML 消息）、多条件 AND/OR；
- 不支持任意脚本，避免安全与性能问题；复杂逻辑由多个场景组合表达。

### 响应模板

- 支持占位符：消息 ID、correlationId、时间、随机 UUID、场景变量；
- 模板只允许受控变量，不执行任意表达式；
- 响应体大小上限可配。

## 8. 本地存储与保留

既然使用方自行部署，消息和场景默认保存在 Worker 本地，不进入 MockHub。

### SQLite 表（草案）

```text
scenario         场景与规则
message          收到的消息 / 发出的响应（正文按配置保存）
event            匹配、异常、连接、发送等事件
setting          配置版本与运行状态
```

### 消息正文保存

- 默认开启（按需求：需要查看消息内容）；
- 单条正文大小上限可配，例如 1MB，超限截断并标记；
- 保留策略可配：最大条数、最大天数、总字节上限；
- 超限自动清理最旧记录；
- 支持按场景/资源过滤后删除；
- 支持导出/导入场景（不包含 Broker 凭据）。

### 敏感数据

- 消息可能含业务敏感信息，Worker 部署在使用方环境，由使用方负责磁盘权限和备份；
- 本地管理界面默认只绑定 127.0.0.1；
- 远程访问必须启用访问令牌；
- 日志和事件表默认不重复记录完整正文，正文只在 message 表按保留策略保存；
- 可配置字段脱敏规则（如 header 名称黑名单），第一版可先提供关闭正文保存的开关。

## 9. 管理与可观测

### 本地管理 API

```text
GET    /api/status
GET    /api/resources
GET    /api/scenarios
POST   /api/scenarios
PUT    /api/scenarios/{id}
DELETE /api/scenarios/{id}
GET    /api/messages?limit=&offset=&resource=
GET    /api/events
POST   /api/test/publish
POST   /api/test/request
GET    /api/config/version
```

- 访问令牌通过环境变量或配置文件设置；
- 默认只监听本机；
- 不提供任意代码执行、脚本上传。

### 本地 Web UI（可选）

第一版可以只做最小页面：

- 连接状态与 Broker 信息（不含凭据）；
- 场景列表与编辑；
- 消息列表/详情；
- 测试发送；
- 事件与错误。

如果不做 Web UI，至少提供 CLI 和 REST，便于先用起来。

### 观测指标

- 连接状态、重连次数；
- 收到/发出消息数、匹配/未匹配数；
- 场景命中率、错误数、超时数；
- 消息存储条数/字节、清理次数；
- 最近错误与时间。

## 10. 可选接入 MockHub

如果使用方同时使用 MockHub，可让 Worker 注册到 MockHub，获得统一状态和监控；不注册也能独立运行。

### 控制协议（Worker → MockHub，出站）

```text
POST /api/adapters/register       注册，返回 adapter token
POST /api/adapters/{id}/heartbeat 心跳与状态
GET  /api/adapters/{id}/config    拉取场景/规则版本
POST /api/adapters/{id}/events    批量上报事件
GET  /api/adapters/{id}/commands  拉取测试命令（长轮询）
POST /api/adapters/{id}/results   回传测试结果
```

- Worker 只发起出站连接，MockHub 不需要访问 Worker；
- adapter token 只能访问自己的配置与事件；
- 一个 Worker 可绑定一个或多个团队，规则和事件带 team_id；
- 消息正文默认只在 Worker 本地，MockHub 只收元数据（资源、方向、匹配结果、耗时、大小、hash）；
- 使用方显式开启后，才允许把正文同步到 MockHub，并受保留策略限制；
- MockHub 未来可增加“外部服务 / MQ”页面：适配器状态、场景、测试、事件。

### 多团队边界

- 每个团队使用独立的资源前缀/规则集合；
- Worker 配置中显式声明绑定的团队；
- 未绑定团队的规则不加载、不执行；
- 跨团队共享队列/主题不在第一版支持。

## 11. 安全与隔离

- Worker 是使用方自部署的可信进程，但仍按最小权限设计：
  - Broker 凭据只从环境变量/本地密钥文件读取；
  - 管理端口默认 localhost；
  - 管理 API 需要令牌；
  - 不执行用户上传的代码/脚本；
  - 不把 Broker 凭据写入 SQLite、日志或事件表；
  - 消息正文按保留策略存储，支持关闭；
  - 限制单条消息大小、每秒处理量、批量发送量；
  - 断线重连使用退避，避免打爆 Broker；
  - 优雅关闭，确保消费者和连接释放。
- Worker 代码本身保持通用，客户配置、Broker 地址、消息内容只在使用方环境。

## 12. 可靠性与异常

- 连接断开：自动重连，状态 DEGRADED，事件记录原因；
- 消费处理异常：按配置 NACK/丢弃/记录，不阻塞其他消息；
- 发布失败：重试上限 + 退避，最终记录失败事件；
- 重复消息：可选按 messageId 去重；
- 慢消费者/背压：有界队列，超过阈值拒绝或延迟，保护进程；
- 消息顺序：第一版不承诺严格顺序，文档明确；
- 进程崩溃：SQLite 事务保证场景/事件一致性；未确认消息由 Broker 重投（取决于 Broker 配置）。

## 13. 协议路线

第一版不绑定具体 Broker，只提供 BrokerDriver 接口和内存测试驱动。真实需求出现后按以下标准选择第一个驱动：

| 候选 | 优点 | 注意点 |
| --- | --- | --- |
| AMQP（通用 Broker） | 请求-响应语义清晰，客户端成熟 | 需要部署真实 Broker |
| Kafka | 高吞吐、事件流常见 | 分区/offset/消费者组语义复杂，request/reply 需额外设计 |
| MQTT | 轻量、适合推送 | QoS/会话语义与业务队列不同 |
| IBM MQ / RocketMQ | 企业环境常见 | 客户端库/授权/部署限制 |

选择标准：使用方实际 Broker 类型、是否有测试环境、request/reply 还是 pub/sub、消息大小、是否需要 TLS/认证、是否能部署真实 Broker。

## 14. 测试与验收

- 内存驱动：场景匹配、请求-响应、发布、异常行为；
- 真实 Broker 集成测试：连接、订阅、发布、断线重连、NACK、重复；
- 规则：headers、JSONPath、模板变量、延迟、优先级；
- 存储：正文保存/关闭、大小截断、保留清理、导出导入；
- 管理 API：认证、限流、错误码；
- 安全：管理端口绑定、令牌、凭据不落库、日志不泄露；
- 可选 MockHub 集成：注册、心跳、配置版本、事件上报、断连重试；
- 并发/背压：有界队列、慢消费者、批量发布。

## 15. 实施阶段

1. Phase 0：定稿本设计，确认 Broker/模式/正文保留策略（本文档评审后决定）。
2. Phase 1：Worker 骨架、配置加载、内存 BrokerDriver、场景引擎、本地 SQLite、REST API。
3. Phase 2：第一个真实 BrokerDriver（按实际环境选型），完成 request/reply 或 publish 的最小闭环。
4. Phase 3：本地最小 Web UI / CLI、消息与事件查看、测试发送。
5. Phase 4：可选接入 MockHub（注册、心跳、配置、事件、管理页面）。
6. Phase 5：更多 BrokerDriver、故障注入、录制回放、性能与配额。

## 16. 待评审/待决策

以下问题供后续评审（包括 Codex）分析，不阻塞文档定稿：

1. 第一个要支持的 Broker 类型是什么？能否提供测试环境？
2. 主要场景是 REQUEST_REPLY、PUBLISH 还是两者？
3. 消息正文保留期限、条数、大小上限的默认值？
4. 是否需要本地 Web UI，还是先 CLI + REST？是否直接做 MockHub 集成页面？
5. Worker 交付形态：jar、Docker，还是两者都要？
6. 多团队是共用 Worker 还是每团队一个 Worker 实例？
7. 是否需要消息正文加密存储？使用方磁盘权限是否足够？
8. 是否需要 MockHub 统一管理 Broker 连接配置（含密钥），还是坚持只在使用方环境配置？
9. 第一版是否需要 TLS/双向认证支持？
10. 失败语义：未匹配消息默认忽略还是记录？是否需要死信队列？

## 17. 脱敏与仓库边界

- 本文不包含客户名称、域名、IP、端口、队列名、账号、消息样本或截图；
- 核心仓库只保留通用设计、接口、示例占位符；
- Worker 的客户配置、Broker 凭据、消息数据、部署脚本放在使用方环境或私有仓库；
- 示例配置中的标识全部是占位符，不对应真实资源。
