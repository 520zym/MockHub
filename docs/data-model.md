# 数据模型

所有数据存储在 SQLite 数据库 `{data.path}/mockhub.db` 中。以下用 JSON 格式描述字段，实际为 SQLite 表。

---

## User

```json
{
  "id": "uuid",
  "username": "admin",
  "passwordHash": "bcrypt hash",
  "displayName": "超级管理员",
  "globalRole": "SUPER_ADMIN",
  "firstLogin": true,
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00"
}
```

`globalRole` 取值：`SUPER_ADMIN` / `TEAM_ADMIN` / `MEMBER`

---

## UserTeam（关联表）

用户与团队的多对多关系，含团队内角色。

```json
{
  "userId": "user-uuid",
  "teamId": "team-uuid",
  "role": "TEAM_ADMIN"
}
```

`role` 取值：`TEAM_ADMIN` / `MEMBER`

---

## Team

```json
{
  "id": "uuid",
  "name": "前端团队",
  "identifier": "FE",
  "color": "#185FA5",
  "createdAt": "2024-01-01T00:00:00"
}
```

- `identifier`：团队短标识，用于 Mock 路由路径（`/mock/{identifier}/...`），全局唯一，建议 2~8 个大写字母

---

## ApiGroup

```json
{
  "id": "uuid",
  "teamId": "team-uuid",
  "name": "用户模块",
  "sortOrder": 1,
  "createdAt": "2024-01-01T00:00:00"
}
```

---

## ApiDefinition

```json
{
  "id": "uuid",
  "teamId": "team-uuid",
  "groupId": "group-uuid",
  "type": "REST",
  "name": "获取用户信息",
  "method": "GET",
  "path": "/api/user/{id}",
  "responseCode": 200,
  "contentType": "application/json",
  "responseBody": "{\"code\":0,\"data\":{\"id\":\"{{path.id}}\"}}",
  "delayMs": 0,
  "enabled": true,
  "globalHeaderOverrides": {},
  "soapConfig": null,
  "scenarios": null,
  "createdBy": "user-uuid",
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00",
  "updatedBy": "user-uuid"
}
```

字段说明：
- `type`：`REST` 或 `SOAP`
- `path`：不含团队标识前缀，支持 `{xxx}` 路径参数
- `responseBody`：可包含动态变量占位符
- `globalHeaderOverrides`：JSON 对象，覆盖团队全局响应头的特定值（如 `{"X-Request-Id": "fixed-value"}`）
- `soapConfig`：type=SOAP 时使用，JSON 字符串存储，见下方 SoapConfig
- `scenarios`：v1 为 null，v2 预留多场景响应

---

## ApiTag（关联表）

接口与标签的多对多关系。

```json
{
  "apiId": "api-uuid",
  "tagId": "tag-uuid"
}
```

---

## SoapConfig

以 JSON 字符串存储在 `api_definition.soap_config` 字段中。

```json
{
  "wsdlFileName": "IFOCNotice.wsdl",
  "operations": [
    {
      "operationName": "NoticeExtractService",
      "soapAction": "http://tempuri.org/NoticeExtractService",
      "responseCode": 200,
      "delayMs": 0,
      "responseBody": "<soap:Envelope>...</soap:Envelope>"
    }
  ]
}
```

每个 operation 独立配置响应状态码、延迟和响应体。

---

## Tag

```json
{
  "id": "uuid",
  "teamId": "team-uuid",
  "name": "支付模块",
  "color": "#3B6D11"
}
```

---

## GlobalHeader

团队级别的全局响应头，每个 Mock 响应自动附加。

```json
{
  "id": "uuid",
  "teamId": "team-uuid",
  "headerName": "X-Request-Id",
  "headerValue": "{{uuid}}",
  "enabled": true,
  "sortOrder": 1
}
```

- `headerValue` 支持动态变量（如 `{{uuid}}`、`{{timestamp}}`）
- 可被 ApiDefinition 的 `globalHeaderOverrides` 覆盖

---

## OperationLog

```json
{
  "id": "uuid",
  "teamId": "team-uuid",
  "userId": "user-uuid",
  "username": "张三",
  "action": "UPDATE",
  "targetType": "API",
  "targetId": "api-uuid",
  "targetName": "获取用户信息",
  "detail": "修改了响应体",
  "createdAt": "2024-01-01T00:00:00"
}
```

`action` 取值：`CREATE` / `UPDATE` / `DELETE` / `TOGGLE` / `IMPORT`

---

## RequestLog

```json
{
  "id": "uuid",
  "teamId": "team-uuid",
  "apiId": "api-uuid",
  "apiPath": "/api/user/info",
  "method": "GET",
  "requestHeaders": {},
  "requestBody": "",
  "requestParams": {},
  "responseCode": 200,
  "durationMs": 120,
  "createdAt": "2024-01-01T00:00:00"
}
```

- `requestHeaders`、`requestBody`、`requestParams` 以 JSON 字符串存储在 TEXT 字段中
