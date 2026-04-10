# MockHub

内网接口模拟服务，支持 REST 和 SOAP 接口 Mock，提供现代化 Web 管理界面。单个 jar 文件即可运行，零外部依赖，专为离线内网环境设计。

---

## 功能特性

- **REST Mock** -- 支持 GET / POST / PUT / DELETE / PATCH，自定义状态码、响应头、响应体
- **SOAP Mock** -- 上传 WSDL 自动解析 Operation，独立配置每个操作的返回 XML
- **团队隔离** -- 多团队独立管理接口，Mock 路径按团队标识隔离，互不干扰
- **路径参数** -- 支持 `/api/user/{id}` 风格路径匹配，响应体中通过 `{{path.id}}` 引用参数值
- **动态变量** -- 响应体支持 `{{timestamp}}`、`{{uuid}}`、`{{date}}`、`{{datetime}}`、`{{random_int}}` 等占位符
- **Monaco Editor** -- 内置代码编辑器，语法高亮、格式化、错误标红，支持 JSON / XML / 纯文本
- **大文本支持** -- 响应体支持 5~6 MB 大文本
- **WSDL 托管** -- 上传的 WSDL 文件可通过 `/wsdl/{fileName}` 直接访问，`soap:address` 自动替换为实际地址
- **全局响应头** -- 团队级别的公共响应头，接口级别可覆盖
- **导入导出** -- 按团队导出接口定义（含分组、标签），支持合并或覆盖两种导入模式
- **操作日志 / 请求日志** -- 记录管理操作和 Mock 请求，支持按条数或天数自动清理
- **权限控制** -- 超级管理员 / 团队管理员 / 普通成员 三级权限
- **CORS 支持** -- Mock 接口默认允许跨域，可通过参数关闭
- **SQLite 存储** -- 嵌入式单文件数据库，无需安装，易于备份

---

## 环境要求

- **Java 8** 或更高版本（推荐 Java 8，已严格兼容）
- Windows / Linux / macOS 均可运行

---

## 快速开始

### 1. 下载

从 Release 页面下载 `mockhub-1.0.0.jar`。

### 2. 运行

```bash
java -jar mockhub-1.0.0.jar
```

首次启动会自动创建 `data/` 目录和 SQLite 数据库，并初始化默认管理员账号。

### 3. 访问

浏览器打开 `http://localhost:8080`，使用默认账号登录：

- 用户名：`admin`
- 密码：`admin123`

> 首次登录会强制要求修改密码。

### 4. 使用 Mock

创建接口后，Mock 地址格式为：

```
http://{host}:{port}/mock/{teamIdentifier}/your/api/path
```

例如团队标识为 `FE`，配置了 `GET /api/user/info`，则 Mock 地址为：

```
GET http://localhost:8080/mock/FE/api/user/info
```

---

## 启动参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--server.port` | `8080` | 服务端口 |
| `--data.path` | `./data` | 数据目录路径（SQLite 数据库、WSDL 文件） |
| `--log.retain.mode` | `count` | 日志保留模式：`count`（按条数）或 `days`（按天数） |
| `--log.retain.count` | `1000` | `count` 模式下保留的最大条数 |
| `--log.retain.days` | `30` | `days` 模式下保留的天数 |
| `--mock.cors.enabled` | `true` | Mock 接口是否允许跨域 |

示例：

```bash
java -jar mockhub-1.0.0.jar \
  --server.port=9090 \
  --data.path=D:/mockhub/data \
  --log.retain.mode=days \
  --log.retain.days=7
```

---

## 开发者指南

### 技术栈

- 后端：Java 8 + Spring Boot 2.7.x + Spring Security + Apache CXF 3.x + SQLite
- 前端：Vue 3 + Vite + Element Plus + Monaco Editor + Axios

### 本地开发

```bash
# 启动后端（项目根目录）
mvn spring-boot:run

# 启动前端（另开终端）
cd frontend
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`，API 请求会代理到后端 `http://localhost:8080`。

### 生产构建

```bash
# 1. 构建前端（产物输出到 src/main/resources/static）
cd frontend
npm run build

# 2. 打包 fat jar
cd ..
mvn clean package -DskipTests
```

构建产物：`target/mockhub-1.0.0.jar`

---

## 注册为 Windows 服务

使用 [WinSW](https://github.com/winsw/winsw) 可将 MockHub 注册为 Windows 服务，实现开机自启动。

### 步骤

1. 下载 `WinSW-x64.exe`，重命名为 `mockhub-service.exe`，放到 jar 同级目录

2. 在同级目录创建 `mockhub-service.xml`：

```xml
<service>
  <id>MockHub</id>
  <name>MockHub</name>
  <description>MockHub 接口模拟服务</description>
  <executable>java</executable>
  <arguments>-jar mockhub-1.0.0.jar --server.port=8080 --data.path=./data</arguments>
  <workingdirectory>%BASE%</workingdirectory>
  <logpath>%BASE%\logs</logpath>
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>3</keepFiles>
  </log>
</service>
```

3. 以管理员身份运行命令：

```bash
# 安装服务
mockhub-service.exe install

# 启动服务
mockhub-service.exe start

# 查看状态
mockhub-service.exe status

# 停止服务
mockhub-service.exe stop

# 卸载服务
mockhub-service.exe uninstall
```

---

## 数据备份

MockHub 的所有数据存储在 `data/` 目录下，核心是 `mockhub.db` 文件（SQLite 数据库）。

建议定期备份整个 `data/` 目录：

```bash
# 示例：备份到指定目录
xcopy /E /I data D:\backup\mockhub-data-%date:~0,4%%date:~5,2%%date:~8,2%
```

备份内容包括：

- `mockhub.db` -- 全部业务数据（用户、团队、接口定义、日志等）
- `wsdl/` -- 上传的 WSDL 文件

> 建议在服务停止时备份，或利用 SQLite 的 WAL 模式特性在运行时安全复制。

---

## 开源协议

[MIT License](LICENSE)
