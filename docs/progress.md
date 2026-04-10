# MockHub 开发进度

## 已完成

### 1. pom.xml ✅
- parent: spring-boot-starter-parent:2.7.18
- Java 8 编译（source/target 1.8）
- 依赖：web、security、jdbc、sqlite-jdbc、cxf、jjwt、jaxb-api、lombok(provided)、test
- spring-boot-maven-plugin + maven-compiler-plugin
- **注意**：Lombok 标记为 provided scope，因开发环境 JDK 25 不兼容 Lombok annotation processing。common 模块全部手写 getter/setter。后续模块在 Java 8 环境编译时可正常使用 Lombok 注解。

### 2. 主类 MockHubApplication.java ✅
- @SpringBootApplication + @EnableAsync + @EnableConfigurationProperties

### 3. common 模块 ✅

#### config/
- DataProperties.java — data.path 配置
- LogRetainProperties.java — log.retain.mode/count/days 配置
- MockCorsProperties.java — mock.cors.enabled 配置
- DataSourceConfig.java — SQLite WAL 模式 + schema.sql 执行 + 目录创建
- SecurityConfig.java — JWT 无状态认证 + 路径放行规则
- CorsConfig.java — Mock 路径 CORS 配置
- WebConfig.java — WebMvcConfigurer 预留

#### filter/
- JwtAuthFilter.java — JWT 认证过滤器，shouldNotFilter 跳过放行路径

#### model/
- Result.java — 统一响应
- PageResult.java — 分页响应
- BizException.java — 业务异常
- GlobalExceptionHandler.java — 全局异常处理器

#### model/enums/
- UserRole.java — SUPER_ADMIN, TEAM_ADMIN, MEMBER
- MockHttpMethod.java — GET, POST, PUT, DELETE, PATCH
- ApiType.java — REST, SOAP
- ContentType.java — JSON, XML, TEXT

#### util/
- JwtUtil.java — JWT 生成/校验（HMAC-SHA256，8 小时有效期）
- PasswordUtil.java — BCrypt 加密/校验
- DynamicVariableUtil.java — {{timestamp}}、{{uuid}}、{{path.xxx}} 等替换
- SecurityContextUtil.java — 从 SecurityContext 获取当前用户信息
- PermissionChecker.java — 权限校验接口（实现类由 system 模块提供）

## 验证
- `mvn compile` 通过 ✅

## 已修改文件清单
- pom.xml（新建）
- src/main/java/com/mockhub/MockHubApplication.java（新建）
- src/main/java/com/mockhub/common/config/ — 7 个文件（新建）
- src/main/java/com/mockhub/common/filter/JwtAuthFilter.java（新建）
- src/main/java/com/mockhub/common/model/ — 4 个文件 + enums/ 4 个文件（新建）
- src/main/java/com/mockhub/common/util/ — 5 个文件（新建）

## 关键决策
1. Lombok scope=provided：JDK 25 下 Lombok annotation processor 崩溃（TypeTag::UNKNOWN），改为手写 getter/setter
2. SecurityConfig 用 WebSecurityConfigurerAdapter：Spring Boot 2.7 兼容方式，deprecated 警告可忽略
3. JwtUtil 启动时随机生成密钥：服务重启后旧 Token 自动失效，符合内网场景
4. JwtAuthFilter 用 shouldNotFilter 跳过放行路径，比在 doFilterInternal 中判断更清晰
