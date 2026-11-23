package com.mockhub.mock.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.dto.ApiMatchPreviewRequest;
import com.mockhub.mock.model.dto.ApiMatchPreviewRequest.Response;
import com.mockhub.mock.model.dto.ApiMatchPreviewRequest.Sample;
import com.mockhub.mock.model.dto.ApiMatchPreviewResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiMatchPreviewServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private PermissionChecker permissions;
    private TeamRepository teams;
    private ApiRepository apis;
    private ApiResponseRepository responses;
    private ResponseMatcher matcher;
    private ApiMatchPreviewService service;
    private ApiMatchPreviewRequest request;
    private MockHttpServletRequest original;
    private Team team;

    @BeforeEach void setUp() {
        permissions = mock(PermissionChecker.class);
        teams = mock(TeamRepository.class);
        apis = mock(ApiRepository.class);
        responses = mock(ApiResponseRepository.class);
        matcher = new ResponseMatcher(responses, mapper);
        service = new ApiMatchPreviewService(permissions, teams, apis, responses, matcher, mapper);
        team = new Team(); team.setId("team");
        when(teams.findById("team")).thenReturn(team);
        request = new ApiMatchPreviewRequest();
        request.setTeamId("team"); request.setResponseMode("CONDITION"); request.setSample(new Sample());
        original = new MockHttpServletRequest();
    }

    @Test void sortedFirstMatchUsesOriginalDraftIndexEvenWithDuplicateIds() {
        Response late = tab("same", 20, condition("QUERY", "q", "EQ", "yes", "STRING"));
        Response early = tab("same", 1, late.getConditions()); early.setClientKey("new-tab");
        request.setResponses(Arrays.asList(late, tab("fallback", -1, null), early));
        request.getSample().setQuery(Collections.singletonMap("q", "yes"));
        ApiMatchPreviewResult result = preview();
        assertEquals("CONDITION_MATCH", result.getReason()); assertEquals(2, result.getMatchedIndex());
        assertEquals("new-tab", result.getMatchedClientKey()); assertEquals(3, result.getEnabledCount());
        assertFalse(result.isUsingSavedConfiguration());
        verify(permissions).checkTeamAccess("team"); verify(permissions).checkTeamWrite("team");
        verify(teams).findById("team"); verifyNoMoreInteractions(teams, permissions); verifyNoInteractions(apis, responses);
    }

    @Test void fallbackAndNoMatchAreActualMatcherOutcomes() {
        Response a = tab("a", 0, condition("QUERY", "q", "EQ", "yes", "STRING"));
        request.setResponses(Arrays.asList(a, tab("fallback", 1, "{}")));
        assertEquals("fallback", preview().getMatchedResponseId()); assertEquals("FALLBACK", preview().getReason());
        request.setResponses(Arrays.asList(a, tab("also-no", 1, a.getConditions())));
        ApiMatchPreviewResult result = preview(); assertEquals("NO_MATCH", result.getReason());
        assertNull(result.getMatchedIndex()); assertNull(result.getResponseCode());
    }

    @Test void singleEnabledShortCircuitsFailingRuleAndDisabledIsIgnored() {
        Response disabled = tab("disabled", -1, null); disabled.setActive(false);
        request.setResponses(Arrays.asList(disabled, tab(null, 10, condition("QUERY", "missing", "EQ", "yes", "STRING"))));
        ApiMatchPreviewResult result = preview(); assertEquals("SINGLE_ENABLED", result.getReason());
        assertEquals(1, result.getMatchedIndex()); assertEquals(1, result.getEnabledCount());
        assertNull(result.getMatchedResponseId());
    }

    @Test void noEnabledIsNotInventedFallback() {
        Response disabled = tab("off", 0, null); disabled.setActive(false);
        request.setResponses(Collections.singletonList(disabled));
        assertEquals("NO_ENABLED_RESPONSE", preview().getReason());
        request.setResponses(Collections.emptyList()); assertEquals(0, preview().getEnabledCount());
    }

    @Test void randomOnlyReturnsSortedEnabledCandidatesIgnoringConditions() throws Exception {
        Response disabled = tab("off", 0, null); disabled.setActive(false);
        Response file = tab("file", 2, "{\"conditions\":[{\"operator\":\"UNKNOWN\"}]}");
        file.setBodyType("FILE"); file.setFilePath("../../never-open-this"); file.setResponseBody("never return this");
        request.setResponseMode("RANDOM");
        request.setResponses(Arrays.asList(disabled, file, tab("text", 1, null)));
        ApiMatchPreviewResult result = preview();
        assertEquals("RANDOM_EQUAL_PROBABILITY", result.getReason()); assertNull(result.getMatchedIndex());
        assertEquals(2, result.getCandidates().size()); assertEquals(2, result.getCandidates().get(0).getIndex());
        assertEquals("FILE", result.getCandidates().get(1).getBodyType());
        String json = mapper.writeValueAsString(result);
        assertFalse(json.contains("never")); assertFalse(json.contains("filePath")); assertFalse(json.contains("responseBody"));
        verifyNoInteractions(responses, apis);
    }

    @Test void headerIsCaseInsensitiveAndQueryIsSampleOnly() {
        request.setResponses(Arrays.asList(tab("match", 0, condition("HEADER", "x-ToKeN", "EQ", "sample", "STRING")), tab("fallback", 1, null)));
        request.getSample().setHeaders(Collections.singletonMap("X-Token", "sample"));
        assertEquals("match", preview().getMatchedResponseId());
        request.getResponses().get(0).setConditions(condition("QUERY", "token", "EQ", "sample", "STRING"));
        original.addParameter("token", "sample", "second");
        assertEquals("fallback", preview().getMatchedResponseId());
        request.getSample().setQuery(Collections.singletonMap("token", "sample"));
        assertEquals("match", preview().getMatchedResponseId());
    }

    @Test void jsonArrayAndNumberUseRealBigDecimalSemantics() {
        request.setResponses(Arrays.asList(tab("number", 0, condition("BODY", "items[0].price", "EQ", "1.00", "NUMBER")), tab("fallback", 1, null)));
        request.getSample().setBody("{\"items\":[{\"price\":1}]}");
        assertEquals("number", preview().getMatchedResponseId());
        request.getSample().setBody("{\"items\":[{\"price\":2}]}");
        assertEquals("fallback", preview().getMatchedResponseId());
    }

    @Test void soapXmlNamespacesAndDtdRejectionUseExistingParser() {
        Response soap = tab("soap", 0, condition("BODY", "Envelope.Body.GetUser.id", "EQ", "42", "NUMBER"));
        Response fallback = tab("fallback", 1, null);
        soap.setSoapOperationName("GetUser"); fallback.setSoapOperationName("GetUser");
        request.setOperationName("GetUser"); request.setResponses(Arrays.asList(soap, fallback));
        request.getSample().setContentType("application/soap+xml");
        request.getSample().setBody("<s:Envelope xmlns:s='urn:soap'><s:Body><u:GetUser xmlns:u='urn:user'><u:id>42</u:id></u:GetUser></s:Body></s:Envelope>");
        assertEquals("soap", preview().getMatchedResponseId());
        request.getSample().setBody("<!DOCTYPE Envelope [<!ENTITY xxe SYSTEM 'file:///never-read-preview'>]><Envelope><Body><GetUser><id>&xxe;</id></GetUser></Body></Envelope>");
        assertEquals("fallback", preview().getMatchedResponseId());
        assertNull(original.getAttribute(ResponseMatcher.ATTR_PARSED_XML_BODY));
    }

    @Test void wrapperDoesNotInheritAuthorizationCookiesOrAnyParserCache() throws Exception {
        original.addHeader("Authorization", "Bearer real-jwt"); original.setCookies(new Cookie("session", "real-cookie"));
        original.setAttribute(ResponseMatcher.ATTR_RAW_BODY, "real-body");
        Object jsonCache = mapper.readTree("{\"secret\":true}"); Object xmlCache = new Object();
        original.setAttribute(ResponseMatcher.ATTR_PARSED_BODY, jsonCache);
        original.setAttribute(ResponseMatcher.ATTR_PARSED_XML_BODY, xmlCache);
        request.setResponses(Arrays.asList(tab("leak", 0, condition("HEADER", "Authorization", "EQ", "Bearer real-jwt", "STRING")), tab("fallback", 1, null)));
        assertEquals("fallback", preview().getMatchedResponseId());
        request.getResponses().get(0).setConditions(condition("BODY", "secret", "EQ", "true", "STRING"));
        request.getSample().setBody("{\"secret\":false}");
        assertEquals("fallback", preview().getMatchedResponseId());
        ApiMatchPreviewService.SampleRequest wrapper = new ApiMatchPreviewService.SampleRequest(original, request.getSample());
        assertNull(wrapper.getCookies()); assertNull(wrapper.getHeader("Cookie")); assertNull(wrapper.getUserPrincipal());
        wrapper.setAttribute("local", "only"); assertNull(original.getAttribute("local"));
        assertSame(jsonCache, original.getAttribute(ResponseMatcher.ATTR_PARSED_BODY));
        assertSame(xmlCache, original.getAttribute(ResponseMatcher.ATTR_PARSED_XML_BODY));
        assertEquals("real-body", original.getAttribute(ResponseMatcher.ATTR_RAW_BODY));
        assertEquals("{\"secret\":false}", wrapper.getReader().readLine());
        assertEquals(request.getSample().getBody().getBytes("UTF-8").length, wrapper.getContentLength());
    }

    @Test void viewerCannotInjectDraftRegexOrModeAndSavedConfigurationRemainsReadable() {
        stored("CONDITION");
        doThrow(new BizException(40104, "readonly")).when(permissions).checkTeamWrite("team");
        Response injection = tab("evil", 0, "not even JSON");
        request.setResponses(Collections.singletonList(injection)); request.setResponseMode("INVALID-IGNORED");
        ApiMatchPreviewResult result = preview();
        assertTrue(result.isUsingSavedConfiguration()); assertEquals("stored", result.getMatchedResponseId());
        assertEquals("CONDITION", result.getMode()); assertNull(result.getMatchedClientKey());
        verify(apis).findById("api"); verify(responses).findByApiId("api");
        verify(teams).findById("team"); verifyNoMoreInteractions(apis, responses, teams);
    }

    @Test void viewerMustSpecifyExistingAuthorizedApi() {
        doThrow(new BizException(40104, "readonly")).when(permissions).checkTeamWrite("team");
        assertCode(40104);
        request.setApiId("trash-or-missing"); assertCode(40401); verifyNoInteractions(responses);
    }

    @Test void archivedEvenWithWritablePermissionUsesSavedConfiguration() {
        stored("RANDOM"); team.setStatus("ARCHIVED");
        request.setResponseMode("CONDITION"); request.setResponses(Collections.singletonList(tab("evil", 0, null)));
        ApiMatchPreviewResult result = preview(); assertTrue(result.isUsingSavedConfiguration());
        assertEquals("RANDOM", result.getMode()); assertEquals("stored", result.getCandidates().get(0).getResponseId());
    }

    @Test void archiveWriteDenialIsTheOnlyOtherReadOnlyDowngrade() {
        stored("CONDITION");
        doThrow(new BizException(40903, "archived")).when(permissions).checkTeamWrite("team");
        assertTrue(preview().isUsingSavedConfiguration());
    }

    @Test void noTeamCannotFallThroughToGlobalScope() {
        request.setTeamId(null); assertCode(40001); verifyNoInteractions(permissions, teams, apis, responses);
    }

    @Test void accessIsCheckedBeforeEvenLookingUpTeam() {
        doThrow(new BizException(40102, "not a member")).when(permissions).checkTeamAccess("team");
        assertCode(40102); verifyNoInteractions(teams, apis, responses);
    }

    @Test void teamMustExistEvenForPermissionBypass() {
        when(teams.findById("team")).thenReturn(null); assertCode(40403);
        verify(permissions, never()).checkTeamWrite(anyString()); verifyNoInteractions(apis, responses);
    }

    @Test void apiTeamCannotBeForged() {
        stored("CONDITION"); when(apis.findById("api")).thenReturn(api("another-team", "CONDITION"));
        assertCode(40102); verifyNoInteractions(responses);
    }

    @Test void editorCanPreviewNewDraftWithoutConfigReads() {
        request.setResponses(Collections.singletonList(tab("new", 0, null)));
        assertEquals("new", preview().getMatchedResponseId()); verifyNoInteractions(apis, responses);
    }

    @Test void writeDetectionDoesNotSwallowUnknownPermissionCodesOrDatabaseFailures() {
        request.setResponses(Collections.singletonList(tab("new", 0, null)));
        for (int code : new int[]{40102, 40403, 40101, 50001}) {
            doThrow(new BizException(code, "denied")).when(permissions).checkTeamWrite("team"); assertCode(code);
        }
        doThrow(new DataAccessResourceFailureException("db offline")).when(permissions).checkTeamWrite("team");
        assertThrows(DataAccessResourceFailureException.class, this::preview); verifyNoInteractions(responses, apis);
    }

    @Test void savedSoapUsesOperationModeNotClientOrRestMode() {
        stored("CONDITION"); ApiDefinition api = api("team", "CONDITION"); api.setType("SOAP");
        api.setSoapConfig("{\"operations\":[{\"operationName\":\"GetUser\",\"responseMode\":\"RANDOM\"}]}");
        when(apis.findById("api")).thenReturn(api);
        ApiResponse soap = entity(tab("soap-saved", 0, null)); soap.setSoapOperationName("GetUser");
        when(responses.findByApiIdAndOperation("api", "GetUser")).thenReturn(Collections.singletonList(soap));
        doThrow(new BizException(40104, "readonly")).when(permissions).checkTeamWrite("team");
        request.setOperationName("GetUser");
        assertEquals("RANDOM", preview().getMode()); assertEquals("soap-saved", preview().getCandidates().get(0).getResponseId());
        request.setOperationName("unknown"); assertCode(40001);
    }

    @Test void limitsRejectRatherThanSilentlyTruncate() {
        request.setResponses(Collections.nCopies(51, tab("x", 0, null))); assertCode(40001);
        request.setResponses(Collections.singletonList(tab("x", 0, null)));
        request.getSample().setBody(repeat('x', 1024 * 1024 + 1)); assertCode(40001);
        request.getSample().setBody(repeat('中', 350000)); assertCode(40001);
        request.getSample().setBody(""); Map<String, String> values = new HashMap<String, String>();
        for (int i = 0; i < 101; i++) values.put("q" + i, "x");
        request.getSample().setQuery(values); assertCode(40001);
        request.getSample().setQuery(null); request.getSample().setHeaders(Collections.singletonMap("bad\rname", "x")); assertCode(40001);
        request.getSample().setHeaders(Collections.singletonMap("ok", "bad\nvalue")); assertCode(40001);
        request.getSample().setHeaders(null); request.setResponseMode("BOGUS"); assertCode(40001);
    }

    @Test void malformedConditionsAreNeverAFalseFallbackIncludingDisabledOrRandom() {
        for (String json : Arrays.asList("oops", "[]", "null", "{} {}", "{\"conditions\":{}}", "{\"unknown\":true}")) {
            Response bad = tab("bad", 0, json); request.setResponses(Collections.singletonList(bad));
            request.setResponseMode("CONDITION"); assertCode(40001);
            bad.setActive(false); request.setResponseMode("RANDOM"); assertCode(40001);
        }
    }

    @Test void conditionsCountAndFieldsHaveClearValidationErrors() {
        String single = "{\"source\":\"QUERY\",\"path\":\"q\",\"operator\":\"EQ\",\"value\":\"x\"}";
        request.setResponses(Collections.singletonList(tab("too-many", 0,
                "{\"conditions\":[" + String.join(",", Collections.nCopies(101, single)) + "]}")));
        assertCode(40001);
        for (String json : Arrays.asList("{\"conditions\":[null]}", condition("INVALID", "x", "EQ", "x", "STRING"),
                condition("QUERY", "x", "BOGUS", "x", "STRING"), condition("QUERY", "x", "GT", "2", "STRING"))) {
            request.getResponses().get(0).setConditions(json); assertCode(40001);
        }
    }

    @Test void previewDoesNotLogSampleNumericOrMalformedJsonAndProductionStillWarns() {
        Logger logger = (Logger) LoggerFactory.getLogger(ResponseMatcher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            Response numeric = tab("numeric", 0, condition("HEADER", "Authorization", "GT", "1", "NUMBER"));
            Response fallback = tab("fallback", 1, null); request.setResponses(Arrays.asList(numeric, fallback));
            request.getSample().setHeaders(Collections.singletonMap("Authorization", "secret-credential"));
            preview(); assertFalse(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("secret-credential")));
            numeric.setConditions(condition("BODY", "a", "EQ", "x", "STRING")); request.getSample().setBody("{secret-body");
            preview(); assertFalse(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("secret-body")));
            appender.list.clear();
            numeric.setConditions(condition("HEADER", "Authorization", "GT", "1", "NUMBER"));
            when(responses.findEnabledByApiId("production")).thenReturn(Arrays.asList(entity(numeric), entity(fallback)));
            original.addHeader("Authorization", "production-warning"); matcher.matchWithMode("production", "CONDITION", original);
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("production-warning")), "preview must restore original logging");
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    private ApiMatchPreviewResult preview() { return service.preview(request, original); }
    private void assertCode(int code) { assertEquals(code, assertThrows(BizException.class, this::preview).getCode()); }
    private void stored(String mode) {
        request.setApiId("api"); when(apis.findById("api")).thenReturn(api("team", mode));
        when(responses.findByApiId("api")).thenReturn(Collections.singletonList(entity(tab("stored", 0, null))));
    }
    private static ApiDefinition api(String team, String mode) {
        ApiDefinition api = new ApiDefinition(); api.setId("api"); api.setTeamId(team); api.setType("REST"); api.setResponseMode(mode); return api;
    }
    private static Response tab(String id, int order, String conditions) {
        Response r = new Response(); r.setId(id); r.setName(id); r.setResponseCode(200); r.setBodyType("TEXT");
        r.setSortOrder(order); r.setActive(true); r.setConditions(conditions); return r;
    }
    private static ApiResponse entity(Response dto) {
        ApiResponse r = new ApiResponse(); r.setId(dto.getId()); r.setName(dto.getName()); r.setResponseCode(dto.getResponseCode());
        r.setBodyType(dto.getBodyType()); r.setConditions(dto.getConditions()); r.setSortOrder(dto.getSortOrder()); r.setActive(dto.isActive()); return r;
    }
    private String condition(String source, String path, String op, String value, String type) {
        try {
            Map<String, String> c = new HashMap<String, String>(); c.put("source", source); c.put("path", path);
            c.put("operator", op); c.put("value", value); c.put("valueType", type);
            return mapper.writeValueAsString(Collections.singletonMap("conditions", Collections.singletonList(c)));
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static String repeat(char c, int count) { char[] chars = new char[count]; Arrays.fill(chars, c); return new String(chars); }
}
