package com.mockhub.mock.service.match;

import com.mockhub.common.model.BizException;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ResponseValidator 单元测试。专注 DTO 路径（Entity 路径逻辑等价，DtoAdapter 做的只是字段映射）。
 */
class ResponseValidatorTest {

    // ========== 启用数量约束 ==========

    @Test
    void allDisabledThrows40410() {
        List<ApiResponseDTO> list = Arrays.asList(dto(false, null), dto(false, null));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40410, ex.getCode());
    }

    @Test
    void nullOrEmptyListPassesSilently() {
        // ApiServiceImpl.saveResponses 在 responses==null 时 return 不调用校验；
        // 但直接给空列表也不应抛——未提供数据的场景由调用方决定
        assertDoesNotThrow(() -> ResponseValidator.validateDtos(null));
        assertDoesNotThrow(() -> ResponseValidator.validateDtos(Collections.<ApiResponseDTO>emptyList()));
    }

    @Test
    void singleEnabledNoFallbackRequired() {
        // 单启用不要求兜底规则
        assertDoesNotThrow(() -> ResponseValidator.validateDtos(
                Arrays.asList(dto(true, null))));
        assertDoesNotThrow(() -> ResponseValidator.validateDtos(
                Arrays.asList(dto(true, null), dto(false, null))));
    }

    @Test
    void multipleEnabledWithoutFallbackThrows40411() {
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, ruleJson("BODY", "a", "EQ", "1", "STRING")),
                dto(true, ruleJson("BODY", "b", "EQ", "2", "STRING")));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40411, ex.getCode());
    }

    @Test
    void multipleEnabledWithMultipleFallbacksThrows40412() {
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, null),
                dto(true, null),
                dto(true, ruleJson("BODY", "a", "EQ", "1", "STRING")));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40412, ex.getCode());
    }

    @Test
    void multipleEnabledWithExactlyOneFallbackPasses() {
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, ruleJson("BODY", "a", "EQ", "1", "STRING")),
                dto(true, null),
                dto(false, ruleJson("BODY", "c", "EQ", "3", "STRING")));
        assertDoesNotThrow(() -> ResponseValidator.validateDtos(list));
    }

    // ========== 条件合法性 ==========

    @Test
    void emptyPathThrows40413() {
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, ruleJson("BODY", "", "EQ", "1", "STRING")),
                dto(true, null));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40413, ex.getCode());
    }

    @Test
    void tooLongPathThrows40413() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            sb.append("a");
        }
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, ruleJson("BODY", sb.toString(), "EQ", "1", "STRING")),
                dto(true, null));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40413, ex.getCode());
    }

    @Test
    void unknownOperatorThrows40414() {
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, ruleJson("BODY", "a", "NOPE", "1", "STRING")),
                dto(true, null));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40414, ex.getCode());
    }

    @Test
    void numericOperatorWithoutNumberTypeThrows40415() {
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, ruleJson("BODY", "a", "GT", "10", "STRING")),
                dto(true, null));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40415, ex.getCode());
    }

    @Test
    void invalidSourceThrows40416() {
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, ruleJson("HEADER", "a", "EQ", "1", "STRING")),
                dto(true, null));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40416, ex.getCode());
    }

    @Test
    void validRulePasses() {
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, ruleJson("BODY", "user.id", "EQ", "1001", "NUMBER")),
                dto(true, ruleJson("QUERY", "action", "CONTAINS", "buy", "STRING")),
                dto(true, null));
        assertDoesNotThrow(() -> ResponseValidator.validateDtos(list));
    }

    // ========== SOAP 分组独立 ==========

    @Test
    void restAndSoapAreValidatedSeparately() {
        // REST 单启用合法；SOAP operation X 有 2 启用但无兜底 → 应失败
        List<ApiResponseDTO> list = new ArrayList<ApiResponseDTO>();
        list.add(dtoWithOperation(true, null, null));       // REST 启用
        list.add(dtoWithOperation(true,
                ruleJson("BODY", "a", "EQ", "1", "STRING"), "opX"));
        list.add(dtoWithOperation(true,
                ruleJson("BODY", "b", "EQ", "2", "STRING"), "opX"));

        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40411, ex.getCode());
    }

    @Test
    void corruptedConditionsJsonTreatedAsNoRule() {
        // 坏 JSON 视为无规则 → 不报错（日志 warn），但参与"无规则计数"
        // 这里两条启用+都被视为无规则 → 40412
        List<ApiResponseDTO> list = Arrays.asList(
                dto(true, "not-json"),
                dto(true, null));
        BizException ex = assertThrows(BizException.class, () -> ResponseValidator.validateDtos(list));
        assertEquals(40412, ex.getCode());
    }

    // ========== 辅助方法 ==========

    private static ApiResponseDTO dto(boolean active, String conditions) {
        return dtoWithOperation(active, conditions, null);
    }

    private static ApiResponseDTO dtoWithOperation(boolean active, String conditions, String operation) {
        ApiResponseDTO d = new ApiResponseDTO();
        d.setName("test");
        d.setResponseCode(200);
        d.setContentType("application/json");
        d.setResponseBody("{}");
        d.setActive(active);
        d.setConditions(conditions);
        d.setSoapOperationName(operation);
        return d;
    }

    private static String ruleJson(String source, String path, String operator, String value, String valueType) {
        return "{\"conditions\":[{\"source\":\"" + source + "\",\"path\":\"" + path
                + "\",\"operator\":\"" + operator + "\",\"value\":\"" + value
                + "\",\"valueType\":\"" + valueType + "\"}]}";
    }
}
