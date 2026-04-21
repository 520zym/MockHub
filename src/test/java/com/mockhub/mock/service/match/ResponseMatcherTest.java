package com.mockhub.mock.service.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.dto.match.MatchCondition;
import com.mockhub.mock.model.dto.match.MatchRule;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ResponseMatcher 单元测试。
 * <p>
 * 通过 Mockito mock ApiResponseRepository，用 Spring 自带的 MockHttpServletRequest
 * 构造请求；ObjectMapper 使用真实实例（无需 mock）。
 */
class ResponseMatcherTest {

    private ApiResponseRepository repo;
    private ResponseMatcher matcher;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        repo = mock(ApiResponseRepository.class);
        mapper = new ObjectMapper();
        matcher = new ResponseMatcher(repo, mapper);
    }

    // ========== match() 整体流程 ==========

    @Nested
    class MatchFlow {

        @Test
        void noEnabledReturnsNull() {
            when(repo.findEnabledByApiId("api1")).thenReturn(Collections.<ApiResponse>emptyList());
            assertNull(matcher.match("api1", newRequest()));
        }

        @Test
        void singleEnabledShortCircuits() {
            ApiResponse r = response("r1", null, 1);
            when(repo.findEnabledByApiId("api1")).thenReturn(Arrays.asList(r));
            assertEquals(r, matcher.match("api1", newRequest()));
        }

        @Test
        void firstMatchingRuleWins() throws Exception {
            ApiResponse ruleA = response("rA", ruleJson("BODY", "status", "EQ", "ok", "STRING"), 1);
            ApiResponse ruleB = response("rB", ruleJson("BODY", "status", "EQ", "fail", "STRING"), 2);
            ApiResponse fallback = response("rF", null, 3);
            when(repo.findEnabledByApiId("api1")).thenReturn(Arrays.asList(ruleA, ruleB, fallback));

            MockHttpServletRequest req = newRequest();
            req.setContent("{\"status\":\"fail\"}".getBytes("UTF-8"));

            assertEquals(ruleB, matcher.match("api1", req));
        }

        @Test
        void orderMattersEvenIfBothMatch() throws Exception {
            // 两条规则都能命中，按 sort_order 顺序取第一条
            ApiResponse first = response("r1", ruleJson("BODY", "status", "EQ", "ok", "STRING"), 1);
            ApiResponse second = response("r2", ruleJson("BODY", "status", "CONTAINS", "o", "STRING"), 2);
            ApiResponse fallback = response("rF", null, 3);
            when(repo.findEnabledByApiId("api1")).thenReturn(Arrays.asList(first, second, fallback));

            MockHttpServletRequest req = newRequest();
            req.setContent("{\"status\":\"ok\"}".getBytes("UTF-8"));

            assertEquals(first, matcher.match("api1", req));
        }

        @Test
        void fallbackWhenNoRuleMatches() throws Exception {
            ApiResponse ruleA = response("rA", ruleJson("BODY", "status", "EQ", "ok", "STRING"), 1);
            ApiResponse fallback = response("rF", null, 2);
            when(repo.findEnabledByApiId("api1")).thenReturn(Arrays.asList(ruleA, fallback));

            MockHttpServletRequest req = newRequest();
            req.setContent("{\"status\":\"unknown\"}".getBytes("UTF-8"));

            assertEquals(fallback, matcher.match("api1", req));
        }

        @Test
        void multipleEnabledWithoutFallbackReturnsNull() {
            // 数据异常兜底（保存校验应已拦截此情况）
            ApiResponse ruleA = response("rA", ruleJson("BODY", "status", "EQ", "ok", "STRING"), 1);
            ApiResponse ruleB = response("rB", ruleJson("BODY", "status", "EQ", "fail", "STRING"), 2);
            when(repo.findEnabledByApiId("api1")).thenReturn(Arrays.asList(ruleA, ruleB));

            MockHttpServletRequest req = newRequest();
            req.setParameter("dummy", "x");

            assertNull(matcher.match("api1", req));
        }

        @Test
        void conditionsWithEmptyArrayIsTreatedAsFallback() {
            // {"conditions":[]} = 空规则 = 无规则，应作为兜底候选
            ApiResponse fallback = response("rF", "{\"conditions\":[]}", 1);
            ApiResponse ruleA = response("rA", ruleJson("BODY", "x", "EQ", "y", "STRING"), 2);
            when(repo.findEnabledByApiId("api1")).thenReturn(Arrays.asList(fallback, ruleA));

            MockHttpServletRequest req = newRequest();
            req.setParameter("any", "value");
            assertEquals(fallback, matcher.match("api1", req));
        }

        @Test
        void malformedConditionsJsonTreatedAsFallback() {
            ApiResponse bad = response("rBad", "not-json", 1);
            ApiResponse ruleA = response("rA", ruleJson("BODY", "x", "EQ", "y", "STRING"), 2);
            when(repo.findEnabledByApiId("api1")).thenReturn(Arrays.asList(bad, ruleA));

            // 坏 JSON 应被视为"无规则" → 兜底第一条
            assertEquals(bad, matcher.match("api1", newRequest()));
        }
    }

    // ========== extract() 取值 ==========

    @Nested
    class Extract {

        @Test
        void queryParameter() {
            MockHttpServletRequest req = newRequest();
            req.setParameter("userId", "1001");
            assertEquals("1001", matcher.extract("QUERY", "userId", req));
        }

        @Test
        void queryParameterMissingReturnsNull() {
            assertNull(matcher.extract("QUERY", "nope", newRequest()));
        }

        @Test
        void bodyNestedPath() throws Exception {
            MockHttpServletRequest req = newRequest();
            req.setContent("{\"user\":{\"addr\":{\"city\":\"BJ\"}}}".getBytes("UTF-8"));
            assertEquals("BJ", matcher.extract("BODY", "user.addr.city", req));
        }

        @Test
        void bodyArrayIndex() throws Exception {
            MockHttpServletRequest req = newRequest();
            req.setContent("{\"items\":[{\"id\":1},{\"id\":2}]}".getBytes("UTF-8"));
            assertEquals("2", matcher.extract("BODY", "items[1].id", req));
        }

        @Test
        void bodyMissingFieldReturnsNull() throws Exception {
            MockHttpServletRequest req = newRequest();
            req.setContent("{\"a\":1}".getBytes("UTF-8"));
            assertNull(matcher.extract("BODY", "b.c", req));
        }

        @Test
        void bodyMalformedJsonReturnsNull() throws Exception {
            MockHttpServletRequest req = newRequest();
            req.setContent("{not json".getBytes("UTF-8"));
            assertNull(matcher.extract("BODY", "a", req));
        }

        @Test
        void bodyCachedAttributeReused() throws Exception {
            // 首次 extract 会解析并缓存，second call 不再读 reader
            MockHttpServletRequest req = newRequest();
            req.setContent("{\"a\":1,\"b\":2}".getBytes("UTF-8"));
            assertEquals("1", matcher.extract("BODY", "a", req));
            assertEquals("2", matcher.extract("BODY", "b", req));
            // 请求属性已缓存
            assertNotNull(req.getAttribute(ResponseMatcher.ATTR_PARSED_BODY));
        }

        @Test
        void emptyPathReturnsNull() {
            assertNull(matcher.extract("QUERY", "", newRequest()));
            assertNull(matcher.extract("BODY", null, newRequest()));
        }

        @Test
        void unknownSourceReturnsNull() {
            assertNull(matcher.extract("HEADER", "X-Foo", newRequest()));
        }
    }

    // ========== navigate() 点路径解析 ==========

    @Nested
    class Navigate {
        @Test
        void simpleDotPath() throws Exception {
            assertEquals("\"v\"", matcher.navigate(mapper.readTree("{\"a\":\"v\"}"), "a").toString());
        }

        @Test
        void deeplyNested() throws Exception {
            assertEquals("3", matcher.navigate(
                    mapper.readTree("{\"a\":{\"b\":{\"c\":3}}}"), "a.b.c").toString());
        }

        @Test
        void arrayAtRoot() throws Exception {
            assertEquals("\"x\"", matcher.navigate(
                    mapper.readTree("{\"xs\":[\"x\",\"y\"]}"), "xs[0]").toString());
        }

        @Test
        void arrayOfObjects() throws Exception {
            assertEquals("\"y\"", matcher.navigate(
                    mapper.readTree("{\"xs\":[{\"n\":\"x\"},{\"n\":\"y\"}]}"), "xs[1].n").toString());
        }

        @Test
        void missingSegmentReturnsNull() throws Exception {
            assertNull(matcher.navigate(mapper.readTree("{\"a\":1}"), "b"));
        }
    }

    // ========== evalOperator() 各操作符 ==========

    @Nested
    class Operators {

        @Test
        void eqStringMatch() {
            assertTrue(matcher.evalOperator(cond("EQ", "abc", "STRING"), "abc"));
            assertFalse(matcher.evalOperator(cond("EQ", "abc", "STRING"), "xyz"));
        }

        @Test
        void eqNumberCrossType() {
            // "28" vs "28.0" 数字比较应等
            assertTrue(matcher.evalOperator(cond("EQ", "28.0", "NUMBER"), "28"));
        }

        @Test
        void eqNullReturnsFalse() {
            assertFalse(matcher.evalOperator(cond("EQ", "abc", "STRING"), null));
        }

        @Test
        void neString() {
            assertTrue(matcher.evalOperator(cond("NE", "abc", "STRING"), "xyz"));
            assertFalse(matcher.evalOperator(cond("NE", "abc", "STRING"), "abc"));
        }

        @Test
        void neNumberUnparseableIsFalse() {
            // actual 解析失败 → 不可比 → 返回 false（不说不等于）
            assertFalse(matcher.evalOperator(cond("NE", "10", "NUMBER"), "abc"));
        }

        @Test
        void contains() {
            assertTrue(matcher.evalOperator(cond("CONTAINS", "ell", "STRING"), "hello"));
            assertFalse(matcher.evalOperator(cond("CONTAINS", "xyz", "STRING"), "hello"));
            assertFalse(matcher.evalOperator(cond("CONTAINS", "x", "STRING"), null));
        }

        @Test
        void isEmptyNullIsTrue() {
            assertTrue(matcher.evalOperator(cond("IS_EMPTY", null, "STRING"), null));
        }

        @Test
        void isEmptyEmptyStringIsTrue() {
            assertTrue(matcher.evalOperator(cond("IS_EMPTY", null, "STRING"), ""));
        }

        @Test
        void isEmptyEmptyArrayStringIsTrue() {
            assertTrue(matcher.evalOperator(cond("IS_EMPTY", null, "STRING"), "[]"));
            assertTrue(matcher.evalOperator(cond("IS_EMPTY", null, "STRING"), "{}"));
        }

        @Test
        void isEmptyNonEmptyIsFalse() {
            assertFalse(matcher.evalOperator(cond("IS_EMPTY", null, "STRING"), "x"));
            assertFalse(matcher.evalOperator(cond("IS_EMPTY", null, "STRING"), "[1]"));
        }

        @Test
        void inListString() {
            assertTrue(matcher.evalOperator(cond("IN", "[\"a\",\"b\",\"c\"]", "STRING"), "b"));
            assertFalse(matcher.evalOperator(cond("IN", "[\"a\",\"b\"]", "STRING"), "c"));
        }

        @Test
        void inListNumber() {
            assertTrue(matcher.evalOperator(cond("IN", "[1,2,3]", "NUMBER"), "2"));
            assertFalse(matcher.evalOperator(cond("IN", "[1,2,3]", "NUMBER"), "99"));
        }

        @Test
        void inListExpectedNotArrayIsFalse() {
            assertFalse(matcher.evalOperator(cond("IN", "not-an-array", "STRING"), "a"));
        }

        @Test
        void gtGteLtLte() {
            assertTrue(matcher.evalOperator(cond("GT", "10", "NUMBER"), "20"));
            assertFalse(matcher.evalOperator(cond("GT", "10", "NUMBER"), "10"));
            assertTrue(matcher.evalOperator(cond("GTE", "10", "NUMBER"), "10"));
            assertTrue(matcher.evalOperator(cond("LT", "10", "NUMBER"), "5"));
            assertTrue(matcher.evalOperator(cond("LTE", "10", "NUMBER"), "10"));
        }

        @Test
        void gtUnparseableActualIsFalse() {
            assertFalse(matcher.evalOperator(cond("GT", "10", "NUMBER"), "abc"));
        }

        @Test
        void regex() {
            assertTrue(matcher.evalOperator(cond("REGEX", "^\\d+$", "STRING"), "123"));
            assertFalse(matcher.evalOperator(cond("REGEX", "^\\d+$", "STRING"), "abc"));
        }

        @Test
        void regexInvalidPatternIsFalse() {
            // 未闭合的括号
            assertFalse(matcher.evalOperator(cond("REGEX", "(unclosed", "STRING"), "x"));
        }

        @Test
        void unknownOperatorIsFalse() {
            assertFalse(matcher.evalOperator(cond("XYZ", "1", "STRING"), "1"));
        }

        @Test
        void nullOperatorIsFalse() {
            assertFalse(matcher.evalOperator(cond(null, "1", "STRING"), "1"));
        }
    }

    // ========== Operators 枚举工具方法 ==========

    @Nested
    class OperatorsConstants {
        @Test
        void isValidRecognizesAll() {
            assertTrue(ResponseMatcher.Operators.isValid("EQ"));
            assertTrue(ResponseMatcher.Operators.isValid("eq"));
            assertTrue(ResponseMatcher.Operators.isValid("REGEX"));
            assertFalse(ResponseMatcher.Operators.isValid("FOO"));
            assertFalse(ResponseMatcher.Operators.isValid(null));
        }

        @Test
        void requiresNumber() {
            assertTrue(ResponseMatcher.Operators.requiresNumber("GT"));
            assertTrue(ResponseMatcher.Operators.requiresNumber("lte"));
            assertFalse(ResponseMatcher.Operators.requiresNumber("EQ"));
            assertFalse(ResponseMatcher.Operators.requiresNumber(null));
        }
    }

    // ========== 集成：多条件 AND ==========

    @Nested
    class MultiConditionAnd {
        @Test
        void allConditionsMustPass() throws Exception {
            MatchRule rule = new MatchRule();
            List<MatchCondition> cs = new ArrayList<MatchCondition>();
            cs.add(cond("BODY", "a", "EQ", "1", "NUMBER"));
            cs.add(cond("BODY", "b", "EQ", "x", "STRING"));
            rule.setConditions(cs);
            String json = mapper.writeValueAsString(rule);

            ApiResponse ruleA = response("rA", json, 1);
            ApiResponse fallback = response("rF", null, 2);
            when(repo.findEnabledByApiId("api1")).thenReturn(Arrays.asList(ruleA, fallback));

            // a=1 b=x → 命中
            MockHttpServletRequest ok = newRequest();
            ok.setContent("{\"a\":1,\"b\":\"x\"}".getBytes("UTF-8"));
            assertEquals(ruleA, matcher.match("api1", ok));

            // a=1 b=y → 兜底
            MockHttpServletRequest ng = newRequest();
            ng.setContent("{\"a\":1,\"b\":\"y\"}".getBytes("UTF-8"));
            assertEquals(fallback, matcher.match("api1", ng));
        }
    }

    // ========== 辅助方法 ==========

    private static ApiResponse response(String id, String conditions, int sortOrder) {
        ApiResponse r = new ApiResponse();
        r.setId(id);
        r.setApiId("api1");
        r.setName("resp-" + id);
        r.setResponseCode(200);
        r.setContentType("application/json");
        r.setResponseBody("{}");
        r.setActive(true);
        r.setSortOrder(sortOrder);
        r.setConditions(conditions);
        return r;
    }

    private static MatchCondition cond(String source, String path, String operator, String value, String valueType) {
        MatchCondition c = new MatchCondition();
        c.setSource(source);
        c.setPath(path);
        c.setOperator(operator);
        c.setValue(value);
        c.setValueType(valueType);
        return c;
    }

    private static MatchCondition cond(String operator, String value, String valueType) {
        return cond("BODY", "x", operator, value, valueType);
    }

    private static String ruleJson(String source, String path, String operator, String value, String valueType) {
        // 手写避免依赖 mapper 失败
        return "{\"conditions\":[{\"source\":\"" + source + "\",\"path\":\"" + path
                + "\",\"operator\":\"" + operator + "\",\"value\":\"" + value
                + "\",\"valueType\":\"" + valueType + "\"}]}";
    }

    private static MockHttpServletRequest newRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setContentType("application/json");
        return req;
    }
}
