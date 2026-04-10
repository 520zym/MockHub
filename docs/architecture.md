# 架构与项目结构

## 项目结构（模块分层）

按业务模块组织，每个模块内按 controller / service / model / repository 分层。

```
mockhub/
├── src/
│   ├── main/
│   │   ├── java/com/mockhub/
│   │   │   ├── MockHubApplication.java
│   │   │   │
│   │   │   ├── common/                          # ── 公共基础 ──
│   │   │   │   ├── config/
│   │   │   │   │   ├── SecurityConfig.java      # Spring Security + JWT 配置
│   │   │   │   │   ├── DataSourceConfig.java    # SQLite 数据源 + WAL 模式
│   │   │   │   │   ├── CorsConfig.java          # CORS 配置
│   │   │   │   │   └── WebConfig.java           # 静态资源、大文件上传限制等
│   │   │   │   ├── filter/
│   │   │   │   │   └── JwtAuthFilter.java
│   │   │   │   ├── model/
│   │   │   │   │   ├── Result.java              # 统一响应 {"code","msg","data"}
│   │   │   │   │   └── enums/
│   │   │   │   │       ├── UserRole.java        # SUPER_ADMIN, TEAM_ADMIN, MEMBER
│   │   │   │   │       ├── HttpMethod.java      # GET, POST, PUT, DELETE, PATCH
│   │   │   │   │       ├── ApiType.java         # REST, SOAP
│   │   │   │   │       └── ContentType.java     # JSON, XML, TEXT
│   │   │   │   └── util/
│   │   │   │       ├── JwtUtil.java
│   │   │   │       ├── PasswordUtil.java
│   │   │   │       └── DynamicVariableUtil.java # {{timestamp}}、{{path.xxx}} 等替换
│   │   │   │
│   │   │   ├── auth/                            # ── 认证模块 ──
│   │   │   │   ├── AuthController.java          # login / logout / change-password
│   │   │   │   └── AuthService.java
│   │   │   │
│   │   │   ├── system/                          # ── 系统管理（用户、团队、设置、健康检查）──
│   │   │   │   ├── controller/
│   │   │   │   │   ├── UserController.java
│   │   │   │   │   ├── TeamController.java
│   │   │   │   │   ├── SettingsController.java
│   │   │   │   │   └── HealthController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── UserService.java
│   │   │   │   │   ├── TeamService.java
│   │   │   │   │   └── InitService.java         # 首次启动初始化
│   │   │   │   ├── model/
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── User.java
│   │   │   │   │   │   ├── Team.java
│   │   │   │   │   │   └── UserTeam.java        # 用户-团队关联
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── LoginRequest.java
│   │   │   │   │       ├── LoginResponse.java
│   │   │   │   │       └── ...
│   │   │   │   └── repository/
│   │   │   │       ├── UserRepository.java
│   │   │   │       └── TeamRepository.java
│   │   │   │
│   │   │   ├── mock/                            # ── Mock 核心（接口定义、分发、分组、标签、SOAP）──
│   │   │   │   ├── controller/
│   │   │   │   │   ├── MockDispatchController.java   # /mock/{teamId}/** 分发入口
│   │   │   │   │   ├── ApiController.java            # 接口 CRUD
│   │   │   │   │   ├── GroupController.java
│   │   │   │   │   ├── TagController.java
│   │   │   │   │   ├── GlobalHeaderController.java
│   │   │   │   │   └── ImportExportController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── MockDispatchService.java      # 路径匹配 + 响应构建
│   │   │   │   │   ├── ApiService.java
│   │   │   │   │   ├── GroupService.java
│   │   │   │   │   ├── TagService.java
│   │   │   │   │   ├── GlobalHeaderService.java
│   │   │   │   │   ├── SoapService.java              # WSDL 解析、SOAP 分发
│   │   │   │   │   ├── ImportExportService.java
│   │   │   │   │   └── DynamicVariableService.java
│   │   │   │   ├── model/
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── ApiDefinition.java
│   │   │   │   │   │   ├── ApiGroup.java
│   │   │   │   │   │   ├── Tag.java
│   │   │   │   │   │   ├── ApiTag.java               # 接口-标签关联
│   │   │   │   │   │   └── GlobalHeader.java
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── ApiDefinitionDTO.java
│   │   │   │   │   │   └── ...
│   │   │   │   │   └── SoapConfig.java               # 嵌套对象，JSON 存储在 api_definition 表
│   │   │   │   └── repository/
│   │   │   │       ├── ApiRepository.java
│   │   │   │       ├── GroupRepository.java
│   │   │   │       ├── TagRepository.java
│   │   │   │       └── GlobalHeaderRepository.java
│   │   │   │
│   │   │   └── log/                             # ── 日志模块 ──
│   │   │       ├── LogController.java
│   │   │       ├── LogService.java              # 异步写入 + 保留策略清理
│   │   │       ├── model/
│   │   │       │   ├── OperationLog.java
│   │   │       │   └── RequestLog.java
│   │   │       └── LogRepository.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── schema.sql                       # SQLite 建表 DDL
│   │       └── static/                          # Vue 构建产物
│   └── test/
├── frontend/                                    # Vue 3 前端源码（见 ui-design.md）
├── pom.xml
├── README.md
└── CLAUDE.md
```

