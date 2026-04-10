-- MockHub SQLite Schema
-- 所有主键使用 TEXT 存储 UUID，时间字段使用 TEXT 存储 ISO 格式

-- ========== 用户 ==========

CREATE TABLE IF NOT EXISTS user (
    id           TEXT PRIMARY KEY,
    username     TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    global_role  TEXT NOT NULL DEFAULT 'MEMBER',  -- SUPER_ADMIN / TEAM_ADMIN / MEMBER
    first_login  INTEGER NOT NULL DEFAULT 1,      -- 0=false, 1=true
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_username ON user(username);

-- ========== 团队 ==========

CREATE TABLE IF NOT EXISTS team (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    identifier TEXT NOT NULL,   -- 团队短标识，全局唯一，2~8 大写字母/数字
    color      TEXT NOT NULL DEFAULT '#185FA5',
    created_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_team_identifier ON team(identifier);
CREATE UNIQUE INDEX IF NOT EXISTS idx_team_name ON team(name);

-- ========== 用户-团队关联 ==========

CREATE TABLE IF NOT EXISTS user_team (
    user_id TEXT NOT NULL,
    team_id TEXT NOT NULL,
    role    TEXT NOT NULL DEFAULT 'MEMBER',  -- TEAM_ADMIN / MEMBER
    PRIMARY KEY (user_id, team_id),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_team_team_id ON user_team(team_id);

-- ========== 接口分组 ==========

CREATE TABLE IF NOT EXISTS api_group (
    id         TEXT PRIMARY KEY,
    team_id    TEXT NOT NULL,
    name       TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_api_group_team_id ON api_group(team_id);

-- ========== 接口定义 ==========

CREATE TABLE IF NOT EXISTS api_definition (
    id                     TEXT PRIMARY KEY,
    team_id                TEXT NOT NULL,
    group_id               TEXT,            -- 可为 null（未分组）
    type                   TEXT NOT NULL DEFAULT 'REST',  -- REST / SOAP
    name                   TEXT NOT NULL,
    description            TEXT,            -- 接口描述，存储 HTML 富文本
    method                 TEXT NOT NULL,   -- GET / POST / PUT / DELETE / PATCH
    path                   TEXT NOT NULL,   -- 不含团队标识前缀，支持 {xxx} 路径参数
    response_code          INTEGER NOT NULL DEFAULT 200,
    content_type           TEXT NOT NULL DEFAULT 'application/json',
    response_body          TEXT,            -- 可能很大（5~6MB），支持动态变量占位符
    delay_ms               INTEGER NOT NULL DEFAULT 0,
    enabled                INTEGER NOT NULL DEFAULT 1,   -- 0=false, 1=true
    global_header_overrides TEXT,           -- JSON 字符串，覆盖团队全局响应头
    soap_config            TEXT,            -- JSON 字符串，type=SOAP 时使用，见 SoapConfig
    scenarios              TEXT,            -- JSON 字符串，v1 为 null，v2 预留
    created_by             TEXT,
    created_at             TEXT NOT NULL,
    updated_at             TEXT NOT NULL,
    updated_by             TEXT,
    FOREIGN KEY (team_id)  REFERENCES team(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES api_group(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL,
    FOREIGN KEY (updated_by) REFERENCES user(id) ON DELETE SET NULL
);

-- 同团队内 path + method 唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_api_definition_team_path_method
    ON api_definition(team_id, path, method);

CREATE INDEX IF NOT EXISTS idx_api_definition_team_id ON api_definition(team_id);
CREATE INDEX IF NOT EXISTS idx_api_definition_group_id ON api_definition(group_id);
CREATE INDEX IF NOT EXISTS idx_api_definition_enabled ON api_definition(team_id, enabled);

-- ========== 标签 ==========

CREATE TABLE IF NOT EXISTS tag (
    id      TEXT PRIMARY KEY,
    team_id TEXT NOT NULL,
    name    TEXT NOT NULL,
    color   TEXT NOT NULL DEFAULT '#3B6D11',
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tag_team_id ON tag(team_id);

-- ========== 接口-标签关联 ==========

CREATE TABLE IF NOT EXISTS api_tag (
    api_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    PRIMARY KEY (api_id, tag_id),
    FOREIGN KEY (api_id) REFERENCES api_definition(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_api_tag_tag_id ON api_tag(tag_id);

-- ========== 全局响应头 ==========

CREATE TABLE IF NOT EXISTS global_header (
    id           TEXT PRIMARY KEY,
    team_id      TEXT NOT NULL,
    header_name  TEXT NOT NULL,
    header_value TEXT NOT NULL,
    enabled      INTEGER NOT NULL DEFAULT 1,  -- 0=false, 1=true
    sort_order   INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_global_header_team_id ON global_header(team_id);

-- ========== 操作日志 ==========

CREATE TABLE IF NOT EXISTS operation_log (
    id          TEXT PRIMARY KEY,
    team_id     TEXT,
    user_id     TEXT,
    username    TEXT NOT NULL,
    action      TEXT NOT NULL,      -- CREATE / UPDATE / DELETE / TOGGLE / IMPORT
    target_type TEXT NOT NULL,      -- API / GROUP / TAG / TEAM / USER 等
    target_id   TEXT,
    target_name TEXT,
    detail      TEXT,
    created_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_operation_log_team_id ON operation_log(team_id);
CREATE INDEX IF NOT EXISTS idx_operation_log_created_at ON operation_log(created_at);

-- ========== 请求日志 ==========

CREATE TABLE IF NOT EXISTS request_log (
    id              TEXT PRIMARY KEY,
    team_id         TEXT,
    api_id          TEXT,
    api_path        TEXT,
    method          TEXT,
    request_headers TEXT,    -- JSON 字符串
    request_body    TEXT,    -- JSON 字符串
    request_params  TEXT,    -- JSON 字符串
    response_code   INTEGER,
    duration_ms     INTEGER,
    created_at      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_request_log_team_id ON request_log(team_id);
CREATE INDEX IF NOT EXISTS idx_request_log_created_at ON request_log(created_at);
CREATE INDEX IF NOT EXISTS idx_request_log_api_id ON request_log(api_id);

-- ========== 接口返回体（多返回体支持） ==========

CREATE TABLE IF NOT EXISTS api_response (
    id                  TEXT PRIMARY KEY,
    api_id              TEXT NOT NULL,
    soap_operation_name TEXT,            -- REST 为 null；SOAP 填 operationName
    name                TEXT NOT NULL DEFAULT 'Default',
    response_code       INTEGER NOT NULL DEFAULT 200,
    content_type        TEXT NOT NULL DEFAULT 'application/json',
    response_body       TEXT,            -- 支持动态变量占位符，可能很大
    delay_ms            INTEGER NOT NULL DEFAULT 0,
    is_active           INTEGER NOT NULL DEFAULT 0,  -- 0=false, 1=true，同一 api_id + soap_operation_name 下只有一个为 1
    sort_order          INTEGER NOT NULL DEFAULT 0,
    conditions          TEXT,            -- v2 预留：JSON 条件匹配规则，当前为 null
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    FOREIGN KEY (api_id) REFERENCES api_definition(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_api_response_api_id ON api_response(api_id);
CREATE INDEX IF NOT EXISTS idx_api_response_active ON api_response(api_id, is_active);
