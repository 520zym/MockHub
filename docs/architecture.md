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
