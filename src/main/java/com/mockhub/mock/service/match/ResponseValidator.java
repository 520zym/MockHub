package com.mockhub.mock.service.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.model.dto.match.MatchCondition;
import com.mockhub.mock.model.dto.match.MatchRule;
import com.mockhub.mock.model.entity.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多返回体 + 条件匹配的保存校验。
 * <p>
 * 适用于 ApiServiceImpl 保存接口（DTO 形式）和 ImportExportService 导入（Entity 形式）两个路径。
 *
 * <p>校验规则（按 REST 接口或 SOAP operation 独立分组）：
 * <ol>
 *   <li>启用项数 == 0 → 抛 BizException(40410)</li>
 *   <li>启用项数 &gt;= 2 且无"无规则"项 → 抛 BizException(40411)</li>
 *   <li>启用项数 &gt;= 2 且"无规则"项多于 1 个 → 抛 BizException(40412)</li>
 *   <li>每条规则的 conditions 合法性：
 *     <ul>
 *       <li>path 长度 &le; 500 → 否则 40413</li>
 *       <li>operator 在枚举内 → 否则 40414</li>
 *       <li>数字操作符要求 valueType=NUMBER → 否则 40415</li>
 *       <li>source 在 BODY/QUERY 内 → 否则 40416</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>SOAP 返回体（{@code soapOperationName != null}）按 operation 分组独立校验启用与兜底规则；
 * 运行时也按 operation 复用同一套条件匹配逻辑。
 */
