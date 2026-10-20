package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.dto.match.MatchRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 诊断用的只读条件评估，语义必须与运行时匹配器一致。
 */
class AiMatchEvaluatorTest {

    private AiMatchEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AiMatchEvaluator(new ObjectMapper());
    }

    @Test
    void bodyEqMatches() {
        MatchRule rule = evaluator.parseRule("{\"conditions\":[{\"source\":\"BODY\",\"path\":\"userType\",\"operator\":\"EQ\",\"value\":\"vip\",\"valueType\":\"STRING\"}]}");
        assertNull(evaluator.evaluate(rule, "{\"userType\":\"vip\"}", "{}"));
    }

    @Test
    void bodyEqMissesWithReason() {
        MatchRule rule = evaluator.parseRule("{\"conditions\":[{\"source\":\"BODY\",\"path\":\"userType\",\"operator\":\"EQ\",\"value\":\"vip\",\"valueType\":\"STRING\"}]}");
        String reason = evaluator.evaluate(rule, "{\"userType\":\"normal\"}", "{}");
        assertNotNull(reason);
        assertTrue(reason.contains("userType"));
    }

    @Test
    void queryNumberCompare() {
        MatchRule rule = evaluator.parseRule("{\"conditions\":[{\"source\":\"QUERY\",\"path\":\"amount\",\"operator\":\"GT\",\"value\":\"100\",\"valueType\":\"NUMBER\"}]}");
        assertNull(evaluator.evaluate(rule, "{}", "{\"amount\":\"128\"}"));
        assertNotNull(evaluator.evaluate(rule, "{}", "{\"amount\":\"80\"}"));
    }

    @Test
    void isEmptyMatchesMissingField() {
        MatchRule rule = evaluator.parseRule("{\"conditions\":[{\"source\":\"BODY\",\"path\":\"refundNo\",\"operator\":\"IS_EMPTY\",\"value\":\"\",\"valueType\":\"STRING\"}]}");
        assertNull(evaluator.evaluate(rule, "{\"orderNo\":\"1\"}", "{}"));
    }
}
