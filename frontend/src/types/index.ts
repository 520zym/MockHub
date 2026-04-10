// ===== 通用 =====

export interface PageResult<T> {
  total: number
  page: number
  size: number
  items: T[]
}

// ===== 用户 =====

export interface UserTeamRole {
  teamId: string
  teamName: string
  identifier: string
  role: 'TEAM_ADMIN' | 'MEMBER'
}

export interface User {
  id: string
  username: string
  displayName: string
  globalRole: 'SUPER_ADMIN' | 'TEAM_ADMIN' | 'MEMBER'
  firstLogin: boolean
  teams: UserTeamRole[]
  createdAt: string
}

export interface LoginResult {
  token: string
  user: User
}

// ===== 团队 =====

export interface Team {
  id: string
  name: string
  identifier: string
  color: string
  memberCount: number
  apiCount: number
  createdAt: string
}

export interface TeamMember {
  userId: string
  username: string
  displayName: string
  role: 'TEAM_ADMIN' | 'MEMBER'
}

// ===== 接口定义 =====

export interface TagVO {
  id: string
  name: string
  color: string
}

export interface ApiDefinitionVO {
  id: string
  teamId: string
  teamName: string
  teamIdentifier: string
  teamColor: string
  groupId: string | null
  groupName: string | null
  type: 'REST' | 'SOAP'
  name: string
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  path: string
  responseCode: number
  contentType: string
  delayMs: number
  enabled: boolean
  tags: TagVO[]
  createdBy: string
  createdByName: string
  createdAt: string
  updatedAt: string
}

export interface ApiDefinitionDetail extends ApiDefinitionVO {
  responseBody: string
  globalHeaderOverrides: Record<string, string>
  soapConfig: SoapConfig | null
}

export interface SoapConfig {
  wsdlFileName: string
  operations: SoapOperation[]
}

export interface SoapOperation {
  operationName: string
  soapAction: string
  responseCode: number
  delayMs: number
  responseBody: string
}

export interface ApiDefinitionForm {
  teamId: string
  groupId: string | null
  type: 'REST' | 'SOAP'
  name: string
  method: string
  path: string
  responseCode: number
  contentType: string
  responseBody: string
  delayMs: number
  enabled: boolean
  tagIds: string[]
  globalHeaderOverrides: Record<string, string>
  soapConfig: SoapConfig | null
}

// ===== 分组 =====

export interface ApiGroup {
  id: string
  teamId: string
  name: string
  sortOrder: number
  apiCount: number
  createdAt: string
}

// ===== 标签 =====

export interface Tag {
  id: string
  teamId: string
  name: string
  color: string
}

// ===== 全局响应头 =====

export interface GlobalHeader {
  id?: string
  headerName: string
  headerValue: string
  enabled: boolean
  sortOrder: number
}

// ===== 日志 =====

export interface OperationLog {
  id: string
  teamId: string
  userId: string
  username: string
  action: 'CREATE' | 'UPDATE' | 'DELETE' | 'TOGGLE' | 'IMPORT'
  targetType: string
  targetId: string
  targetName: string
  detail: string
  createdAt: string
}

export interface RequestLog {
  id: string
  teamId: string
  apiId: string
  apiPath: string
  method: string
  requestHeaders: Record<string, string>
  requestBody: string
  requestParams: Record<string, string>
  responseCode: number
  durationMs: number
  createdAt: string
}

// ===== 全局设置 =====

export interface Settings {
  logRetainMode: 'count' | 'days'
  logRetainCount: number
  logRetainDays: number
  mockCorsEnabled: boolean
}

// ===== 导入结果 =====

export interface ImportResult {
  imported: number
  skipped: number
  overridden: number
}

// ===== WSDL 解析结果 =====

export interface WsdlParseResult {
  fileName: string
  operations: Array<{
    operationName: string
    soapAction: string
  }>
}
