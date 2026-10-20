package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mcp.model.McpSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 工具默认开关与工具清单。
 */
class McpToolRegistryTest {

    private final McpToolRegistry registry = new McpToolRegistry(new ObjectMapper());

    @Test
    void defaultSettingsExposeAllButApply() {
        McpSettings settings = new McpSettings();
        assertEquals(7, registry.listEnabled(settings).size());
        assertFalse(registry.isEnabled(settings, McpToolRegistry.APPLY_API_DRAFT));
        assertTrue(registry.isEnabled(settings, McpToolRegistry.SEARCH_APIS));
    }

    @Test
    void disabledToolIsExcluded() {
        McpSettings settings = new McpSettings();
        settings.setToolSearchApis(false);
        assertEquals(6, registry.listEnabled(settings).size());
        assertNull(registry.get("unknown.tool"));
        assertNotNull(registry.get(McpToolRegistry.CREATE_API_DRAFT).toJson(new ObjectMapper()));
    }
}
