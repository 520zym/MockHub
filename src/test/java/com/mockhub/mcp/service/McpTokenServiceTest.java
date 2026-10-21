package com.mockhub.mcp.service;

import com.mockhub.mcp.model.McpToken;
import com.mockhub.mcp.model.dto.McpTokenCreateRequest;
import com.mockhub.mcp.model.dto.McpTokenCreateResult;
import com.mockhub.mcp.repository.McpTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP Token：创建只回显一次、哈希落库、过期/撤销校验。
 */
class McpTokenServiceTest {

    private McpTokenRepository repository;
    private McpTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(McpTokenRepository.class);
        service = new McpTokenService(repository);
    }

    @Test
    void createReturnsPlaintextOnceAndStoresHash() {
        McpTokenCreateRequest request = new McpTokenCreateRequest();
        request.setName("Cursor");
        request.setExpiresInDays(30);

        McpTokenCreateResult result = service.create("user-1", request);

        assertTrue(result.getToken().startsWith("mhk_"));
        assertNotNull(result.getExpiresAt());
        ArgumentCaptor<McpToken> captor = ArgumentCaptor.forClass(McpToken.class);
        verify(repository).insert(captor.capture());
        assertNotEquals(result.getToken(), captor.getValue().getTokenHash());
        assertEquals(McpTokenService.sha256(result.getToken()), captor.getValue().getTokenHash());
        assertEquals("user-1", captor.getValue().getUserId());
    }

    @Test
    void authenticateValidToken() {
        McpToken token = new McpToken();
        token.setId("t1");
        token.setRevokedAt(null);
        when(repository.findByHash(anyString())).thenReturn(token);

        McpToken result = service.authenticate("mhk_valid");

        assertNotNull(result);
        verify(repository).touchLastUsed(anyString(), anyString());
    }

    @Test
    void authenticateRejectsRevokedOrUnknown() {
        McpToken revoked = new McpToken();
        revoked.setRevokedAt("2026-01-01T00:00:00");
        when(repository.findByHash(anyString())).thenReturn(revoked);
        assertNull(service.authenticate("mhk_revoked"));

        when(repository.findByHash(anyString())).thenReturn(null);
        assertNull(service.authenticate("mhk_missing"));
        assertNull(service.authenticate("bad-prefix"));
    }

    @Test
    void rejectsBlankName() {
        McpTokenCreateRequest request = new McpTokenCreateRequest();
        request.setName(" ");
        boolean failed = false;
        try {
            service.create("user-1", request);
        } catch (Exception e) {
            failed = true;
        }
        assertTrue(failed);
    }
}
