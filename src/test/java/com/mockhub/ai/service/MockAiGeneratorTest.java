package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.dto.AiConditionsResult;
import com.mockhub.ai.model.dto.AiGenerateRequest;
import com.mockhub.ai.model.dto.AiResponseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置演示模型的确定性输出测试。
 */
class MockAiGeneratorTest {

    private MockAiGenerator generator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        generator = new MockAiGenerator(mapper);
    }

    @Test
    void generatesValidUserJson() throws Exception {
        AiGenerateRequest req = new AiGenerateRequest();
        req.setApiName("用户信息查询");
        req.setPath("/api/user/info");
        req.setApiType("REST");
        req.setSize("STANDARD");

        AiResponseResult result = generator.response(req);

        assertEquals("application/json", result.getContentType());
        JsonNode node = mapper.readTree(result.getContent());
        assertEquals(0, node.path("code").asInt());
        assertEquals("张三", node.path("data").path("name").asText());
        assertTrue(node.path("data").has("level"));
    }

    @Test
    void generatesSoapXmlForSoapApi() {
        AiGenerateRequest req = new AiGenerateRequest();
        req.setApiName("通知服务");
        req.setApiType("SOAP");
        req.setSoapOperationName("notify");
        req.setSize("STANDARD");

        AiResponseResult result = generator.response(req);

        assertEquals("application/xml", result.getContentType());
        assertTrue(result.getContent().contains("<soapenv:Envelope"));
        assertTrue(result.getContent().contains("notifyResponse"));
    }

    @Test
    void extractsConditionFromInstruction() {
        AiGenerateRequest req = new AiGenerateRequest();
        req.setInstruction("当 body 的 userType 是 vip 时命中");
        req.setBodySample("{\"userType\":\"vip\",\"orderNo\":\"20240601\"}");

        AiConditionsResult result = generator.conditions(req);

        assertEquals(1, result.getRules().size());
        assertTrue(result.getRules().get(0).isValid());
        assertEquals("BODY", result.getRules().get(0).getSource());
        assertEquals("userType", result.getRules().get(0).getPath());
        assertEquals("vip", result.getRules().get(0).getValue());
    }

    @Test
    void flagsUnsupportedHeaderCondition() {
        AiGenerateRequest req = new AiGenerateRequest();
        req.setInstruction("当 header 的 env 是 test 时命中");
        req.setBodySample("{\"userType\":\"vip\"}");

        AiConditionsResult result = generator.conditions(req);

        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("Header"));
    }

    @Test
    void rejectsUnknownBodyField() {
        AiGenerateRequest req = new AiGenerateRequest();
        req.setInstruction("当 body 的 orderNo 是 20240601 时命中");
        req.setBodySample("{\"userType\":\"vip\"}");

        AiConditionsResult result = generator.conditions(req);

        assertFalse(result.getRules().isEmpty());
        assertFalse(result.getRules().get(0).isValid());
        assertTrue(result.getRules().get(0).getWarning().contains("orderNo"));
    }
}
