package com.mockhub.mock.service.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Header 来源条件：匹配器取值、校验器放行。
 */
class HeaderConditionTest {

    private ApiResponseRepository repo;
    private ResponseMatcher matcher;

    @BeforeEach
    void setUp() {
        repo = mock(ApiResponseRepository.class);
        matcher = new ResponseMatcher(repo, new ObjectMapper());
    }

    @Test
    void extractsHeaderCaseInsensitively() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Env", "test");

        assertEquals("test", matcher.extract("HEADER", "x-env", request));
    }

    @Test
    void matchesResponseByHeaderCondition() {
        ApiResponse vip = new ApiResponse();
        vip.setId("vip");
        vip.setName("VIP");
        vip.setActive(true);
        vip.setSortOrder(0);
        vip.setConditions("{\"conditions\":[{\"source\":\"HEADER\",\"path\":\"X-Env\",\"operator\":\"EQ\",\"value\":\"test\",\"valueType\":\"STRING\"}]}");
        ApiResponse fallback = new ApiResponse();
        fallback.setId("fb");
        fallback.setName("兜底");
        fallback.setActive(true);
        fallback.setSortOrder(1);
        fallback.setConditions(null);
        List<ApiResponse> enabled = Arrays.asList(vip, fallback);
        when(repo.findEnabledByApiId("api-1")).thenReturn(enabled);

        MockHttpServletRequest hit = new MockHttpServletRequest();
        hit.addHeader("X-Env", "test");
        assertEquals("vip", matcher.match("api-1", hit).getId());

        MockHttpServletRequest miss = new MockHttpServletRequest();
        assertEquals("fb", matcher.match("api-1", miss).getId());
    }

    @Test
    void validatorAcceptsHeaderSource() {
        com.mockhub.mock.model.dto.ApiResponseDTO dto = new com.mockhub.mock.model.dto.ApiResponseDTO();
        dto.setActive(true);
        dto.setConditions("{\"conditions\":[{\"source\":\"HEADER\",\"path\":\"X-Env\",\"operator\":\"EQ\",\"value\":\"test\",\"valueType\":\"STRING\"}]}");
        ResponseValidator.validateDtos(Collections.singletonList(dto));
    }
}