public class ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidator.class);

    private static final int PATH_MAX_LENGTH = 500;

    /** 路径参数校验时使用的 ObjectMapper，线程安全可静态共享 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponseValidator() {
    }

    /**
     * DTO 路径校验入口，ApiServiceImpl.saveResponses 使用。
     *
     * @param responses 待保存的返回体 DTO 列表
     * @throws BizException 任何校验失败抛此异常；code 范围 40410~40416
     */
    public static void validateDtos(List<ApiResponseDTO> responses) {
        validateDtos(responses, null);
    }

    /**
     * 按返回体归属校验。modeByGroup 的 key 为 __REST__ 或 SOAP operation 名；缺省为 CONDITION，
     * 从而保持老客户端与导入包的行为。
     */
    public static void validateDtos(List<ApiResponseDTO> responses, Map<String, String> modeByGroup) {
        Map<String, List<DtoAdapter>> grouped = new HashMap<String, List<DtoAdapter>>();
        if (responses != null) {
            for (ApiResponseDTO dto : responses) {
                String key = dto.getSoapOperationName() == null ? "__REST__" : dto.getSoapOperationName();
                List<DtoAdapter> list = grouped.get(key);
                if (list == null) {
                    list = new ArrayList<DtoAdapter>();
                    grouped.put(key, list);
                }
                list.add(new DtoAdapter(dto));
            }
        }
        validateGroupedResponses(grouped, modeByGroup);
    }

    /**
     * Entity 路径校验入口，ImportExportService 导入时调用；按 apiId 分组后调用。
     *
     * @param responses 同一个接口的所有返回体 entity
     */
    public static void validateEntities(List<ApiResponse> responses) {
        validateEntities(responses, null);
    }

    /** 导入路径使用的实体校验入口。 */
    public static void validateEntities(List<ApiResponse> responses, Map<String, String> modeByGroup) {
        Map<String, List<DtoAdapter>> grouped = new HashMap<String, List<DtoAdapter>>();
        if (responses != null) {
            for (ApiResponse e : responses) {
                String key = e.getSoapOperationName() == null ? "__REST__" : e.getSoapOperationName();
                List<DtoAdapter> list = grouped.get(key);
                if (list == null) {
                    list = new ArrayList<DtoAdapter>();
                    grouped.put(key, list);
                }
                list.add(new DtoAdapter(e));
            }
        }
        validateGroupedResponses(grouped, modeByGroup);
    }

    private static void validateGroupedResponses(Map<String, List<DtoAdapter>> grouped,
                                                 Map<String, String> modeByGroup) {
        if (modeByGroup != null) {
            for (String group : modeByGroup.keySet()) {
                String mode = responseModeFor(group, modeByGroup);
                if ("RANDOM".equals(mode) && !grouped.containsKey(group)) {
                    throw new BizException(40410,
                            "随机返回体至少需要一个启用的返回体（分组 " + group + "）");
                }
            }
        }
        for (Map.Entry<String, List<DtoAdapter>> e : grouped.entrySet()) {
            validateGroup(e.getKey(), e.getValue(), responseModeFor(e.getKey(), modeByGroup));
        }
    }

    /**
     * 校验一组同归属（同 API 同 operation）的返回体。
     */
    private static void validateGroup(String group, List<DtoAdapter> items, String responseMode) {
        int enabled = 0;
        int enabledNoRule = 0;

        for (DtoAdapter item : items) {
            if (!item.enabled) {
                continue;
            }
            if ("FILE".equalsIgnoreCase(item.bodyType)
                    && (item.filePath == null || item.filePath.trim().isEmpty())) {
                throw new BizException(40417,
                        "文件返回体必须先上传文件（分组 " + group + "）");
            }
            enabled++;
            if ("RANDOM".equals(responseMode)) {
                // 随机模式不读取条件；保留原配置，用户切回条件模式时再按原规则校验。
                continue;
            }
            MatchRule rule = parseRule(item.conditions);
            boolean noRule = rule == null || rule.isEmpty();
            if (noRule) {
                enabledNoRule++;
            } else {
                validateRule(rule);
            }
        }

        if (enabled == 0) {
            throw new BizException(40410,
                    "至少需要一个启用的返回体（分组 " + group + "）");
        }

        if (enabled == 1 || "RANDOM".equals(responseMode)) {
            return; // 单启用不要求兜底
        }

        if (enabledNoRule == 0) {
            throw new BizException(40411,
                    "多启用返回体时必须有一个无规则的作为兜底（分组 " + group + "）");
        }
        if (enabledNoRule > 1) {
            throw new BizException(40412,
                    "只允许一个无规则的启用返回体作为兜底（分组 " + group + "，当前 "
                            + enabledNoRule + " 个）");
        }
    }

    private static String responseModeFor(String group, Map<String, String> modeByGroup) {
        String mode = modeByGroup == null ? null : modeByGroup.get(group);
        if (mode == null || mode.trim().isEmpty()) {
            return "CONDITION";
        }
        if ("CONDITION".equalsIgnoreCase(mode) || "RANDOM".equalsIgnoreCase(mode)) {
            return mode.toUpperCase();
        }
        throw new BizException(40418, "返回体选择模式仅支持 CONDITION 或 RANDOM：" + mode);
    }

    /**
     * 逐条条件校验。
     */
    private static void validateRule(MatchRule rule) {
        for (MatchCondition c : rule.getConditions()) {
            if (c == null) {
                throw new BizException(40414, "规则中存在空条件");
            }
            String path = c.getPath();
            if (path == null || path.isEmpty()) {
                throw new BizException(40413, "条件字段路径不能为空");
            }
            if (path.length() > PATH_MAX_LENGTH) {
                throw new BizException(40413,
                        "条件字段路径长度超过 " + PATH_MAX_LENGTH + " 字符：" + path.substring(0, 50) + "...");
            }
            String source = c.getSource();
            if (!"BODY".equalsIgnoreCase(source) && !"QUERY".equalsIgnoreCase(source)) {
                throw new BizException(40416, "条件参数来源必须为 BODY 或 QUERY：" + source);
            }
            if (!ResponseMatcher.Operators.isValid(c.getOperator())) {
                throw new BizException(40414, "未知操作符：" + c.getOperator());
            }
            if (ResponseMatcher.Operators.requiresNumber(c.getOperator())
                    && !"NUMBER".equalsIgnoreCase(c.getValueType())) {
                throw new BizException(40415,
                        "操作符 " + c.getOperator() + " 要求 valueType=NUMBER，当前=" + c.getValueType());
            }
        }
    }

    /**
     * 解析 conditions JSON。解析失败视为"无规则"——保存流程希望尽量让老数据无阻通过，
     * 但记录 warn 便于排障。
     */
    private static MatchRule parseRule(String conditionsJson) {
        if (conditionsJson == null || conditionsJson.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(conditionsJson, MatchRule.class);
        } catch (Exception e) {
            log.warn("保存校验：解析 conditions JSON 失败，视为无规则: {}", e.getMessage());
            return null;
        }
    }

    /**
     * DTO / Entity 的统一视图，消除双路径的 boilerplate。
     */
    private static final class DtoAdapter {
        final boolean enabled;
        final String conditions;
        final String bodyType;
        final String filePath;

        DtoAdapter(ApiResponseDTO dto) {
            this.enabled = dto.isActive();
            this.conditions = dto.getConditions();
            this.bodyType = dto.getBodyType();
            this.filePath = dto.getFilePath();
        }

        DtoAdapter(ApiResponse e) {
            this.enabled = e.isActive();
            this.conditions = e.getConditions();
            this.bodyType = e.getBodyType();
            this.filePath = e.getFilePath();
        }
    }
}
