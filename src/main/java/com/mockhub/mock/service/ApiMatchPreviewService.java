package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.dto.ApiMatchPreviewRequest;
import com.mockhub.mock.model.dto.ApiMatchPreviewResult;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.model.dto.match.MatchRule;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.mock.service.match.ResponseValidator;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.repository.TeamRepository;
import org.springframework.stereotype.Service;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Read-only selection. Deliberately has no dispatch, file, history, log or mutation dependencies. */
@Service
public class ApiMatchPreviewService {
    private final PermissionChecker permissions;
    private final TeamRepository teams;
    private final ApiRepository apis;
    private final ApiResponseRepository responses;
    private final ResponseMatcher matcher;
    private final ObjectMapper mapper;

    public ApiMatchPreviewService(PermissionChecker permissions, TeamRepository teams,
                                 ApiRepository apis, ApiResponseRepository responses,
                                 ResponseMatcher matcher, ObjectMapper mapper) {
        this.permissions = permissions;
        this.teams = teams;
        this.apis = apis;
        this.responses = responses;
        this.matcher = matcher;
        this.mapper = mapper;
    }

    public ApiMatchPreviewResult preview(ApiMatchPreviewRequest request, HttpServletRequest httpRequest) {
        if (request == null || blank(request.getTeamId())) throw invalid("teamId 必填");
        bounded(request.getTeamId(), 128, "teamId 过长");
        permissions.checkTeamAccess(request.getTeamId());
        Team team = teams.findById(request.getTeamId());
        if (team == null) throw new BizException(40403, "团队不存在");
        bounded(request.getApiId(), 128, "apiId 过长");
        bounded(request.getOperationName(), 256, "operationName 过长");
        ApiDefinition api = null;
        if (!blank(request.getApiId())) {
            // Normal lookup excludes trash. Do not use lifecycle findIncludingDeleted.
            api = apis.findById(request.getApiId());
            if (api == null) throw new BizException(40401, "接口不存在");
            if (!request.getTeamId().equals(api.getTeamId())) {
                throw new BizException(40102, "接口不属于当前团队");
            }
        }
        boolean writable = true;
        try {
            permissions.checkTeamWrite(request.getTeamId());
        } catch (BizException e) {
            // Never turn DB failures, missing memberships, unknown roles or missing teams into read access.
            if (e.getCode() != 40104 && e.getCode() != 40903) throw e;
            writable = false;
        }
        boolean saved = !writable || team.isArchived();
        if (saved && api == null) throw new BizException(40104, "只读试配必须指定已有接口");
        String operation = blank(request.getOperationName()) ? null : request.getOperationName();
        String mode = saved ? savedMode(api, operation) : mode(request.getResponseMode(), false);
        ApiMatchPreviewRequest.Sample sample = request.getSample() == null
                ? new ApiMatchPreviewRequest.Sample() : request.getSample();
        validateSample(sample);
        List<Entry> entries = saved ? savedEntries(api, operation) : draftEntries(request, operation);
        List<Entry> enabled = new ArrayList<Entry>();
        for (Entry entry : entries) {
            entry.noRule = validateConditions(entry.response, mode);
            if (entry.response.isActive()) enabled.add(entry);
        }
        // Stable ordering preserves draft index as the tie-breaker, just as existing tab order does.
        enabled.sort(Comparator.comparingInt(e -> e.response.getSortOrder()));
        ApiMatchPreviewResult result = new ApiMatchPreviewResult();
        result.setMode(mode);
        result.setUsingSavedConfiguration(saved);
        result.setEnabledCount(enabled.size());
        if (enabled.isEmpty()) {
            result.setReason("NO_ENABLED_RESPONSE");
            return result;
        }
        if ("RANDOM".equals(mode)) {
            // The production matcher chooses uniformly from exactly these enabled entries.
            // Do not suggest a deterministic hit or evaluate conditions that RANDOM ignores.
            for (Entry entry : enabled) result.getCandidates().add(candidate(entry));
            result.setReason("RANDOM_EQUAL_PROBABILITY");
            return result;
        }
        List<ApiResponse> enabledResponses = new ArrayList<ApiResponse>();
        for (Entry entry : enabled) enabledResponses.add(entry.response);
        ApiResponse selected = matcher.previewEnabled(enabledResponses, mode, new SampleRequest(httpRequest, sample));
        if (selected == null) {
            result.setReason("NO_MATCH");
            return result;
        }
        for (Entry entry : enabled) {
            if (entry.response != selected) continue;
            result.setMatchedResponseId(selected.getId());
            result.setMatchedResponseName(selected.getName());
            result.setMatchedClientKey(entry.clientKey);
            result.setMatchedIndex(entry.index);
            result.setResponseCode(selected.getResponseCode());
            result.setBodyType(bodyType(selected));
            result.setReason(enabled.size() == 1 ? "SINGLE_ENABLED" : entry.noRule ? "FALLBACK" : "CONDITION_MATCH");
            return result;
        }
        throw new IllegalStateException("Matcher returned a response outside the supplied preview configuration");
    }

