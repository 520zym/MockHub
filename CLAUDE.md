# MockHub — CLAUDE.md

本文档是 MockHub 项目的完整开发指南。详细规格拆分到 `docs/` 下的子文档，本文件只包含概览和关键决策。

> **阅读顺序**：本文件 → [架构与项目结构](docs/architecture.md) → [数据模型](docs/data-model.md) → [API 接口设计](docs/api-design.md) → [UI 设计规范](docs/ui-design.md)

---

## 项目概述

MockHub 是一个部署在内网 Windows Server 2016 上的接口模拟服务，支持 REST 和 SOAP 接口的 Mock，有现代化 Web 管理页面，完全离线运行。数据存储使用嵌入式 SQLite，无需安装任何外部数据库。

---

## 技术选型

### 后端
- **Java 8**（严格兼容，禁止 Java 9+ API）
- **Spring Boot 2.7.x** + **Spring Security**（JWT 认证）
- **Apache CXF 3.x** — SOAP 服务支持
- **SQLite**（`sqlite-jdbc` 嵌入式，打包进 fat jar）
- **Jackson** + **Lombok**
- 打包为单个可执行 **fat jar**（`spring-boot-maven-plugin`）

### 前端（内嵌在 jar 中）
- **Vue 3** + **Vite** → 构建产物放入 `src/main/resources/static`
- **Element Plus** — UI 组件库（需覆盖主题以匹配 Soft UI 风格，见 [UI 设计规范](docs/ui-design.md)）
- **Monaco Editor** — 代码编辑器
- **Axios** — HTTP 请求

### 数据存储
- 嵌入式 SQLite 单文件：`{data.path}/mockhub.db`
- 默认 `data.path=./data/`，支持启动参数覆盖
- WSDL 文件存储在 `{data.path}/wsdl/`

---

## 关键设计决策

### 1. Mock 路由：团队标识前缀

Mock 请求路径格式：**`/mock/{teamIdentifier}/your/api/path`**

- `teamIdentifier` 是团队的短标识（如 `FE`、`BE`），不同团队可以定义相同路径而互不干扰
- 示例：`GET /mock/FE/api/user/123`

### 2. 路径参数匹配

支持 `{xxx}` 风格的路径参数，匹配优先级：**精确匹配 > 路径参数匹配**

- 配置 `/api/user/{id}`，可匹配 `/api/user/123`
- 响应体中通过 `{{path.id}}` 引用路径参数值

### 3. SQLite 而非 JSON 文件

选择 SQLite 的理由：并发安全内置（WAL 模式）、查询/分页/筛选天然支持、单文件易备份、无需手动加锁和优雅停机处理。

### 4. JWT 认证范围

- `/api/**` 管理接口：需要 JWT 认证
- `/mock/**` Mock 请求、`/wsdl/**` WSDL 托管、`/api/health` 健康检查：**无需认证**
- JWT 密钥启动时随机生成，重启后旧 Token 自动失效

### 5. CORS

- `--mock.cors.enabled=true`（默认启用）
- 启用时 `/mock/**` 允许所有跨域请求

### 6. 多场景响应（v2 预留）

ApiDefinition 中预留 `scenarios` 字段，v1 为 null。v2 计划支持根据请求参数/Body 匹配不同响应。

---

## 启动参数

```bash
java -jar mockhub.jar \
  --server.port=8080 \
  --data.path=./data \
  --log.retain.mode=count \
  --log.retain.count=1000 \
  --log.retain.days=30 \
  --mock.cors.enabled=true
```

---

## 首次启动初始化

1. 自动创建 `data/` 目录和 `wsdl/` 子目录
2. 自动建表（DDL 内置在 `schema.sql`）
3. 自动创建默认超管：`admin / admin123`，`firstLogin=true`
4. 控制台打印初始账号信息

---

## 权限模型

| 操作 | 超级管理员 | 团队管理员 | 普通成员 |
|------|:----------:|:----------:|:--------:|
| 管理用户/团队 | Y | - | - |
| 管理团队成员 | Y | 本团队 | - |
| 接口增删改、启停、分组 | Y | 本团队 | 本团队 |
| 创建标签 | Y | 本团队 | 本团队 |
| 修改/删除标签、全局响应头 | Y | 本团队 | - |
| 导入导出、查看日志 | Y | 本团队 | 本团队 |
| 全局设置 | Y | - | - |

- Service 层入口校验权限，跨团队访问返回 403
- 超级管理员不能被删除或降级

---

## 构建与打包

```bash
# 开发
mvn spring-boot:run                    # 后端
cd frontend && npm install && npm run dev  # 前端

# 生产
cd frontend && npm run build           # 产物输出到 src/main/resources/static
mvn clean package -DskipTests          # 产物：target/mockhub-1.0.0.jar

# 运行
java -jar mockhub-1.0.0.jar --server.port=8080 --data.path=D:/mockhub/data
```

可选：使用 `winsw` 注册为 Windows 服务实现开机自启（README 中提供配置示例）。

---

## 注意事项

- 严格 Java 8，禁止 `var`、`record`、Java 9+ Stream API
- SQLite WAL 模式，通过 JDBC 连接池管理
- 管理接口统一格式：`{"code": 0, "msg": "success", "data": {...}}`
- Mock 接口直接返回用户配置的响应体，不包装统一格式
- 文件上传限制 10MB
- 前端路由 hash 模式（`#/`）
- 日志异步写入，启动时和写入后各执行一次清理
- WSDL 托管时动态替换 `<soap:address location>` 为实际服务地址

---

## 详细文档索引

| 文档 | 内容 | 谁需要看 |
|------|------|----------|
| [docs/architecture.md](docs/architecture.md) | 项目结构（模块分层）、Mock 分发流程、SOAP 处理、动态变量、导入导出 | 所有后端 |
| [docs/data-model.md](docs/data-model.md) | 全部实体字段定义、关联表 | 所有后端 |
| [docs/api-design.md](docs/api-design.md) | REST API 端点 + 请求/响应 DTO + 错误码（前后端契约） | 前端 + 后端 Controller 层 |
| [docs/contracts.md](docs/contracts.md) | 跨模块 Java 接口签名、依赖关系、开发顺序（内部契约） | 后端各模块 |
| [docs/ui-design.md](docs/ui-design.md) | Soft UI 设计风格、页面布局、组件规范 | 前端 |
