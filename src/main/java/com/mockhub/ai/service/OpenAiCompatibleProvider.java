package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.common.model.BizException;
import org.slf4j.Logger;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * OpenAI Chat Completions 兼容实现。
 * <p>
 * 支持 OpenAI、DeepSeek、Ollama(/v1) 等兼容 /chat/completions 的服务。
 * 只读取 choices[0].message.content，不依赖供应商私有字段。
 */
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final ObjectMapper objectMapper;

    public OpenAiCompatibleProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(AiSettings settings, String system, String user) {
        String endpoint = resolveEndpoint(settings.getBaseUrl());
        if (endpoint == null) {
            throw new BizException(40303, "AI 服务尚未配置 Base URL");
        }
        boolean local = "ollama".equalsIgnoreCase(settings.getVendor());
        String apiKey = settings.getApiKey() == null ? null : settings.getApiKey().trim();
        if (!local && (apiKey == null || apiKey.isEmpty())) {
            throw new BizException(40303, "AI 服务尚未配置 API Key");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", settings.getModel());
        body.put("temperature", 0.3);
        body.put("max_tokens", 1800);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + apiKey);
        }

        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(90000);
        restTemplate.setRequestFactory(factory);

        long started = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.info("AI 请求开始：requestId={}, model={}, endpoint={}, systemChars={}, userChars={}, maxTokens=1800",
                requestId, settings.getModel(), endpoint, system == null ? 0 : system.length(),
                user == null ? 0 : user.length());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST,
                    new HttpEntity<String>(body.toString(), headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                throw new BizException(50201, "模型返回为空，请检查模型名称与服务地址");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.trim().isEmpty()) {
                throw new BizException(50201, "模型返回为空内容");
            }
            JsonNode usage = root.path("usage");
            log.info("AI 请求完成：requestId={}, model={}, status={}, durationMs={}, responseChars={}, promptTokens={}, completionTokens={}, totalTokens={}",
                    requestId, settings.getModel(), response.getStatusCodeValue(),
                    System.currentTimeMillis() - started, content.length(),
                    usage.path("prompt_tokens").asLong(-1), usage.path("completion_tokens").asLong(-1),
                    usage.path("total_tokens").asLong(-1));
            return content;
        } catch (HttpStatusCodeException e) {
            String detail = e.getResponseBodyAsString();
            if (detail != null && detail.length() > 300) {
                detail = detail.substring(0, 300) + "…";
            }
            log.warn("AI 请求失败：requestId={}, model={}, status={}, durationMs={}, response={}",
                    requestId, settings.getModel(), e.getStatusCode().value(),
                    System.currentTimeMillis() - started, detail);
            throw new BizException(50201, "AI 服务返回 " + e.getStatusCode().value()
                    + (detail != null && !detail.isEmpty() ? "：" + detail : ""));
        } catch (ResourceAccessException e) {
            log.warn("AI 请求连接失败：requestId={}, model={}, durationMs={}, error={}",
                    requestId, settings.getModel(), System.currentTimeMillis() - started, e.getMessage());
            throw new BizException(50202, "连接 AI 服务超时或失败，请检查 Base URL 与网络");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 请求异常：requestId={}, model={}, durationMs={}",
                    requestId, settings.getModel(), System.currentTimeMillis() - started, e);
            throw new BizException(50203, "调用 AI 服务失败：" + e.getMessage());
        }
    }

    /**
     * 拼接 /chat/completions：允许用户直接粘贴完整地址，也允许只填服务根地址。
     */
    private String resolveEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return null;
        }
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        return url + "/chat/completions";
    }
}