package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** /models 响应解析。 */
class AiModelCatalogServiceTest {

    private final AiModelCatalogService service =
            new AiModelCatalogService(null, new ObjectMapper());

    @Test
    void parsesOpenAiStyleModels() throws Exception {
        String body = "{\"data\":[{\"id\":\"deepseek-chat\",\"owned_by\":\"deepseek\"},"
                + "{\"id\":\"deepseek-reasoner\",\"owned_by\":\"deepseek\"},"
                + "{\"id\":\"deepseek-chat\"}]}";
        List<Map<String, Object>> models = service.parseModels(body);
        assertEquals(2, models.size());
        assertEquals("deepseek-chat", models.get(0).get("id"));
        assertEquals("provider", models.get(0).get("source"));
    }

    @Test
    void parsesKimiMetadata() throws Exception {
        String body = "{\"data\":[{\"id\":\"kimi-k3\",\"context_length\":262144,"
                + "\"supports_image_in\":true,\"supports_reasoning\":true}]}";
        Map<String, Object> model = service.parseModels(body).get(0);
        assertEquals(262144L, model.get("contextLength"));
        assertEquals(true, model.get("supportsImage"));
        assertEquals(true, model.get("supportsReasoning"));
    }

    @Test
    void rejectsUnknownShape() {
        assertThrows(BizException.class, () -> service.parseModels("{\"object\":\"list\"}"));
    }
}
