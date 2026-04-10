package com.mockhub.mock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.util.DynamicVariableUtil;
import com.mockhub.log.LogService;
import com.mockhub.log.model.RequestLog;
import com.mockhub.mock.model.SoapConfig;
import com.mockhub.mock.model.SoapOperation;
import com.mockhub.mock.model.dto.ApiMatchResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mock 请求分发服务 -- 核心中的核心
 * <p>
 * 完整的分发流程：
 * <pre>
 * 1. 收到请求 → 提取团队标识、请求路径、HTTP 方法
 * 2. 通过团队标识查找团队 → 不存在返回 404
 * 3. 调用 ApiService.findMatch 匹配接口定义（精确匹配 > 路径参数匹配）
 * 4. 未匹配到 → 返回 404
 * 5. 判断 SOAP 请求（Content-Type: text/xml 或 application/soap+xml）
 *    → 从 SOAPAction 请求头匹配 SoapConfig 中的 operation
 *    → 使用 operation 独立的 responseCode、delayMs、responseBody
 * 6. 执行延迟（delayMs > 0 时 Thread.sleep）
 * 7. 动态变量替换（{{timestamp}}、{{path.xxx}} 等）
 * 8. 全局响应头叠加（团队级别 + 接口级别覆盖）
 * 9. 返回配置的响应码 + responseBody
 * 10. 异步写入请求日志
 * </pre>
 *
 * <b>注意</b>：此 Controller 不返回 Result 包装，直接返回 responseBody。
 * 错误时返回 {"error": "..."} 格式。
 */
