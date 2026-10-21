package com.mockhub.ai.service;

import com.mockhub.ai.model.AiProviderPreset;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiSettingsDTO;
import com.mockhub.ai.model.dto.AiSettingsUpdateRequest;
import com.mockhub.ai.model.dto.AiStatusDTO;
import com.mockhub.ai.repository.AiSettingsRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 配置与访问控制。
 * <p>
 * 访问控制顺序：启用 → 使用范围 → 模型配置 → 能力开关 → 团队权限。
 * 前端只拿到掩码 Key 与可用能力，不接触明文。
 */
@Service
public class AiSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AiSettingsService.class);

    public static final String CAP_RESPONSE = "responseBody";
    public static final String CAP_CONDITIONS = "conditions";
    public static final String CAP_EXPLAIN = "explain";
    public static final String CAP_DRAFTS = "drafts";

    private final AiSettingsRepository repository;
    private final OpenAiCompatibleProvider openAiProvider;
    private final PermissionChecker permissionChecker;
    private final AiKeyCipher keyCipher;

    public AiSettingsService(AiSettingsRepository repository,
                             OpenAiCompatibleProvider openAiProvider,
                             PermissionChecker permissionChecker,
                             AiKeyCipher keyCipher) {
        this.repository = repository;
        this.openAiProvider = openAiProvider;
        this.permissionChecker = permissionChecker;
        this.keyCipher = keyCipher;
    }

    /** 读取原始配置并解密 Key；不存在时返回默认值（不改库）。 */
    public AiSettings load() {
        AiSettings settings = repository.load();
        if (settings == null) {
            settings = new AiSettings();
        }
        if (settings.getProvider() == null) {
            settings.setProvider(AiSettings.PROVIDER_OPENAI);
        }
        if (settings.getVendor() == null || settings.getVendor().trim().isEmpty()) {
            settings.setVendor(AiProviderPresets.infer(settings.getBaseUrl()));
        }
        if (settings.getAccessScope() == null) {
            settings.setAccessScope(AiSettings.SCOPE_ALL);
        }
        if (settings.getApiKey() != null && !settings.getApiKey().trim().isEmpty()) {
            try {
                settings.setApiKey(keyCipher.decrypt(settings.getApiKey()));
            } catch (Exception e) {
                log.warn("AI API Key 解密失败：{}", e.getMessage());
                settings.setApiKey(null);
                settings.setKeyInvalid(true);
            }
        }
        return settings;
    }

    /** 设置页读取：Key 只返回掩码。 */
    public AiSettingsDTO getDto() {
        return toDto(load());
    }

    /** 保存配置；apiKey=null 保持原值，空串清除。 */
    public AiSettingsDTO update(AiSettingsUpdateRequest request) {
        AiSettings current = load();
        if (request.getProvider() != null) {
            String provider = request.getProvider().trim().toUpperCase();
            if (!AiSettings.PROVIDER_OPENAI.equals(provider) && !AiSettings.PROVIDER_MOCK.equals(provider)) {
                throw new BizException(40001, "不支持的 AI Provider");
            }
            current.setProvider(provider);
        }
        if (request.getAccessScope() != null) {
            String scope = request.getAccessScope().trim().toUpperCase();
            if (!AiSettings.SCOPE_ALL.equals(scope) && !AiSettings.SCOPE_ADMIN.equals(scope)) {
                throw new BizException(40001, "不支持的使用范围");
            }
            current.setAccessScope(scope);
        }
        current.setEnabled(request.isEnabled());
        current.setBaseUrl(trimToNull(request.getBaseUrl()));
        current.setModel(trimToNull(request.getModel()));
        if (request.getVendor() != null) {
            String vendor = AiProviderPresets.normalize(request.getVendor());
            current.setVendor(vendor);
            AiProviderPreset preset = AiProviderPresets.get(vendor);
            if (preset != null && !AiProviderPresets.CUSTOM.equals(vendor)) {
                if (!notBlank(current.getBaseUrl())) {
                    current.setBaseUrl(preset.getBaseUrl());
                }
                if (!notBlank(current.getModel())) {
                    current.setModel(preset.getDefaultModel());
                }
            }
        }
        if (request.getApiKey() != null) {
            String key = request.getApiKey().trim();
            current.setApiKey(key.isEmpty() ? null : keyCipher.encrypt(key));
        } else if (current.getApiKey() != null && !isEncryptedValue(current.getApiKey())) {
            // 兜底：旧明文数据在保存时一并加密
            current.setApiKey(keyCipher.encrypt(current.getApiKey()));
        }
        current.setKeyInvalid(false);
        current.setCapResponseBody(request.isCapResponseBody());
        current.setCapConditions(request.isCapConditions());
        current.setCapExplain(request.isCapExplain());
        current.setCapDrafts(request.isCapDrafts());
        current.setUpdatedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        current.setUpdatedBy(SecurityContextUtil.getCurrentUsername());
        repository.save(current);
        return toDto(load());
    }

    /** 设置页连通性测试，仅超管可调用。 */
    public Map<String, Object> testConnection() {
        AiSettings settings = load();
        if (!isConfigured(settings)) {
            throw new BizException(40303, "请先填写 Base URL、Model 与 API Key");
        }
        long started = System.currentTimeMillis();
        String provider = settings.getProvider();
        if (AiSettings.PROVIDER_MOCK.equals(provider)) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("ok", true);
            result.put("latencyMs", 0);
            result.put("provider", provider);
            result.put("model", "内置演示模型");
            result.put("message", "演示模式无需联网，生成结果由本地模板产出");
            return result;
        }
        String reply = openAiProvider.complete(settings,
                "You are a connectivity probe. Reply with the single word OK.",
                "ping");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("latencyMs", System.currentTimeMillis() - started);
        result.put("provider", provider);
        result.put("model", settings.getModel());
        result.put("message", reply.length() > 120 ? reply.substring(0, 120) : reply);
        return result;
    }

    /**
     * 当前用户可见状态：不可用时给出可读原因，前端据此降级。
     */
    public AiStatusDTO status() {
        AiSettings settings = load();
        AiStatusDTO dto = new AiStatusDTO();
        dto.setEnabled(settings.isEnabled());
        dto.setProvider(settings.getProvider());
        dto.setModel(settings.getModel());

        boolean scopeAllowed = !isAdminOnly(settings) || SecurityContextUtil.isSuperAdmin();
        boolean visible = settings.isEnabled() && scopeAllowed;
        String reason = null;
        if (!settings.isEnabled()) {
            reason = "AI 助手未启用";
        } else if (!scopeAllowed) {
            reason = "仅超级管理员可使用 AI 助手";
        } else if (settings.isKeyInvalid()) {
            reason = "API Key 无法解密，请重新配置";
        } else if (!isConfigured(settings)) {
            reason = "模型服务尚未配置";
        }

        boolean available = visible && reason == null;
        dto.setVisible(visible);
        dto.setAvailable(available);
        dto.setReason(reason);
        Map<String, Boolean> caps = new LinkedHashMap<String, Boolean>();
        caps.put(CAP_RESPONSE, available && settings.isCapResponseBody());
        caps.put(CAP_CONDITIONS, available && settings.isCapConditions());
        caps.put(CAP_EXPLAIN, available && settings.isCapExplain());
        caps.put(CAP_DRAFTS, available && settings.isCapDrafts());
        dto.setCapabilities(caps);
        return dto;
    }

    /**
     * 业务入口的统一守卫，返回已校验的配置。
     */
    public AiSettings assertUsable(String capability, String teamId) {
        AiSettings settings = load();
        if (!settings.isEnabled()) {
            throw new BizException(40301, "AI 助手未启用");
        }
        if (isAdminOnly(settings) && !SecurityContextUtil.isSuperAdmin()) {
            throw new BizException(40302, "仅超级管理员可使用 AI 助手");
        }
        if (settings.isKeyInvalid()) {
            throw new BizException(40303, "API Key 无法解密，请重新配置");
        }
        if (!isConfigured(settings)) {
            throw new BizException(40303, "模型服务尚未配置，请联系超级管理员");
        }
        if (capability != null && !isCapabilityEnabled(settings, capability)) {
            throw new BizException(40304, "该 AI 能力已被管理员关闭");
        }
        if (teamId != null && !teamId.trim().isEmpty()) {
            permissionChecker.checkTeamAccess(teamId);
        }
        return settings;
    }

    public boolean isConfigured(AiSettings settings) {
        if (settings.isKeyInvalid()) {
            return false;
        }
        if (AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            return true;
        }
        boolean keyRequired = !"ollama".equalsIgnoreCase(settings.getVendor());
        return notBlank(settings.getBaseUrl())
                && notBlank(settings.getModel())
                && (!keyRequired || notBlank(settings.getApiKey()));
    }

    private boolean isCapabilityEnabled(AiSettings settings, String capability) {
        if (CAP_RESPONSE.equals(capability)) return settings.isCapResponseBody();
        if (CAP_CONDITIONS.equals(capability)) return settings.isCapConditions();
        if (CAP_EXPLAIN.equals(capability)) return settings.isCapExplain();
        if (CAP_DRAFTS.equals(capability)) return settings.isCapDrafts();
        return false;
    }

    private boolean isAdminOnly(AiSettings settings) {
        return AiSettings.SCOPE_ADMIN.equalsIgnoreCase(settings.getAccessScope());
    }

    private boolean isEncryptedValue(String value) {
        return value != null && value.startsWith(AiKeyCipher.PREFIX);
    }

    private AiSettingsDTO toDto(AiSettings settings) {
        AiSettingsDTO dto = new AiSettingsDTO();
        dto.setEnabled(settings.isEnabled());
        dto.setProvider(settings.getProvider());
        dto.setVendor(settings.getVendor());
        dto.setBaseUrl(settings.getBaseUrl());
        dto.setModel(settings.getModel());
        boolean configured = notBlank(settings.getApiKey());
        dto.setKeyConfigured(configured);
        dto.setKeyMask(configured ? mask(settings.getApiKey()) : null);
        dto.setCapResponseBody(settings.isCapResponseBody());
        dto.setCapConditions(settings.isCapConditions());
        dto.setCapExplain(settings.isCapExplain());
        dto.setCapDrafts(settings.isCapDrafts());
        dto.setAccessScope(settings.getAccessScope());
        dto.setUpdatedAt(settings.getUpdatedAt());
        return dto;
    }

    /** 掩码规则：保留前 3 位与后 4 位；过短的 Key 全部遮蔽。 */
    private String mask(String key) {
        String trimmed = key.trim();
        if (trimmed.length() <= 8) {
            return "••••••";
        }
        return trimmed.substring(0, 3) + "••••••••" + trimmed.substring(trimmed.length() - 4);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}