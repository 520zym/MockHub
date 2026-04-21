package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.SoapConfig;
import com.mockhub.mock.model.SoapOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * MockDispatchService.matchSoapOperation 单元测试
 * 覆盖两遍扫描：精确命中优先、尾部匹配用 / 前缀防误匹配、单 operation 兜底。
 */
class MockDispatchServiceSoapMatchTest {

    private MockDispatchService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new MockDispatchService(
                mock(com.mockhub.system.service.TeamService.class),
                mock(ApiService.class),
                mock(com.mockhub.mock.repository.ApiResponseRepository.class),
                mock(GlobalHeaderService.class),
                mock(com.mockhub.log.service.LogService.class),
                mapper,
                mock(DynamicVariableResolver.class),
                mock(com.mockhub.mock.service.match.ResponseMatcher.class)
        );
    }

    private String buildConfig(SoapOperation... ops) throws Exception {
        SoapConfig cfg = new SoapConfig();
        cfg.setOperations(Arrays.asList(ops));
        return mapper.writeValueAsString(cfg);
    }

    private SoapOperation op(String name, String action) {
        SoapOperation op = new SoapOperation();
        op.setOperationName(name);
        op.setSoapAction(action);
        return op;
    }

    @Test
    void exactSoapActionWinsOverTailMatch() throws Exception {
        String cfg = buildConfig(
                op("BarFoo", "http://tempuri.org/BarFoo"),
                op("Foo", "http://tempuri.org/Foo")
        );
        SoapOperation matched = service.matchSoapOperation(cfg, "http://tempuri.org/Foo");
        assertEquals("Foo", matched.getOperationName());
    }

    @Test
    void tailMatchUsesSlashPrefixToAvoidFalsePositive() throws Exception {
        String cfg = buildConfig(op("GetNavi", "http://tempuri.org/GetNavi"));
        SoapOperation matched = service.matchSoapOperation(cfg, "http://other/XxxGetNavi");
        assertNull(matched);
    }

    @Test
    void tailMatchHitsWithSlashPrefix() throws Exception {
        String cfg = buildConfig(op("GetNavi", "http://tempuri.org/GetNavi"));
        SoapOperation matched = service.matchSoapOperation(cfg, "http://other.host/GetNavi");
        assertEquals("GetNavi", matched.getOperationName());
    }

    @Test
    void nullSoapActionWithSingleOperationFallsBack() throws Exception {
        String cfg = buildConfig(op("OnlyOne", "http://tempuri.org/OnlyOne"));
        SoapOperation matched = service.matchSoapOperation(cfg, null);
        assertEquals("OnlyOne", matched.getOperationName());
    }

    @Test
    void nullSoapActionWithMultipleOperationsReturnsNull() throws Exception {
        String cfg = buildConfig(
                op("A", "http://tempuri.org/A"),
                op("B", "http://tempuri.org/B")
        );
        SoapOperation matched = service.matchSoapOperation(cfg, null);
        assertNull(matched);
    }

    @Test
    void emptyConfigReturnsNull() {
        SoapOperation matched = service.matchSoapOperation(null, "http://x");
        assertNull(matched);
    }

    /**
     * 回归测试（I-1）：旧代码 endsWith("Foo") 会让 SOAPAction 结尾为 BarFoo 的请求误命中 operationName=Foo。
     * 修复后用 endsWith("/Foo")，"http://tempuri.org/BarFoo" 不以 "/Foo" 结尾，不误匹配。
     */
    @Test
    void tailMatchDoesNotConfuseBarFooWithFoo() throws Exception {
        String cfg = buildConfig(op("Foo", "http://tempuri.org/Foo"));
        SoapOperation matched = service.matchSoapOperation(cfg, "http://tempuri.org/BarFoo");
        assertNull(matched);
    }

    /**
     * 防御测试（I-2）：operations 为空列表时不应 NPE 或误命中。
     */
    @Test
    void emptyOperationsListReturnsNull() throws Exception {
        SoapConfig cfg = new SoapConfig();
        cfg.setOperations(java.util.Collections.emptyList());
        String json = mapper.writeValueAsString(cfg);
        assertNull(service.matchSoapOperation(json, "http://tempuri.org/Foo"));
    }
}
