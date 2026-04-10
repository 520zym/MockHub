# API 接口设计

所有管理接口前缀 `/api/**`，需 JWT 认证（请求头 `Authorization: Bearer {token}`）。Mock 入口和 WSDL 托管无需认证。

## 通用约定

### 统一响应格式

```json
// 成功
{"code": 0, "msg": "success", "data": { ... }}

// 失败
{"code": 40001, "msg": "用户名或密码错误", "data": null}
```

### 分页响应格式

带分页的列表接口，`data` 结构统一为：

```json
{
  "total": 100,
  "page": 1,
  "size": 20,
  "items": [...]
}
```

### 错误码体系

| 范围 | 模块 | 说明 |
|------|------|------|
| 40001~40099 | 认证 | 登录、Token、密码相关 |
| 40101~40199 | 权限 | 无权限、跨团队访问 |
| 40201~40299 | 用户 | 用户增删改相关 |
| 40301~40399 | 团队 | 团队增删改相关 |
| 40401~40499 | 接口 | 接口增删改、导入导出相关 |
| 40501~40599 | 分组 | 分组相关 |
| 40601~40699 | 标签 | 标签相关 |
| 40701~40799 | SOAP/WSDL | WSDL 上传解析相关 |
| 50001~50099 | 系统 | 内部错误 |

常用错误码：

| 错误码 | 含义 |
|--------|------|
| 40001 | 用户名或密码错误 |
| 40002 | Token 无效或已过期 |
| 40003 | 首次登录需修改密码 |
| 40004 | 原密码错误 |
| 40101 | 无操作权限 |
| 40102 | 不能访问其他团队数据 |
| 40201 | 用户名已存在 |
| 40202 | 不能删除超级管理员 |
| 40203 | 不能降级超级管理员 |
| 40301 | 团队名称已存在 |
| 40302 | 团队标识已存在 |
| 40303 | 团队下有接口，不能删除 |
| 40401 | 同团队内路径+方法已存在 |
| 40402 | 接口不存在 |
| 40701 | WSDL 文件解析失败 |
| 50001 | 系统内部错误 |

### 认证错误处理

JWT 认证在 `JwtAuthFilter` 中统一拦截，权限校验在 Service 层处理。各场景的处理方式如下：

**1. JWT 校验失败（过期、签名无效、格式错误）**

`JwtAuthFilter` 直接返回 HTTP 401，不进入 Controller 层：

```json
HTTP 401
{"code": 40002, "msg": "Token 无效或已过期"}
```

**2. 无 Token 访问受保护接口**

请求头缺少 `Authorization` 或值为空，`JwtAuthFilter` 直接返回 HTTP 401：

```json
HTTP 401
{"code": 40002, "msg": "未登录"}
```

**3. 有 Token 但权限不足（跨团队访问等）**

Token 校验通过，但 Service 层判断当前用户无权操作目标资源（如普通成员访问其他团队数据），由 Service 层抛出 `BizException`，全局异常处理器返回 HTTP 200：

```json
HTTP 200
{"code": 40102, "msg": "不能访问其他团队数据"}
```

> 注意：权限不足返回 HTTP 200 而非 403，错误信息通过业务错误码 `code` 区分，与项目统一响应格式保持一致。

**4. 前端处理逻辑**

Axios 响应拦截器中统一处理认证错误：

- 收到 HTTP 401 状态码，或响应体中 `code === 40002` 时：
  1. 清除 `localStorage` 中的 `token`
  2. 跳转到 `/login` 页面
  3. 可选：弹出提示"登录已过期，请重新登录"

```javascript
// Axios 拦截器伪代码
axios.interceptors.response.use(
  response => {
    if (response.data && response.data.code === 40002) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return response
  },
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)
```

---

## 认证

### POST `/api/auth/login`

登录，返回 JWT Token。

**请求体：**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应 data：**
```json
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": "uuid",
    "username": "admin",
    "displayName": "超级管理员",
    "globalRole": "SUPER_ADMIN",
    "firstLogin": false,
    "teams": [
      {"teamId": "uuid", "teamName": "前端团队", "identifier": "FE", "role": "TEAM_ADMIN"}
    ]
  }
}
```

- `firstLogin=true` 时，前端应强制跳转修改密码页
- 错误码：40001 用户名或密码错误

### POST `/api/auth/logout`

登出（前端清除本地 Token 即可，后端无状态不做处理）。

### POST `/api/auth/change-password`

修改密码。

**请求体：**
```json
{
  "oldPassword": "admin123",
  "newPassword": "newPass456"
}
```

- 修改成功后 `firstLogin` 自动置为 false
- 错误码：40004 原密码错误

---

## 用户管理（超级管理员）

### GET `/api/users`

用户列表（不分页，用户数量不大）。

**响应 data：** `User[]`

```json
[
  {
    "id": "uuid",
    "username": "zhangsan",
    "displayName": "张三",
    "globalRole": "MEMBER",
    "firstLogin": false,
    "teams": [
      {"teamId": "uuid", "teamName": "前端团队", "identifier": "FE", "role": "MEMBER"}
    ],
    "createdAt": "2024-01-01T00:00:00"
  }
]
```