    private List<Entry> draftEntries(ApiMatchPreviewRequest request, String operation) {
        List<ApiMatchPreviewRequest.Response> drafts = request.getResponses();
        if (drafts == null) throw invalid("responses 必填");
        if (drafts.size() > 50) throw invalid("responses 最多 50 条");
        List<Entry> result = new ArrayList<Entry>();
        for (int i = 0; i < drafts.size(); i++) {
            ApiMatchPreviewRequest.Response dto = drafts.get(i);
            if (dto == null) throw invalid("responses 不能包含空项");
            bounded(dto.getId(), 128, "response id 过长");
            bounded(dto.getClientKey(), 256, "clientKey 过长");
            bounded(dto.getName(), 500, "response name 过长");
            if (!blank(dto.getBodyType()) && !"TEXT".equalsIgnoreCase(dto.getBodyType())
                    && !"FILE".equalsIgnoreCase(dto.getBodyType())) throw invalid("bodyType 仅支持 TEXT 或 FILE");
            bounded(dto.getSoapOperationName(), 256, "soapOperationName 过长");
            // Still reject malformed rules in disabled/other-operation entries, never silently truncate.
            ApiResponse response = new ApiResponse();
            response.setId(dto.getId());
            response.setName(dto.getName());
            response.setResponseCode(dto.getResponseCode());
            response.setBodyType(dto.getBodyType());
            response.setActive(dto.isActive());
            response.setSortOrder(dto.getSortOrder());
            response.setConditions(dto.getConditions());
            validateConditions(response, "RANDOM");
            if (Objects.equals(operation, dto.getSoapOperationName())) {
                result.add(new Entry(response, i, dto.getClientKey()));
            }
            // Never copy responseBody, filePath, attachments or delay into execution.
        }
        return result;
    }

    private List<Entry> savedEntries(ApiDefinition api, String operation) {
        List<ApiResponse> stored = operation == null ? responses.findByApiId(api.getId())
                : responses.findByApiIdAndOperation(api.getId(), operation);
        List<Entry> result = new ArrayList<Entry>();
        for (ApiResponse response : stored) {
            if (Objects.equals(operation, response.getSoapOperationName())) {
                result.add(new Entry(response, result.size(), null));
            }
        }
        if (result.size() > 50) throw invalid("responses 最多 50 条");
        return result;
    }