@Service
public class MockDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MockDispatchService.class);

    private final TeamService teamService;
    private final ApiService apiService;
    private final GlobalHeaderService globalHeaderService;
    private final LogService logService;
    private final ObjectMapper objectMapper;

    public MockDispatchService(TeamService teamService,
                               ApiService apiService,
                               GlobalHeaderService globalHeaderService,
                               LogService logService,
                               ObjectMapper objectMapper) {
        this.teamService = teamService;
        this.apiService = apiService;
        this.globalHeaderService = globalHeaderService;
        this.logService = logService;
        this.objectMapper = objectMapper;
    }

    /**
     * 分发 Mock 请求并构建响应
     * <p>
     * 此方法是 Mock 请求处理的完整流程入口，包含：
     * 团队查找 → 接口匹配 → SOAP 处理 → 延迟执行 → 动态变量替换 → 全局响应头叠加 → 请求日志记录
     *
     * @param teamIdentifier 团队短标识（如 "FE"），从 URL 路径中提取
     * @param method         HTTP 方法（GET/POST/PUT/DELETE/PATCH）
     * @param path           请求路径（不含 /mock/{teamIdentifier} 前缀）
     * @param request        原始 HttpServletRequest，用于读取请求头、请求体等信息
     * @return ResponseEntity 包含响应码、响应头和响应体
     */
    public ResponseEntity<String> dispatch(String teamIdentifier, String method,
                                           String path, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("收到 Mock 请求: {} /mock/{}/{}", method, teamIdentifier, path);

        // ====== 1. 通过团队标识查找团队 ======
        Team team = teamService.findByIdentifier(teamIdentifier);
        if (team == null) {
            log.warn("团队不存在: identifier={}", teamIdentifier);
            return buildErrorResponse(HttpStatus.NOT_FOUND,
                    "Team not found: " + teamIdentifier);
        }
        String teamId = team.getId();
        log.debug("团队匹配成功: identifier={} → teamId={}", teamIdentifier, teamId);

        // ====== 2. 匹配接口定义 ======
        ApiMatchResult matchResult = apiService.findMatch(teamId, method, path);
        if (matchResult == null) {
            log.warn("未匹配到接口: teamId={}, method={}, path={}", teamId, method, path);
            return buildErrorResponse(HttpStatus.NOT_FOUND,
                    "No mock found for " + method + " " + path);
        }

        ApiDefinition api = matchResult.getApi();
        Map<String, String> pathVariables = matchResult.getPathVariables();
        log.info("接口匹配成功: apiId={}, apiName={}, configPath={}, pathVars={}",
                api.getId(), api.getName(), api.getPath(), pathVariables);

        // ====== 3. 判断是否为 SOAP 请求 ======
        String contentType = request.getContentType();
        boolean isSoap = isSoapRequest(contentType);
        String responseBody = api.getResponseBody();
        int responseCode = api.getResponseCode();
        int delayMs = api.getDelayMs();

        if (isSoap && api.getSoapConfig() != null && !api.getSoapConfig().isEmpty()) {
            log.debug("检测到 SOAP 请求, Content-Type={}", contentType);

            // 从 SOAPAction 请求头匹配 operation
            String soapAction = request.getHeader("SOAPAction");
            if (soapAction != null) {
                // 去掉引号
                soapAction = soapAction.replace("\"", "").trim();
            }
            log.debug("SOAPAction 头: {}", soapAction);

            SoapOperation matchedOp = matchSoapOperation(api.getSoapConfig(), soapAction);
            if (matchedOp != null) {
                // 使用 operation 独立的配置
                responseBody = matchedOp.getResponseBody();
                responseCode = matchedOp.getResponseCode();
                delayMs = matchedOp.getDelayMs();
                log.info("SOAP operation 匹配成功: operationName={}, soapAction={}",
                        matchedOp.getOperationName(), matchedOp.getSoapAction());
            } else {
                log.warn("未匹配到 SOAP operation: soapAction={}", soapAction);
                // 未匹配到 operation 时仍使用接口默认配置
            }
        }

        // ====== 4. 执行延迟 ======
        if (delayMs > 0) {
            log.debug("执行延迟: {}ms", delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("延迟执行被中断");
            }
        }

        // ====== 5. 动态变量替换 ======
        if (responseBody != null && !responseBody.isEmpty()) {
            responseBody = DynamicVariableUtil.replace(responseBody, pathVariables);
            log.debug("动态变量替换完成, 响应体长度={}", responseBody.length());
        }

        // ====== 6. 全局响应头叠加 ======
        Map<String, String> headerOverrides = parseHeaderOverrides(api.getGlobalHeaderOverrides());
        Map<String, String> finalHeaders = globalHeaderService.buildHeaders(
                teamId, headerOverrides, pathVariables);
        log.debug("最终响应头: {}", finalHeaders.keySet());

        // ====== 7. 构建 HTTP 响应 ======
        HttpHeaders httpHeaders = new HttpHeaders();
        for (Map.Entry<String, String> entry : finalHeaders.entrySet()) {
            httpHeaders.add(entry.getKey(), entry.getValue());
        }
        // 设置 Content-Type，确保包含 charset=UTF-8 避免中文乱码
        String respContentType = api.getContentType();
        if (respContentType != null && !respContentType.isEmpty()) {
            if (!respContentType.toLowerCase().contains("charset")) {
                respContentType = respContentType + "; charset=UTF-8";
            }
            httpHeaders.set(HttpHeaders.CONTENT_TYPE, respContentType);
        } else {
            httpHeaders.set(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Mock 响应: apiId={}, statusCode={}, duration={}ms", api.getId(), responseCode, durationMs);

        // ====== 8. 异步写入请求日志 ======
        asyncWriteRequestLog(team, api, method, path, request, responseCode, durationMs);

        return new ResponseEntity<String>(
                responseBody != null ? responseBody : "",
                httpHeaders,
                HttpStatus.valueOf(responseCode));
    }

    /**
     * 判断请求是否为 SOAP 请求
     * <p>
     * SOAP 1.1: Content-Type 为 text/xml
     * SOAP 1.2: Content-Type 为 application/soap+xml
     *
     * @param contentType 请求的 Content-Type
     * @return 是 SOAP 请求返回 true
     */
    private boolean isSoapRequest(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase();
        return ct.contains("text/xml") || ct.contains("application/soap+xml");
    }

    /**
     * 从 SoapConfig JSON 中匹配指定 soapAction 对应的 operation
     *
     * @param soapConfigJson 接口定义中存储的 SoapConfig JSON 字符串
     * @param soapAction     请求头中的 SOAPAction 值（已去除引号）
     * @return 匹配到的 SoapOperation，未匹配返回 null
     */
    private SoapOperation matchSoapOperation(String soapConfigJson, String soapAction) {
        if (soapConfigJson == null || soapConfigJson.isEmpty()) {
            return null;
        }

        try {
            SoapConfig config = objectMapper.readValue(soapConfigJson, SoapConfig.class);
            if (config.getOperations() == null || config.getOperations().isEmpty()) {
                return null;
            }

            for (SoapOperation op : config.getOperations()) {
                // 匹配 soapAction（支持精确匹配和尾部匹配）
                if (soapAction != null && op.getSoapAction() != null) {
                    if (soapAction.equals(op.getSoapAction()) ||
                            soapAction.endsWith(op.getOperationName())) {
                        return op;
                    }
                }
                // 如果 soapAction 为 null，且只有一个 operation，默认返回第一个
                if (soapAction == null && config.getOperations().size() == 1) {
                    return op;
                }
            }
        } catch (Exception e) {
            log.error("解析 SoapConfig 失败: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 解析接口级别的全局响应头覆盖配置
     *
     * @param overridesJson globalHeaderOverrides JSON 字符串
     * @return 覆盖配置 Map，解析失败时返回空 Map
     */
    private Map<String, String> parseHeaderOverrides(String overridesJson) {
        if (overridesJson == null || overridesJson.isEmpty()) {
            return new LinkedHashMap<String, String>();
        }
        try {
            return objectMapper.readValue(overridesJson,
                    new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            log.error("解析 globalHeaderOverrides 失败: {}", e.getMessage());
            return new LinkedHashMap<String, String>();
        }
    }

    /**
     * 异步写入请求日志
     * <p>
     * 收集请求的所有信息（请求头、请求体、请求参数），组装 RequestLog 对象后
     * 调用 LogService.asyncLogRequest 异步写入，不阻塞 Mock 响应。
     *
     * @param team         团队对象
     * @param api          匹配到的接口定义
     * @param method       HTTP 方法
     * @param path         请求路径
     * @param request      原始请求
     * @param responseCode 响应状态码
     * @param durationMs   响应耗时
     */
    private void asyncWriteRequestLog(Team team, ApiDefinition api, String method,
                                      String path, HttpServletRequest request,
                                      int responseCode, long durationMs) {
        try {
            RequestLog reqLog = new RequestLog();
            reqLog.setId(UUID.randomUUID().toString());
            reqLog.setTeamId(team.getId());
            reqLog.setApiId(api.getId());
            reqLog.setApiPath(path);
            reqLog.setMethod(method);
            reqLog.setResponseCode(responseCode);
            reqLog.setDurationMs(durationMs);
            reqLog.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));

            // 收集请求头
            Map<String, String> headers = new LinkedHashMap<String, String>();
            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    headers.put(name, request.getHeader(name));
                }
            }
            reqLog.setRequestHeaders(objectMapper.writeValueAsString(headers));

            // 收集请求参数
            Map<String, String> params = new LinkedHashMap<String, String>();
            Enumeration<String> paramNames = request.getParameterNames();
            if (paramNames != null) {
                while (paramNames.hasMoreElements()) {
                    String name = paramNames.nextElement();
                    params.put(name, request.getParameter(name));
                }
            }
            reqLog.setRequestParams(objectMapper.writeValueAsString(params));

            // 收集请求体（尝试读取，若已被消费则忽略）
            try {
                BufferedReader reader = request.getReader();
                if (reader != null) {
                    StringBuilder body = new StringBuilder();
                    char[] buffer = new char[4096];
                    int bytesRead;
                    while ((bytesRead = reader.read(buffer)) != -1) {
                        body.append(buffer, 0, bytesRead);
                        // 限制请求体日志大小，超过 1MB 截断
                        if (body.length() > 1024 * 1024) {
                            body.append("...(truncated)");
                            break;
                        }
                    }
                    reqLog.setRequestBody(body.toString());
                }
            } catch (Exception e) {
                // 请求体可能已被消费，忽略
                log.debug("读取请求体失败（可能已被消费）: {}", e.getMessage());
                reqLog.setRequestBody("");
            }

            logService.asyncLogRequest(reqLog);
        } catch (Exception e) {
            // 日志写入失败不影响 Mock 响应
            log.error("写入请求日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建错误响应
     * <p>
     * Mock 请求错误时返回 {"error": "..."} 格式，不使用 Result 包装。
     *
     * @param status  HTTP 状态码
     * @param message 错误信息
     * @return ResponseEntity
     */
    private ResponseEntity<String> buildErrorResponse(HttpStatus status, String message) {
        String body = "{\"error\": \"" + escapeJson(message) + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<String>(body, headers, status);
    }

    /**
     * 简单的 JSON 字符串转义
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
