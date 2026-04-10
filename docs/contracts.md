# 模块契约

本文档定义前后端所有模块并行开发所需的契约，包括后端模块间 Java 接口、前端 TypeScript 类型定义、开发时 API 对接方式。

---

## 模块依赖关系

```
后端：
  common ← 所有模块（枚举、工具类、统一响应、异常、配置属性）
  system ← auth（校验用户密码）
         ← mock（通过 identifier 查团队、获取用户所属团队）
  log    ← mock（异步写请求日志、写操作日志）

前端：
  api/request.js ← 所有页面（Axios 实例、拦截器、JWT 注入、错误处理）
  stores/user.js ← 所有页面（当前用户信息、权限判断）
  components/layout/ ← 所有页面（AppLayout、Sidebar、Topbar）
```

## 全量并行开发编排

```
第一步（串行）：
  后端 common 模块 — 接口定义 + 枚举 + 工具类 + schema.sql + Result/BizException
  前端基础设施 — Axios 封装 + 路由 + 布局组件 + 类型定义 + 样式变量

第二步（全部并行）：
  ┌─ 后端 system 模块（User + Team CRUD，实现 TeamService/UserService 接口）
  ├─ 后端 auth 模块（登录/登出/改密码，面向 UserService 接口编码）
  ├─ 后端 mock 模块（接口 CRUD + 分发 + SOAP + 标签 + 分组 + 全局响应头 + 导入导出）
  ├─ 后端 log 模块（日志写入/查询/清理，实现 LogService 接口）
  ├─ 前端 Login.vue（登录页 + 首次修改密码）
  ├─ 前端 ApiList.vue + ApiEdit.vue（接口列表 + 编辑，含 Monaco Editor）
  ├─ 前端 TeamManage.vue + UserManage.vue（团队/用户管理）
  ├─ 前端 LogView.vue（操作日志 + 请求日志）
  └─ 前端 Settings.vue（全局设置）

第三步（串行）：前后端集成联调
```

**关键点**：前端在第二步不依赖后端运行，通过 Vite 的 Mock 插件（`vite-plugin-mock`）或硬编码 mock 数据开发，所有页面面向 `api-design.md` 中定义的请求/响应 DTO 编码。

---

## 前端契约

### Axios 封装约定（`api/request.js`）

```javascript
// 所有页面的 API 调用都通过此实例
const request = axios.create({ baseURL: '/api' })

// 请求拦截器：自动注入 JWT
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 0) {
      // 40002: Token 过期 → 跳转登录
      if (res.code === 40002) {
        router.push('/login')
        return Promise.reject(res)
      }
      // 其他业务错误 → ElMessage.error(res.msg)
      ElMessage.error(res.msg)
      return Promise.reject(res)
    }
    return res.data  // 成功时直接返回 data 部分
  },
  error => {
    ElMessage.error('网络错误')
    return Promise.reject(error)
  }
)
```

**所有 API 调用函数返回的是 `data` 部分**（拦截器已解包），不是完整的 `{code, msg, data}` 对象。

### TypeScript 类型定义（`types/index.ts`）

前端各页面面向以下类型编码，与 `api-design.md` 中的响应 DTO 一一对应：

