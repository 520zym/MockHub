# Changelog

本项目遵循 [Semantic Versioning](https://semver.org/)。

## [1.4.1] - 2026-04-12

### 修复

- 接口管理列表的标签筛选支持多选（命中任一标签即返回）
- 接口名称列宽度过窄,改为随容器自适应（最小 180px）
- 接口编辑页响应体自动识别 Content-Type 后,格式标签会同步切换；格式化按钮按当前选中的格式（JSON / XML / Text）分别处理,修复 XML 内容被强制按 JSON 格式化的问题

### 其他

- 前端构建产物（`src/main/resources/static/assets/`）不再入库,改由构建流程生成

## [1.4.0] - 2026-04-11

### 首次公开发布

- REST Mock：支持 GET / POST / PUT / DELETE / PATCH，自定义状态码、响应头、响应体
- SOAP Mock：上传 WSDL 自动解析 Operation，独立配置每个操作的返回 XML
- 团队隔离：多团队独立管理接口，Mock 路径按团队标识隔离
- 路径参数：支持 `/api/user/{id}` 风格路径匹配，响应体引用 `{{path.id}}`
- 动态变量：`{{timestamp}}`、`{{uuid}}`、`{{date}}`、`{{datetime}}`、`{{random_int}}` 等
- 多返回体：单个接口可配置多个响应体，支持切换活跃返回体
- 接口描述：支持富文本描述接口用途和说明
- Monaco Editor：内置代码编辑器，JSON / XML / 纯文本语法高亮和格式化
- WSDL 托管：上传的 WSDL 通过 `/wsdl/{fileName}` 直接访问
- 全局响应头：团队级别公共响应头，接口级别可覆盖
- 导入导出：按团队导出接口定义，支持合并或覆盖导入
- 操作日志 / 请求日志：支持按条数或天数自动清理
- 权限控制：超级管理员 / 团队管理员 / 普通成员 三级权限
- 用户管理：密码修改、超管重置密码
- CORS 支持：Mock 接口默认允许跨域
- SQLite 嵌入式存储：单文件数据库，零外部依赖
