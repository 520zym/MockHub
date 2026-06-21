package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.SoapConfig;
import com.mockhub.mock.model.SoapOperation;
import com.mockhub.mock.model.dto.ApiMatchResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockDispatchServiceSoapConditionalResponseTest {

    private TeamService teamService;
    private ApiService apiService;
    private ResponseMatcher responseMatcher;
    private ObjectMapper objectMapper;
    private DynamicVariableResolver dynamicVariableResolver;
    private MockDispatchService service;

    @BeforeEach
    void setUp() {
        teamService = mock(TeamService.class);
        apiService = mock(ApiService.class);
        responseMatcher = mock(ResponseMatcher.class);
        dynamicVariableResolver = mock(DynamicVariableResolver.class);
        objectMapper = new ObjectMapper();
        GlobalHeaderService globalHeaderService = mock(GlobalHeaderService.class);
        when(globalHeaderService.buildHeaders(any(), any(), any()))
                .thenReturn(java.util.Collections.<String, String>emptyMap());

        service = new MockDispatchService(
                teamService,
                apiService,
                mock(ApiResponseRepository.class),
                globalHeaderService,
                mock(com.mockhub.log.service.LogService.class),
                objectMapper,
                dynamicVariableResolver,
                responseMatcher,
                mock(SoapService.class),
                mock(MockFileStorageService.class)
        );
        when(dynamicVariableResolver.resolve(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void soapDispatchUsesConditionalMatcherForMatchedOperation() throws Exception {
        Team team = new Team();
        team.setId("team-1");
        team.setIdentifier("T1");
        when(teamService.findByIdentifier("T1")).thenReturn(team);

        ApiDefinition api = new ApiDefinition();
        api.setId("api-1");
        api.setName("用户 SOAP");
        api.setType("SOAP");
        api.setMethod("POST");
        api.setPath("/user-service");
        api.setSoapConfig(soapConfig());
        when(apiService.findMatch("team-1", "POST", "/user-service"))
                .thenReturn(new ApiMatchResult(api, java.util.Collections.<String, String>emptyMap()));

        ApiResponse vip = new ApiResponse();
        vip.setId("resp-vip");
        vip.setApiId("api-1");
        vip.setSoapOperationName("GetUser");
        vip.setResponseCode(202);
        vip.setContentType("text/xml; charset=UTF-8");
        vip.setResponseBody("<vip/>");
        vip.setDelayMs(0);
        vip.setActive(true);
        when(responseMatcher.match(eq("api-1"), eq("GetUser"), any())).thenReturn(vip);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContentType("text/xml; charset=UTF-8");
        req.addHeader("SOAPAction", "http://example.com/mockhub/user/GetUser");
        req.setContent("<Envelope><Body><GetUserRequest><userId>u-1001</userId></GetUserRequest></Body></Envelope>"
                .getBytes("UTF-8"));

        ResponseEntity<?> entity = service.dispatch("T1", "POST", "/user-service", req);

        assertEquals(HttpStatus.ACCEPTED, entity.getStatusCode());
        assertEquals("<vip/>", entity.getBody());
        verify(responseMatcher).match(eq("api-1"), eq("GetUser"), any());
    }

    private String soapConfig() throws Exception {
        SoapOperation op = new SoapOperation();
        op.setOperationName("GetUser");
        op.setSoapAction("http://example.com/mockhub/user/GetUser");
        op.setResponseCode(200);
        op.setResponseBody("<default/>");
        SoapConfig config = new SoapConfig();
        config.setOperations(Arrays.asList(op));
        return objectMapper.writeValueAsString(config);
    }
}