```typescript
// ===== 通用 =====

interface PageResult<T> {
  total: number
  page: number
  size: number
  items: T[]
}

// ===== 用户 =====

interface UserTeamRole {
  teamId: string
  teamName: string
  identifier: string
  role: 'TEAM_ADMIN' | 'MEMBER'
}

interface User {
  id: string
  username: string
  displayName: string
  globalRole: 'SUPER_ADMIN' | 'TEAM_ADMIN' | 'MEMBER'
  firstLogin: boolean
  teams: UserTeamRole[]
  createdAt: string
}

interface LoginResult {
  token: string
  user: User
}

// ===== 团队 =====

interface Team {
  id: string
  name: string
  identifier: string
  color: string
  memberCount: number
  apiCount: number
  createdAt: string
}

interface TeamMember {
  userId: string
  username: string
  displayName: string
  role: 'TEAM_ADMIN' | 'MEMBER'
}

// ===== 接口定义 =====

interface TagVO {
  id: string
  name: string
  color: string
}

interface ApiDefinitionVO {
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

// 编辑/详情时的完整对象（含 responseBody）
interface ApiDefinitionDetail extends ApiDefinitionVO {
  responseBody: string
  globalHeaderOverrides: Record<string, string>
  soapConfig: SoapConfig | null
}

interface SoapConfig {
  wsdlFileName: string
  operations: SoapOperation[]
}

interface SoapOperation {
  operationName: string
  soapAction: string
  responseCode: number
  delayMs: number
  responseBody: string
}

// 创建/更新请求体
interface ApiDefinitionForm {
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

interface ApiGroup {
  id: string
  teamId: string
  name: string
  sortOrder: number
  apiCount: number
  createdAt: string
}

// ===== 标签 =====

interface Tag {
  id: string
  teamId: string
  name: string
  color: string
}

// ===== 全局响应头 =====

interface GlobalHeader {
  id?: string
  headerName: string
  headerValue: string
  enabled: boolean
  sortOrder: number
}

// ===== 日志 =====

interface OperationLog {
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

interface RequestLog {
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

interface Settings {
  logRetainMode: 'count' | 'days'
  logRetainCount: number
  logRetainDays: number
  mockCorsEnabled: boolean
}

// ===== 导入结果 =====

interface ImportResult {
  imported: number
  skipped: number
  overridden: number
}

// ===== WSDL 解析结果 =====

interface WsdlParseResult {
  fileName: string
  operations: Array<{
    operationName: string
    soapAction: string
  }>
}
```

### 前端 API 调用函数约定（`api/*.js`）

每个模块一个文件，函数命名和参数与 `api-design.md` 端点一一对应：

```javascript
// api/auth.js
export const login = (data) => request.post('/auth/login', data)           // → LoginResult
export const changePassword = (data) => request.post('/auth/change-password', data)

// api/apis.js
export const getApis = (params) => request.get('/apis', { params })        // → PageResult<ApiDefinitionVO>
export const getApiDetail = (id) => request.get(`/apis/${id}`)             // → ApiDefinitionDetail
export const createApi = (data) => request.post('/apis', data)
export const updateApi = (id, data) => request.put(`/apis/${id}`, data)
export const deleteApi = (id) => request.delete(`/apis/${id}`)
export const copyApi = (id) => request.post(`/apis/${id}/copy`)
export const toggleApi = (id) => request.put(`/apis/${id}/toggle`)         // → { enabled: boolean }
export const importApis = (formData) => request.post('/apis/import', formData)
export const exportApis = (teamId) => request.get('/apis/export', { params: { teamId }, responseType: 'blob' })

// api/teams.js
export const getTeams = () => request.get('/teams')                        // → Team[]
export const createTeam = (data) => request.post('/teams', data)
export const updateTeam = (id, data) => request.put(`/teams/${id}`, data)
export const deleteTeam = (id) => request.delete(`/teams/${id}`)
export const getTeamMembers = (id) => request.get(`/teams/${id}/members`)  // → TeamMember[]
export const addTeamMember = (id, data) => request.post(`/teams/${id}/members`, data)
export const removeTeamMember = (id, userId) => request.delete(`/teams/${id}/members/${userId}`)
export const updateMemberRole = (id, userId, data) => request.put(`/teams/${id}/members/${userId}/role`, data)

// api/users.js
export const getUsers = () => request.get('/users')                        // → User[]
export const createUser = (data) => request.post('/users', data)
export const updateUser = (id, data) => request.put(`/users/${id}`, data)
export const deleteUser = (id) => request.delete(`/users/${id}`)
export const assignTeams = (id, data) => request.post(`/users/${id}/teams`, data)

// api/groups.js
export const getGroups = (teamId) => request.get('/groups', { params: { teamId } })
export const createGroup = (data) => request.post('/groups', data)
export const updateGroup = (id, data) => request.put(`/groups/${id}`, data)
export const deleteGroup = (id) => request.delete(`/groups/${id}`)

// api/tags.js
export const getTags = (teamId) => request.get('/tags', { params: { teamId } })
export const createTag = (data) => request.post('/tags', data)
export const updateTag = (id, data) => request.put(`/tags/${id}`, data)
export const deleteTag = (id) => request.delete(`/tags/${id}`)

// api/soap.js
export const uploadWsdl = (formData) => request.post('/soap/wsdl/upload', formData)
export const getWsdlOperations = (fileName) => request.get(`/soap/wsdl/${fileName}/operations`)

// api/globalHeaders.js
export const getGlobalHeaders = (teamId) => request.get('/global-headers', { params: { teamId } })
export const saveGlobalHeaders = (teamId, data) => request.put('/global-headers', data, { params: { teamId } })

// api/logs.js
export const getOperationLogs = (params) => request.get('/logs/operation', { params })
export const getRequestLogs = (params) => request.get('/logs/request', { params })

// api/settings.js
export const getSettings = () => request.get('/settings')
export const saveSettings = (data) => request.put('/settings', data)
```