---

## Mock 请求分发流程

`MockDispatchController` 监听 `/mock/{teamIdentifier}/**`：

```
收到请求 GET /mock/FE/api/user/123
  ↓
提取团队标识（FE）+ 请求路径（/api/user/123）+ 方法（GET）
  ↓
通过团队标识查找 teamId
  ↓
团队不存在 → 404: {"error": "Team not found: FE"}
  ↓
在 api_definition 表中匹配：teamId + method + enabled=true
匹配优先级：
  1. 精确匹配：path 完全一致
  2. 路径参数匹配：path 含 {xxx}，如 /api/user/{id} 匹配 /api/user/123
  ↓
未匹配 → 404: {"error": "No mock found for GET /api/user/123"}
  ↓
匹配到 → 提取路径参数（如 id=123）
  ↓
REST 请求 → 直接走下一步
SOAP 请求（Content-Type: text/xml 或 application/soap+xml）→ 从 SOAPAction 头匹配 operation
  ↓
执行延迟（delayMs > 0 时 Thread.sleep；SOAP 各 operation 可独立配置延迟）
  ↓
处理动态变量（{{timestamp}}、{{path.id}} 等）
  ↓
添加全局响应头（团队级别）+ 接口级别覆盖
  ↓
返回响应码 + responseBody
  ↓
异步写入请求日志
```

---

## 路径参数匹配算法

Mock 分发时需要将实际请求路径（如 `/api/user/123`）与配置路径（如 `/api/user/{id}`）进行匹配。算法分两阶段：先精确匹配，再路径参数匹配。

### 匹配优先级

1. **精确匹配优先**：遍历所有不含 `{xxx}` 的配置路径，如果请求路径与某条配置路径完全一致，直接命中返回，不再继续匹配。
2. **路径参数匹配**：精确匹配未命中时，遍历所有含 `{xxx}` 的配置路径，将其编译为正则表达式进行匹配。按定义顺序（即数据库返回顺序）遍历，首个匹配成功的即为命中。

### 路径参数转正则

将配置路径中的 `{xxx}` 占位符替换为正则捕获组 `([^/]+)`，整体加上首尾锚定。例如：

| 配置路径 | 编译后的正则 |
|----------|-------------|
| `/api/user/{id}` | `^/api/user/([^/]+)$` |
| `/api/order/{orderId}/item/{itemId}` | `^/api/order/([^/]+)/item/([^/]+)$` |

### 路径参数提取

匹配成功后，按顺序从正则的 capture group 中提取参数值，与配置路径中的占位符名称一一对应，存入 `Map<String, String>`。例如请求 `/api/order/ORD001/item/3` 匹配 `/api/order/{orderId}/item/{itemId}` 后，得到 `{orderId: "ORD001", itemId: "3"}`。

提取的路径参数通过 `{{path.xxx}}` 占位符注入到响应体中（如 `{{path.id}}` 替换为 `123`）。

### 伪代码

```
function matchPath(teamId, requestPath, method):
    // 查出该团队所有已启用的、方法匹配的接口定义
    candidates = findByTeamIdAndMethodAndEnabled(teamId, method, true)

    // 第一阶段：精确匹配
    for api in candidates:
        if not containsPlaceholder(api.path):
            if api.path == requestPath:
                return MatchResult(api, emptyMap)

    // 第二阶段：路径参数匹配
    for api in candidates:
        if containsPlaceholder(api.path):
            paramNames = extractParamNames(api.path)        // ["id"] or ["orderId", "itemId"]
            regex = api.path.replaceAll("\\{[^}]+\\}", "([^/]+)")
            regex = "^" + regex + "$"
            matcher = Pattern.compile(regex).matcher(requestPath)
            if matcher.matches():
                pathParams = new LinkedHashMap()
                for i in range(paramNames.size()):
                    pathParams.put(paramNames[i], matcher.group(i + 1))
                return MatchResult(api, pathParams)

    return null  // 未匹配

function containsPlaceholder(path):
    return path.contains("{")

function extractParamNames(path):
    // 用正则 \{(\w+)\} 提取所有占位符名称，按出现顺序返回
    return findAll("\\{(\\w+)\\}", path)
```