### POST `/api/users`

创建用户。

**请求体：**
```json
{
  "username": "zhangsan",
  "password": "初始密码",
  "displayName": "张三",
  "globalRole": "MEMBER"
}
```

- 创建后 `firstLogin=true`
- 错误码：40201 用户名已存在

### PUT `/api/users/{id}`

修改用户信息（不含密码和团队分配）。

**请求体：**
```json
{
  "displayName": "张三三",
  "globalRole": "TEAM_ADMIN"
}
```

- 错误码：40203 不能降级超级管理员

### DELETE `/api/users/{id}`

删除用户。

- 错误码：40202 不能删除超级管理员

### POST `/api/users/{id}/teams`

为用户分配团队（整体替换）。

**请求体：**
```json
{
  "teamRoles": [
    {"teamId": "team-uuid-1", "role": "TEAM_ADMIN"},
    {"teamId": "team-uuid-2", "role": "MEMBER"}
  ]
}
```

---

## 团队管理（超级管理员）

### GET `/api/teams`

团队列表。

**响应 data：** `Team[]`

```json
[
  {
    "id": "uuid",
    "name": "前端团队",
    "identifier": "FE",
    "color": "#185FA5",
    "memberCount": 5,
    "apiCount": 23,
    "createdAt": "2024-01-01T00:00:00"
  }
]
```

### POST `/api/teams`

创建团队。

**请求体：**
```json
{
  "name": "前端团队",
  "identifier": "FE",
  "color": "#185FA5"
}
```

- `identifier`：2~8 个大写字母/数字，全局唯一
- 错误码：40301 名称已存在，40302 标识已存在

### PUT `/api/teams/{id}`

修改团队。请求体同创建（字段可选更新）。

### DELETE `/api/teams/{id}`

删除团队。

- 错误码：40303 团队下有接口，不能删除

### GET `/api/teams/{id}/members`

团队成员列表。

**响应 data：**
```json
[
  {
    "userId": "uuid",
    "username": "zhangsan",
    "displayName": "张三",
    "role": "TEAM_ADMIN"
  }
]
```

### POST `/api/teams/{id}/members`

添加成员。

**请求体：**
```json
{
  "userId": "user-uuid",
  "role": "MEMBER"
}
```

### DELETE `/api/teams/{id}/members/{userId}`

移除成员。

### PUT `/api/teams/{id}/members/{userId}/role`

修改成员角色。

**请求体：**
```json
{
  "role": "TEAM_ADMIN"
}
```

---

## 接口管理

### GET `/api/apis`

接口列表（自动按当前用户所属团队过滤，超级管理员看到全部）。

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teamId | string | 否 | 按团队筛选 |
| groupId | string | 否 | 按分组筛选 |
| method | string | 否 | 按 HTTP 方法筛选 |
| enabled | boolean | 否 | 按启用状态筛选 |
| keyword | string | 否 | 按名称或路径模糊搜索 |
| tagId | string | 否 | 按标签筛选 |
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 20 |

**响应 data：** 分页格式，items 为 `ApiDefinitionVO[]`

```json
{
  "total": 100,
  "page": 1,
  "size": 20,
  "items": [
    {
      "id": "uuid",
      "teamId": "team-uuid",
      "teamName": "前端团队",
      "teamIdentifier": "FE",
      "teamColor": "#185FA5",
      "groupId": "group-uuid",
      "groupName": "用户模块",
      "type": "REST",
      "name": "获取用户信息",
      "method": "GET",
      "path": "/api/user/{id}",
      "responseCode": 200,
      "contentType": "application/json",
      "delayMs": 0,
      "enabled": true,
      "tags": [
        {"id": "tag-uuid", "name": "支付模块", "color": "#3B6D11"}
      ],
      "createdBy": "user-uuid",
      "createdByName": "张三",
      "createdAt": "2024-01-01T00:00:00",
      "updatedAt": "2024-01-01T00:00:00"
    }
  ]
}
```

> 注意：列表接口不返回 `responseBody`（可能很大），编辑时单独获取。

### POST `/api/apis`

创建接口。

**请求体：**
```json
{
  "teamId": "team-uuid",
  "groupId": "group-uuid",
  "type": "REST",
  "name": "获取用户信息",
  "method": "GET",
  "path": "/api/user/{id}",
  "responseCode": 200,
  "contentType": "application/json",
  "responseBody": "{\"code\":0,\"data\":{}}",
  "delayMs": 0,
  "enabled": true,
  "tagIds": ["tag-uuid-1", "tag-uuid-2"],
  "globalHeaderOverrides": {"X-Request-Id": "fixed"},
  "soapConfig": null
}
```

- 错误码：40401 同团队内路径+方法已存在

### PUT `/api/apis/{id}`

修改接口。请求体同创建。

### GET `/api/apis/{id}`

获取单个接口详情（含 `responseBody`）。

