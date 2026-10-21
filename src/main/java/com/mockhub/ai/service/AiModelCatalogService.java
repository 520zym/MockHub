package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.common.model.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从服务商 /models 拉取模型列表。
 * <p>
 * 只使用服务端已保存的 Base URL 与 Key；Key 不返回前端、不写日志。
 * 服务商不支持 /models（404）或 Key 无效时，由前端回退到推荐列表或手动输入。
 */
@Service
public class AiModelCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AiModelCatalogService.class);
    private static final int MAX_MODELS = 300;

    private final AiSettingsService settingsService;
    private final ObjectMapper objectMapper;

    public AiModelCatalogService(AiSettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listModels() {
        AiSettings settings = settingsService.load();
        if (AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            List<Map<String, Object>> mock = new ArrayList<Map<String, Object>>();
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", "mock-demo");
            item.put("ownedBy", "mockhub");
            item.put("source", "builtin");
            mock.add(item);
            return mock;
        }
        if (!settingsService.isConfigured(settings)) {
            throw new BizException(40303, "请先保存服务地址、模型与 API Key");
        }
        String endpoint = modelsEndpoint(settings.getBaseUrl());
        if (endpoint == null) {
            throw new BizException(40303, "请先填写服务地址");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        String key = settings.getApiKey() == null ? null : settings.getApiKey().trim();
        if (key != null && !key.isEmpty()) {
            headers.set("Authorization", "Bearer " + key);
        }

        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        restTemplate.setRequestFactory(factory);

        long started = System.currentTimeMillis();
        log.info("拉取模型列表：endpoint={}, keyConfigured={}", endpoint, key != null && !key.isEmpty());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, new HttpEntity<String>(headers), String.class);
            List<Map<String, Object>> models = parseModels(response.getBody());
            log.info("拉取模型列表成功：count={}, durationMs={}", models.size(), System.currentTimeMillis() - started);
            return models;
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            log.warn("拉取模型列表失败：status={}, endpoint={}, durationMs={}",
                    status, endpoint, System.currentTimeMillis() - started);
            if (status == 401 || status == 403) {
                throw new BizException(40303, "API Key 无效或无权限（" + status + "），请检查后重试");
            }
            if (status == 404) {
                throw new BizException(40401, "该服务商不支持 /models 接口，请手动输入模型 ID");
            }
            throw new BizException(50201, "获取模型列表失败，服务商返回 " + status);
        } catch (ResourceAccessException e) {
            log.warn("拉取模型列表超时：endpoint={}, durationMs={}, error={}",
                    endpoint, System.currentTimeMillis() - started, e.getMessage());
            throw new BizException(50202, "连接服务商超时，请检查网络或手动输入模型 ID");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            throw new BizException(50203, "获取模型列表失败：" + e.getMessage());
        }
    }

    /**
     * 解析 OpenAI 兼容的 /models 响应；兼容 data / models 两种字段。
     */
    List<Map<String, Object>> parseModels(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body == null ? "" : body);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            data = root.path("models");
        }
        if (!data.isArray()) {
            throw new BizException(50201, "服务商返回的模型列表格式无法识别");
        }
        Map<String, Map<String, Object>> unique = new LinkedHashMap<String, Map<String, Object>>();
        for (JsonNode node : data) {
            String id = node.path("id").asText("").trim();
            if (id.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", id);
            item.put("ownedBy", node.path("owned_by").asText(""));
            if (node.has("context_length") && node.get("context_length").isNumber()) {
                item.put("contextLength", node.get("context_length").asLong());
            }
            if (node.has("supports_image_in")) {
                item.put("supportsImage", node.get("supports_image_in").asBoolean());
            }
            if (node.has("supports_reasoning")) {
                item.put("supportsReasoning", node.get("supports_reasoning").asBoolean());
            }
            item.put("source", "provider");
            unique.put(id, item);
            if (unique.size() >= MAX_MODELS) {
                break;
            }
        }
        return new ArrayList<Map<String, Object>>(unique.values());
    }

    private String modelsEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return null;
        }
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            url = url.substring(0, url.length() - "/chat/completions".length());
        }
        if (url.endsWith("/models")) {
            return url;
        }
        return url + "/models";
    }
}