    private String savedMode(ApiDefinition api, String operation) {
        if ("SOAP".equalsIgnoreCase(api.getType())) {
            if (operation == null) throw invalid("SOAP 试配必须指定 operationName");
            try {
                JsonNode operations = mapper.readTree(api.getSoapConfig()).path("operations");
                for (JsonNode item : operations) {
                    if (operation.equals(item.path("operationName").asText())) {
                        return mode(item.path("responseMode").asText(null), true);
                    }
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw invalid("保存的 SOAP 配置无效");
            }
            throw invalid("SOAP operation 不存在");
        }
        if (operation != null) throw invalid("REST 试配不支持 operationName");
        return mode(api.getResponseMode(), true);
    }

    private String mode(String value, boolean allowDefault) {
        if (allowDefault && blank(value)) return "CONDITION";
        if (!"CONDITION".equalsIgnoreCase(value) && !"RANDOM".equalsIgnoreCase(value)) {
            throw invalid("responseMode 仅支持 CONDITION 或 RANDOM");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private boolean validateConditions(ApiResponse response, String mode) {
        String json = response.getConditions();
        if (json == null || json.isEmpty()) return true;
        bounded(json, 128 * 1024, "conditions 过长");
        MatchRule rule;
        try {
            // Unlike the legacy save validator, preview must not treat malformed JSON as fallback.
            rule = mapper.readerFor(MatchRule.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
            if (rule == null) throw invalid("conditions 必须为规则 JSON 对象");
        } catch (Exception e) {
            throw invalid("conditions JSON 无效");
        }
        if (rule.getConditions() != null && rule.getConditions().size() > 100) {
            throw invalid("每条 response 最多 100 个条件");
        }
        if (!rule.isEmpty() && response.isActive() && "CONDITION".equals(mode)) {
            // Validate rules in isolation, not the save-only enabled/fallback/file invariants:
            // an unsaved editor may legitimately ask why an incomplete draft has no match.
            ApiResponseDTO validation = new ApiResponseDTO();
            validation.setActive(true);
            validation.setBodyType("TEXT");
            validation.setConditions(json);
            try {
                ResponseValidator.validateDtos(Collections.singletonList(validation));
            } catch (BizException e) {
                throw invalid("conditions 条件字段无效");
            }
        }
        return rule.isEmpty();
    }

    private void validateSample(ApiMatchPreviewRequest.Sample sample) {
        String body = sample.getBody();
        if (body != null && (body.length() > 1024 * 1024
                || body.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024)) {
            throw invalid("sample body 最多 1 MiB（UTF-8）");
        }
        bounded(sample.getContentType(), 256, "contentType 过长");
        validateMap(sample.getHeaders(), true);
        validateMap(sample.getQuery(), false);
    }

    private void validateMap(Map<String, String> values, boolean headers) {
        if (values == null) return;
        if (values.size() > 100) throw invalid("sample header/query 最多各 100 项");
        int bytes = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (blank(key) || key.length() > 256 || value == null || value.length() > 8192) {
                throw invalid("sample header/query 键值无效或过长");
            }
            if (headers && (!key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
                throw invalid("sample header 键值无效");
            }
            bytes += key.getBytes(StandardCharsets.UTF_8).length + value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 64 * 1024) throw invalid("sample header/query 最多各 64 KiB");
        }
    }

    private static ApiMatchPreviewResult.Candidate candidate(Entry entry) {
        ApiResponse response = entry.response;
        return new ApiMatchPreviewResult.Candidate(response.getId(), response.getName(), entry.clientKey,
                entry.index, response.getResponseCode(), bodyType(response));
    }
    private static String bodyType(ApiResponse response) {
        return blank(response.getBodyType()) ? "TEXT" : response.getBodyType();
    }
    private static void bounded(String value, int max, String message) {
        if (value != null && value.length() > max) throw invalid(message);
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static BizException invalid(String message) { return new BizException(40001, message); }

    private static class Entry {
        final ApiResponse response;
        final int index;
        final String clientKey;
        boolean noRule;
        Entry(ApiResponse response, int index, String clientKey) {
            this.response = response; this.index = index; this.clientKey = clientKey;
        }
    }

    /** Sample-only view. In particular, JWT headers, cookies and parser caches never reach matching. */
    static final class SampleRequest extends HttpServletRequestWrapper {
        private final Map<String, Object> attributes = new HashMap<String, Object>();
        private final Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        private final Map<String, String> query = new HashMap<String, String>();
        private final byte[] body;
        private final String contentType;
        SampleRequest(HttpServletRequest original, ApiMatchPreviewRequest.Sample sample) {
            super(original);
            if (sample.getHeaders() != null) {
                for (Map.Entry<String, String> entry : sample.getHeaders().entrySet()) {
                    if (!headers.containsKey(entry.getKey())) headers.put(entry.getKey(), entry.getValue());
                }
            }
            if (sample.getQuery() != null) query.putAll(sample.getQuery());
            String raw = sample.getBody() == null ? "" : sample.getBody();
            attributes.put(ResponseMatcher.ATTR_RAW_BODY, raw);
            body = raw.getBytes(StandardCharsets.UTF_8);
            contentType = sample.getContentType() == null ? headers.get("Content-Type") : sample.getContentType();
            if (contentType != null) headers.put("Content-Type", contentType);
        }
        @Override public Object getAttribute(String name) { return attributes.get(name); }
        @Override public Enumeration<String> getAttributeNames() { return Collections.enumeration(attributes.keySet()); }
        @Override public void setAttribute(String name, Object value) {
            if (value == null) attributes.remove(name); else attributes.put(name, value);
        }
        @Override public void removeAttribute(String name) { attributes.remove(name); }
        @Override public String getHeader(String name) { return headers.get(name); }
        @Override public Enumeration<String> getHeaders(String name) {
            String value = getHeader(name);
            return Collections.enumeration(value == null ? Collections.<String>emptyList() : Collections.singletonList(value));
        }
        @Override public Enumeration<String> getHeaderNames() { return Collections.enumeration(headers.keySet()); }
        @Override public int getIntHeader(String name) { return getHeader(name) == null ? -1 : Integer.parseInt(getHeader(name)); }
        @Override public long getDateHeader(String name) {
            String value = getHeader(name);
            if (value == null) return -1;
            org.springframework.http.HttpHeaders parsed = new org.springframework.http.HttpHeaders();
            parsed.set(name, value);
            return parsed.getFirstDate(name);
        }
        @Override public String getParameter(String name) { return query.get(name); }
        @Override public String[] getParameterValues(String name) {
            return query.containsKey(name) ? new String[]{query.get(name)} : null;
        }
        @Override public Enumeration<String> getParameterNames() { return Collections.enumeration(query.keySet()); }
        @Override public Map<String, String[]> getParameterMap() {
            Map<String, String[]> result = new HashMap<String, String[]>();
            for (Map.Entry<String, String> entry : query.entrySet()) result.put(entry.getKey(), new String[]{entry.getValue()});
            return Collections.unmodifiableMap(result);
        }
        @Override public String getQueryString() { return null; }
        @Override public String getContentType() { return contentType; }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8)); }
        @Override public ServletInputStream getInputStream() {
            final ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous sample only"); }
            };
        }
        @Override public Cookie[] getCookies() { return null; }
        @Override public String getAuthType() { return null; }
        @Override public String getRemoteUser() { return null; }
        @Override public Principal getUserPrincipal() { return null; }
        @Override public boolean isUserInRole(String role) { return false; }
        @Override public HttpSession getSession() { return null; }
        @Override public HttpSession getSession(boolean create) { return null; }
    }
}
