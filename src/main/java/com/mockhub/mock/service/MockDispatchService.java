package com.mockhub.mock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.exception.UnresolvedPlaceholderException;
import com.mockhub.common.model.Result;
import com.mockhub.log.service.LogService;
import com.mockhub.log.model.RequestLog;
import com.mockhub.mock.model.SoapConfig;
import com.mockhub.mock.model.SoapOperation;
import com.mockhub.mock.model.dto.ApiMatchResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.service.match.ResponseMatcher;
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
    private final ApiResponseRepository apiResponseRepository;
    private final GlobalHeaderService globalHeaderService;
    private final LogService logService;
    private final ObjectMapper objectMapper;
    private final DynamicVariableResolver dynamicVariableResolver;
    private final ResponseMatcher responseMatcher;
    private final SoapService soapService;

    public MockDispatchService(TeamService teamService,
                               ApiService apiService,
                               ApiResponseRepository apiResponseRepository,
                               GlobalHeaderService globalHeaderService,
                               LogService logService,
                               ObjectMapper objectMapper,
                               DynamicVariableResolver dynamicVariableResolver,
                               ResponseMatcher responseMatcher,
                               SoapService soapService) {
        this.teamService = teamService;
        this.apiService = apiService;
        this.apiResponseRepository = apiResponseRepository;
        this.globalHeaderService = globalHeaderService;
        this.logService = logService;
        this.objectMapper = objectMapper;
        this.dynamicVariableResolver = dynamicVariableResolver;
        this.responseMatcher = responseMatcher;
        this.soapService = soapService;
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

        // ====== 0. 预读请求体缓存到 attribute ======
        // 背景：HttpServletRequest 流只能读一次。v1.4.3 起 ResponseMatcher 需读 body 做条件匹配，
        // 日志写入也要 body。这里一次性读完缓存到 attribute，后续 matcher 和 log 都从 attribute 取。
        cacheRequestBody(request);

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

        // ====== 3. 获取活跃返回体 ======
        String contentType = request.getContentType();
        boolean isSoap = isSoapRequest(contentType);
        String responseBody;
        int responseCode;
        int delayMs;
        String respContentTypeFromResponse = null;

        if (isSoap && api.getSoapConfig() != null && !api.getSoapConfig().isEmpty()) {
            // SOAP 请求处理
            log.debug("检测到 SOAP 请求, Content-Type={}", contentType);

            // 从 SOAPAction 请求头匹配 operation
            String soapAction = request.getHeader("SOAPAction");
            if (soapAction != null) {
                soapAction = soapAction.replace("\"", "").trim();
            }
            log.debug("SOAPAction 头: {}", soapAction);

            SoapOperation matchedOp = matchSoapOperation(api.getSoapConfig(), soapAction);
            if (matchedOp != null) {
                log.info("SOAP operation 匹配成功: operationName={}, soapAction={}",
                        matchedOp.getOperationName(), matchedOp.getSoapAction());

                // 优先从 api_response 表查询活跃返回体
                ApiResponse soapResp = apiResponseRepository.findActiveByApiIdAndOperation(
                        api.getId(), matchedOp.getOperationName());
                if (soapResp != null) {
                    responseBody = soapResp.getResponseBody();
                    responseCode = soapResp.getResponseCode();
                    delayMs = soapResp.getDelayMs();
                    respContentTypeFromResponse = soapResp.getContentType();
                    log.debug("使用 api_response 表的 SOAP 活跃返回体: respId={}, name={}",
                            soapResp.getId(), soapResp.getName());
                } else {
                    // 兼容旧数据：从 operation 读取
                    responseBody = matchedOp.getResponseBody();
                    responseCode = matchedOp.getResponseCode();
                    delayMs = matchedOp.getDelayMs();
                    log.debug("使用 SoapOperation 旧数据回退");
                }
            } else {
                log.warn("未匹配到 SOAP operation: soapAction={}", soapAction);
                // 未匹配到 operation 时使用接口默认配置
                responseBody = api.getResponseBody();
                responseCode = api.getResponseCode();
                delayMs = api.getDelayMs();
            }
        } else {
            // REST 请求处理：v1.4.3 起走条件匹配引擎（启用数 == 1 时会短路等同旧单返回体行为）
            ApiResponse activeResponse = responseMatcher.match(api.getId(), request);
            if (activeResponse != null) {
                responseBody = activeResponse.getResponseBody();
                responseCode = activeResponse.getResponseCode();
                delayMs = activeResponse.getDelayMs();
                respContentTypeFromResponse = activeResponse.getContentType();
                log.debug("使用 api_response 表的 REST 匹配返回体: respId={}, name={}",
                        activeResponse.getId(), activeResponse.getName());
            } else {
                // 兼容旧数据：从 api_definition 读取
                responseBody = api.getResponseBody();
                responseCode = api.getResponseCode();
                delayMs = api.getDelayMs();
                log.debug("使用 api_definition 旧数据回退");
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
        // 使用 DynamicVariableResolver 编排内置变量、路径参数和自定义变量。
        // 当自定义变量的目标分组不存在或为空时会抛 UnresolvedPlaceholderException，
        // 在此捕获并返回 HTTP 500 + 统一 {code,msg,data} 错误响应。
        if (responseBody != null && !responseBody.isEmpty()) {
            try {
                responseBody = dynamicVariableResolver.resolve(responseBody, teamId, teamIdentifier, pathVariables);
                log.debug("动态变量替换完成, 响应体长度={}", responseBody.length());
            } catch (UnresolvedPlaceholderException ex) {
                log.warn("动态变量占位符无法解析: teamIdentifier={}, placeholder={}",
                        teamIdentifier, ex.getPlaceholder());
                return buildResultErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                        50101, ex.getMessage());
            }
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
        // 设置 Content-Type
        // SOAP 请求且未配置 respContentType 时按请求 SOAP 版本兜底（1.1 → text/xml, 1.2 → application/soap+xml）
        // 其他情况：respContentType 优先，默认 application/json
        String respContentType = respContentTypeFromResponse != null ? respContentTypeFromResponse : api.getContentType();
        if (respContentType == null || respContentType.isEmpty()) {
            if (isSoap) {
                respContentType = resolveSoapResponseContentType(contentType);
            } else {
                respContentType = "application/json; charset=UTF-8";
            }
        } else if (!respContentType.toLowerCase().contains("charset")) {
            respContentType = respContentType + "; charset=UTF-8";
        }
        httpHeaders.set(HttpHeaders.CONTENT_TYPE, respContentType);

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
     * 托管 WSDL 文件：GET /mock/{teamIdentifier}{path}?wsdl 命中此分支。
     * <p>
     * 流程：查团队 → 查 method=POST + apiType=SOAP 接口 → 取 soapConfig.wsdlFileName
     *   → 构造 mockUrl → 委托 SoapService.getWsdlContent 读盘并替换 location。
     *
     * @param teamIdentifier 团队短标识
     * @param path           接口路径（含前导 /）
     * @param request        原始请求（用于拼服务器地址）
     * @return WSDL XML 响应
     */
    public ResponseEntity<String> serveWsdl(String teamIdentifier, String path, HttpServletRequest request) {
        // 1. 查找团队
        Team team = teamService.findByIdentifier(teamIdentifier);
        if (team == null) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Team not found: " + teamIdentifier);
        }

        // 2. 查找接口：team + path + POST（SOAP 接口固定 POST）
        ApiMatchResult matchResult = apiService.findMatch(team.getId(), "POST", path);
        if (matchResult == null || matchResult.getApi() == null) {
            return buildErrorResponse(HttpStatus.NOT_FOUND,
                    "No SOAP mock found for " + path);
        }
        ApiDefinition api = matchResult.getApi();

        // 3. 必须是 SOAP 类型且已上传 WSDL
        if (!"SOAP".equalsIgnoreCase(api.getType())) {
            return buildErrorResponse(HttpStatus.NOT_FOUND,
                    "Not a SOAP interface: " + path);
        }
        String soapConfigJson = api.getSoapConfig();
        if (soapConfigJson == null || soapConfigJson.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND,
                    "No WSDL uploaded for " + path);
        }

        String wsdlFileName;
        try {
            SoapConfig cfg = objectMapper.readValue(soapConfigJson, SoapConfig.class);
            wsdlFileName = cfg.getWsdlFileName();
        } catch (Exception e) {
            log.error("解析 SoapConfig 失败: {}", e.getMessage(), e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "SOAP config parse error");
        }
        if (wsdlFileName == null || wsdlFileName.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND,
                    "No WSDL uploaded for " + path);
        }

        // 4. 构造 mockUrl
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        StringBuilder mockUrl = new StringBuilder();
        mockUrl.append(scheme).append("://").append(serverName);
        if (("http".equals(scheme) && serverPort != 80)
                || ("https".equals(scheme) && serverPort != 443)) {
            mockUrl.append(":").append(serverPort);
        }
        mockUrl.append("/mock/").append(teamIdentifier).append(path);

        // 5. 读取 WSDL 并替换 location
        String content = soapService.getWsdlContent(wsdlFileName, mockUrl.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/xml; charset=UTF-8");
        return new ResponseEntity<String>(content, headers, HttpStatus.OK);
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
     * 根据请求 Content-Type 推导 SOAP 响应 Content-Type。
     * <p>
     * SOAP 1.2（application/soap+xml）响应保持 1.2；其余都用 SOAP 1.1 的 text/xml 兜底。
     * 无论输入是否包含 charset，输出都统一追加 charset=UTF-8（避免中文乱码）。
     *
     * @param requestContentType 请求头 Content-Type
     * @return 响应应使用的 Content-Type（一定非空）
     */
    String resolveSoapResponseContentType(String requestContentType) {
        if (requestContentType != null
                && requestContentType.toLowerCase().contains("application/soap+xml")) {
            return "application/soap+xml; charset=UTF-8";
        }
        return "text/xml; charset=UTF-8";
    }

    /**
     * 从 SoapConfig JSON 中匹配指定 soapAction 对应的 operation
     * <p>
     * 采用两遍扫描策略，优先级由高到低：
     * <ol>
     *   <li>第一遍：全量精确匹配 soapAction 字段，命中即返回，不继续扫描</li>
     *   <li>第二遍：全量尾部匹配，要求 soapAction 以 {@code "/" + operationName} 结尾，
     *       避免 {@code XxxGetNavi} 误命中 {@code GetNavi}</li>
     *   <li>兜底：soapAction 为 null 且 operation 只有一个时，直接返回该 operation</li>
     * </ol>
     *
     * @param soapConfigJson 接口定义中存储的 SoapConfig JSON 字符串
     * @param soapAction     请求头中的 SOAPAction 值（已去除引号），可为 null
     * @return 匹配到的 SoapOperation，未匹配返回 null
     */
    SoapOperation matchSoapOperation(String soapConfigJson, String soapAction) {
        if (soapConfigJson == null || soapConfigJson.isEmpty()) {
            return null;
        }

        try {
            SoapConfig config = objectMapper.readValue(soapConfigJson, SoapConfig.class);
            if (config.getOperations() == null || config.getOperations().isEmpty()) {
                return null;
            }

            // 第一遍：全量精确 soapAction 匹配，优先级最高
            if (soapAction != null) {
                for (SoapOperation op : config.getOperations()) {
                    if (soapAction.equals(op.getSoapAction())) {
                        return op;
                    }
                }
            }

            // 第二遍：全量尾部 operationName 匹配（兜底）
            // 用 "/" + operationName 避免 GetNavi 被 XxxGetNavi 类 soapAction 误命中
            if (soapAction != null) {
                for (SoapOperation op : config.getOperations()) {
                    if (op.getOperationName() != null
                            && soapAction.endsWith("/" + op.getOperationName())) {
                        return op;
                    }
                }
            }

            // 单 operation 兜底：SOAPAction 为 null 时仍允许命中
            if (soapAction == null && config.getOperations().size() == 1) {
                return config.getOperations().get(0);
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

            // 收集请求体：v1.4.3 起从 dispatch 开始时缓存到 attribute 的 raw body 读取，
            // 避免 request reader 被 matcher / 自身读取后二次读空
            String cachedBody = (String) request.getAttribute(ResponseMatcher.ATTR_RAW_BODY);
            if (cachedBody == null) {
                reqLog.setRequestBody("");
            } else if (cachedBody.length() > 1024 * 1024) {
                reqLog.setRequestBody(cachedBody.substring(0, 1024 * 1024) + "...(truncated)");
            } else {
                reqLog.setRequestBody(cachedBody);
            }

            logService.asyncLogRequest(reqLog);
        } catch (Exception e) {
            // 日志写入失败不影响 Mock 响应
            log.error("写入请求日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 把请求体一次性读入内存并缓存到 request attribute，供后续 matcher 与日志记录复用。
     * <p>
     * HttpServletRequest 的 reader 只能读一次；v1.4.3 起条件匹配引擎和请求日志都要 body，
     * 统一在分发入口预读。读取失败时 attribute 保持 null，下游按"无 body"处理。
     * 限制读取大小到 10MB，超出截断并写 warn 日志（和响应体上限一致，防止超大请求拖垮内存）。
     */
    private void cacheRequestBody(HttpServletRequest request) {
        // 幂等：若已被其他前置过滤器缓存过（如 ContentCachingRequestWrapper），尊重已有值
        if (request.getAttribute(ResponseMatcher.ATTR_RAW_BODY) != null) {
            return;
        }
        try {
            BufferedReader reader = request.getReader();
            if (reader == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            int limit = 10 * 1024 * 1024;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > limit) {
                    log.warn("请求体超过 {}MB 上限，后续部分已丢弃", limit / 1024 / 1024);
                    break;
                }
            }
            request.setAttribute(ResponseMatcher.ATTR_RAW_BODY, sb.toString());
        } catch (Exception e) {
            // 流被上游过滤器消费、GET 请求无 body 等情况下正常走到这里
            log.debug("预读请求体失败（可能无 body 或已被上游消费）: {}", e.getMessage());
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
     * 构建统一 Result 格式的错误响应
     * <p>
     * Mock 系统自身抛出的错误（如动态变量占位符无法解析）使用与管理接口一致的
     * {@code {code, msg, data}} 格式，便于下游统一按 HTTP 状态码和 body.code 判断。
     * 用户配置的 Mock 响应体仍保持原样不包装（在正常流程里），此方法只服务于异常场景。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码（如 50101 代表未解析的占位符）
     * @param message 错误描述
     */
    private ResponseEntity<String> buildResultErrorResponse(HttpStatus status, int code, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body;
        try {
            body = objectMapper.writeValueAsString(Result.error(code, message));
        } catch (JsonProcessingException e) {
            // 极小概率：ObjectMapper 序列化一个简单 POJO 失败 → 降级到手写 JSON
            body = "{\"code\":" + code + ",\"msg\":\"" + escapeJson(message) + "\",\"data\":null}";
        }
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