### Vite 开发代理配置

```javascript
// vite.config.js
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})
```

前端开发时后端未就绪可使用 `vite-plugin-mock` 或在 `api/request.js` 中临时拦截返回 mock 数据，集成联调时切换到真实代理即可。

### Store 约定（`stores/user.js`）

```javascript
// 所有页面通过此 store 获取当前用户信息和权限判断
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: null   // LoginResult.user
  }),

  getters: {
    isSuperAdmin: (state) => state.user?.globalRole === 'SUPER_ADMIN',
    isTeamAdmin: (state) => (teamId) => {
      if (state.user?.globalRole === 'SUPER_ADMIN') return true
      return state.user?.teams?.some(t => t.teamId === teamId && t.role === 'TEAM_ADMIN')
    },
    userTeamIds: (state) => state.user?.teams?.map(t => t.teamId) || []
  }
})
```

---

## 后端模块间 Java 接口契约

### common 模块公共契约

#### Result\<T\> 统一响应

```java
public class Result<T> {
    private int code;       // 0=成功，非0=错误码
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data);
    public static <T> Result<T> error(int code, String msg);
}
```

#### PageResult\<T\> 分页响应

```java
public class PageResult<T> {
    private long total;
    private int page;
    private int size;
    private List<T> items;
}
```

#### BizException 业务异常

```java
public class BizException extends RuntimeException {
    private int code;
    private String msg;

    public BizException(int code, String msg);
}
```

由全局 `@ControllerAdvice` 捕获，自动转为 `Result.error(code, msg)` 响应。

#### SecurityContextUtil 当前用户上下文

```java
public class SecurityContextUtil {

    /**
     * 获取当前登录用户 ID
     * @return 用户 ID，未登录时抛 BizException(40002)
     */
    public static String getCurrentUserId();

    /**
     * 获取当前登录用户完整信息
     * @return User 对象，未登录时抛 BizException(40002)
     */
    public static User getCurrentUser();

    /**
     * 当前用户是否为超级管理员
     */
    public static boolean isSuperAdmin();
}
```

#### 配置属性类

```java
@ConfigurationProperties(prefix = "data")
public class DataProperties {
    private String path = "./data";   // 数据目录
}

@ConfigurationProperties(prefix = "log.retain")
public class LogRetainProperties {
    private String mode = "count";    // "count" 或 "days"
    private int count = 1000;
    private int days = 30;
}

@ConfigurationProperties(prefix = "mock.cors")
public class MockCorsProperties {
    private boolean enabled = true;
}
```

---

### system 模块对外接口

#### TeamService