### 性能考虑

- 路径参数正则可在接口创建/更新时预编译并缓存，避免每次请求重复编译。
- 精确匹配阶段可用 `HashMap<String, ApiDefinition>` 以 `path` 为 key 实现 O(1) 查找。
- 路径参数模式的接口数量通常较少，线性遍历即可满足性能要求。

---

## SOAP 处理

- SOAP 接口也通过 `/mock/{teamIdentifier}/**` 分发
- 从请求头 `SOAPAction` 提取操作名，匹配 SoapConfig 中的 operation
- 每个 operation 独立配置 `responseCode`、`delayMs`、`responseBody`
- WSDL 文件上传后存储在 `data/wsdl/` 目录
- WSDL 托管路径：`GET /wsdl/{wsdlFileName}`（无需认证），Content-Type `text/xml`
- 托管时动态替换 `<soap:address location>` 为实际服务器地址

---

## 动态变量替换

在返回响应体之前，对 responseBody 做字符串替换：

| 占位符 | 替换值 |
|--------|--------|
| `{{timestamp}}` | 当前毫秒时间戳 |
| `{{uuid}}` | 随机 UUID |
| `{{date}}` | 当前日期 yyyy-MM-dd |
| `{{datetime}}` | 当前日期时间 yyyy-MM-dd HH:mm:ss |
| `{{random_int}}` | 0~10000 随机整数 |
| `{{path.xxx}}` | 路径参数值，如 `{{path.id}}` 对应 URL 中 `{id}` 的实际值 |

---

## 全局响应头叠加规则

Mock 响应返回前，按以下步骤构建最终的 HTTP 响应头：

1. **加载团队全局响应头**：从 `global_header` 表查询目标团队（`teamId`）的所有记录，过滤 `enabled=true`，按 `sortOrder` 升序排列
2. **构建基础 Map**：将查询结果组装为 `headerName → headerValue` 的有序 Map
3. **应用接口级别覆盖**：读取当前 `ApiDefinition.globalHeaderOverrides`（JSON 对象），逐个 key 处理：
   - key 已存在于基础 Map → **替换** value
   - key 不存在于基础 Map → **新增** 该 header
   - value 为空字符串（`""`）→ **删除** 该 header（允许接口级别移除某个全局头）
4. **动态变量替换**：对最终 Map 中所有 value 执行动态变量替换（如 `{{uuid}}`、`{{timestamp}}` 等，规则同「动态变量替换」章节）
5. **写入响应**：将最终 Map 中的所有键值对设置到 HTTP 响应头中

**示例**：

团队全局响应头配置：
| headerName | headerValue | enabled | sortOrder |
|---|---|---|---|
| `X-Request-Id` | `{{uuid}}` | true | 1 |
| `X-Powered-By` | `MockHub` | true | 2 |
| `X-Debug` | `true` | false | 3 |

接口 `globalHeaderOverrides`：
```json
{
  "X-Request-Id": "fixed-123",
  "X-Custom": "hello",
  "X-Powered-By": ""
}
```

最终响应头：
- `X-Request-Id: fixed-123`（被覆盖，不再执行 `{{uuid}}` 替换）
- `X-Custom: hello`（新增）
- `X-Debug` 不出现（`enabled=false`，未进入基础 Map）
- `X-Powered-By` 不出现（value 为空字符串，被移除）

---

## 导入导出

### 导出格式
```json
{
  "version": "1.0",
  "exportedAt": "2024-01-01T00:00:00",
  "teamName": "前端团队",
  "groups": [...],
  "tags": [...],
  "apis": [...],
  "globalHeaders": [...]
}
```

### 导入模式
- **合并**：同团队内已存在的接口（按 path + method 匹配）跳过，新接口追加
- **覆盖**：同团队内已存在的接口直接覆盖，新接口追加

---

## 大文本支持（5~6 MB）

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
server:
  tomcat:
    max-http-form-post-size: 10MB
```

- SQLite TEXT 字段最大支持 1GB，5~6MB 无压力
- 前端 Monaco Editor 加载大文本时禁用 minimap

---

## 日志保留策略

- **清理时机**：应用启动时 + 每次写入后
- `count` 模式：`DELETE FROM request_log WHERE id NOT IN (SELECT id FROM request_log ORDER BY created_at DESC LIMIT ?)`
- `days` 模式：`DELETE FROM request_log WHERE created_at < datetime('now', '-N days')`