**响应 data：** 完整的 `ApiDefinition` 对象（含 responseBody、soapConfig 等所有字段）。

### DELETE `/api/apis/{id}`

删除接口。

- 错误码：40402 接口不存在

### POST `/api/apis/{id}/copy`

复制接口。自动在名称后追加" (副本)"，路径后追加 `-copy`。

**响应 data：** 新创建的接口对象。

### PUT `/api/apis/{id}/toggle`

切换启用/禁用状态（无请求体，服务端取反）。

**响应 data：**
```json
{
  "enabled": false
}
```

### POST `/api/apis/import`

导入接口。

**请求体：** `multipart/form-data`

| 字段 | 类型 | 说明 |
|------|------|------|
| file | File | 导出的 JSON 文件 |
| teamId | string | 导入到哪个团队 |
| mode | string | `merge`（合并）或 `override`（覆盖） |

**响应 data：**
```json
{
  "imported": 15,
  "skipped": 3,
  "overridden": 0
}
```

### GET `/api/apis/export?teamId=xxx`

导出团队所有接口。返回 JSON 文件下载（`Content-Disposition: attachment`）。

---

## ~~分组管理~~（已移除）

> 分组功能已从前端移除。后端 API（`/api/groups`）仍保留但不再使用。

---

## 标签管理

### GET `/api/tags?teamId=xxx`

标签列表。

**响应 data：** `Tag[]`

```json
[
  {"id": "uuid", "teamId": "team-uuid", "name": "支付模块", "color": "#3B6D11"}
]
```

### POST `/api/tags`

创建标签。

**请求体：**
```json
{
  "teamId": "team-uuid",
  "name": "支付模块",
  "color": "#3B6D11"
}
```

### PUT `/api/tags/{id}`

修改标签（团队管理员+）。请求体同创建。

### DELETE `/api/tags/{id}`

删除标签（团队管理员+）。自动清理 api_tag 关联记录。

---

## SOAP / WSDL

### POST `/api/soap/wsdl/upload`

上传 WSDL 文件。

**请求体：** `multipart/form-data`

| 字段 | 类型 | 说明 |
|------|------|------|
| file | File | `.wsdl` 文件 |

**响应 data：**
```json
{
  "fileName": "IFOCNotice.wsdl",
  "operations": [
    {
      "operationName": "NoticeExtractService",
      "soapAction": "http://tempuri.org/NoticeExtractService"
    }
  ]
}
```

- 错误码：40701 WSDL 文件解析失败

### GET `/api/soap/wsdl/{fileName}/operations`

重新解析已上传的 WSDL，返回操作列表。响应同上。

### GET `/wsdl/{fileName}`（无需认证）

托管 WSDL 文件，Content-Type `text/xml`。`<soap:address location>` 动态替换为实际服务地址。

---

## 全局响应头

### GET `/api/global-headers?teamId=xxx`

**响应 data：** `GlobalHeader[]`

```json
[
  {
    "id": "uuid",
    "teamId": "team-uuid",
    "headerName": "X-Request-Id",
    "headerValue": "{{uuid}}",
    "enabled": true,
    "sortOrder": 1
  }
]
```

### PUT `/api/global-headers?teamId=xxx`

整体替换团队的全局响应头。

**请求体：**
```json
[
  {
    "headerName": "X-Request-Id",
    "headerValue": "{{uuid}}",
    "enabled": true,
    "sortOrder": 1
  },
  {
    "headerName": "X-Powered-By",
    "headerValue": "MockHub",
    "enabled": true,
    "sortOrder": 2
  }
]
```

---

## 日志

### GET `/api/logs/operation`

操作日志（分页）。

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teamId | string | 是 | 团队 ID |
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 50 |

**响应 data：** 分页格式，items 为 `OperationLog[]`

### GET `/api/logs/request`

请求日志（分页）。查询参数同上。items 为 `RequestLog[]`。

---

## 全局配置（超级管理员）

### GET `/api/settings`

**响应 data：**
```json
{
  "logRetainMode": "count",
  "logRetainCount": 1000,
  "logRetainDays": 30,
  "mockCorsEnabled": true
}
```

### PUT `/api/settings`

保存配置。请求体同响应 data 结构。

---

## 健康检查（无需认证）

### GET `/api/health`

**响应（直接返回，不包装 Result）：**
```json
{
  "status": "UP",
  "version": "1.0.0"
}
```

---

## Mock 请求入口（无需认证）

### ANY `/mock/{teamIdentifier}/**`

接收所有 Mock 请求。`teamIdentifier` 为团队标识（如 `FE`、`BE`）。

**示例：** `GET /mock/FE/api/user/123`

**响应：** 直接返回用户配置的 `responseBody`，不包装 Result 格式。响应头包含团队全局响应头 + 接口级覆盖。

**错误响应（也不包装 Result）：**
```json
// 团队不存在
HTTP 404: {"error": "Team not found: XX"}

// 未匹配到接口
HTTP 404: {"error": "No mock found for GET /api/user/123"}
```