```java
public interface TeamService {

    /**
     * 通过团队短标识查找团队（Mock 分发时调用）
     *
     * @param identifier 团队短标识如 "FE"，匹配时不区分大小写
     * @return 匹配的团队，未找到时返回 null
     * 副作用：无
     */
    Team findByIdentifier(String identifier);

    /**
     * 查询用户可见的所有团队
     * - 超级管理员：返回全部团队
     * - 其他用户：返回所属团队
     *
     * @param userId 用户 ID，不能为 null
     * @return 团队列表，无团队时返回空列表（不返回 null）
     * 副作用：无
     */
    List<Team> findTeamsByUserId(String userId);

    /**
     * 获取团队详情
     *
     * @param teamId 团队 ID
     * @return 团队对象
     * @throws BizException(40303) 团队不存在
     */
    Team getById(String teamId);
}
```

#### UserService

```java
public interface UserService {

    /**
     * 通过用户名查找用户（登录时调用）
     *
     * @param username 用户名，区分大小写
     * @return 用户对象含 passwordHash，未找到时返回 null
     * 副作用：无
     */
    User findByUsername(String username);

    /**
     * 通过 ID 获取用户
     *
     * @param id 用户 ID
     * @return 用户对象，未找到时返回 null
     * 副作用：无
     */
    User findById(String id);

    /**
     * 更新用户的 firstLogin 标志
     *
     * @param userId 用户 ID
     * @param firstLogin 新值
     * 副作用：写数据库
     */
    void updateFirstLogin(String userId, boolean firstLogin);

    /**
     * 更新用户密码
     *
     * @param userId 用户 ID
     * @param newPasswordHash BCrypt 哈希后的密码
     * 副作用：写数据库
     */
    void updatePassword(String userId, String newPasswordHash);
}
```

---

### log 模块对外接口

#### LogService

```java
public interface LogService {

    /**
     * 异步写入请求日志（Mock 分发后调用，不阻塞响应）
     *
     * @param log 请求日志对象，所有字段由调用方填充
     * 副作用：异步写数据库，写入后自动触发日志清理策略
     * 线程安全：是（内部使用异步队列）
     */
    void asyncLogRequest(RequestLog log);

    /**
     * 同步写入操作日志
     *
     * @param log 操作日志对象，所有字段由调用方填充
     * 副作用：同步写数据库，写入后自动触发日志清理策略
     */
    void logOperation(OperationLog log);
}
```

---

### mock 模块内部接口（不对外，仅模块内部使用）

#### ApiService

```java
public interface ApiService {

    /**
     * 查找匹配的接口定义（Mock 分发时调用）
     * 匹配规则：teamId + method + enabled=true + 路径匹配
     * 优先级：精确匹配 > 路径参数匹配
     *
     * @param teamId 团队 ID
     * @param method HTTP 方法
     * @param path 请求路径（不含 /mock/{identifier} 前缀）
     * @return 匹配结果，含匹配到的 ApiDefinition 和提取的路径参数；未匹配返回 null
     */
    ApiMatchResult findMatch(String teamId, String method, String path);
}

/**
 * 路径匹配结果
 */
public class ApiMatchResult {
    private ApiDefinition api;
    private Map<String, String> pathVariables;  // 如 {"id": "123"}
}
```

---

## 权限校验约定

- **Controller 层**不做权限校验，只做参数绑定和响应包装
- **Service 层入口**统一校验，通过 `SecurityContextUtil` 获取当前用户，判断是否有权操作目标团队数据
- 跨团队访问统一抛 `BizException(40102, "不能访问其他团队数据")`
- 超级管理员跳过团队校验

推荐的校验工具方法（放在 common 中）：

```java
public class PermissionUtil {

    /**
     * 校验当前用户是否有权操作目标团队
     * - 超级管理员：直接通过
     * - 其他用户：检查 user_team 关联
     *
     * @param teamId 目标团队 ID
     * @throws BizException(40102) 无权访问
     */
    public static void checkTeamAccess(String teamId);

    /**
     * 校验当前用户是否为目标团队的管理员（或超级管理员）
     *
     * @param teamId 目标团队 ID
     * @throws BizException(40101) 无操作权限
     */
    public static void checkTeamAdmin(String teamId);
}
```
