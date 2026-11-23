package com.mockhub.mock.service.match;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiResponseRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResponseMatcherPreviewTest {
    @Test void publicEntryDoesNotAccessRepositoryOrMutateSuppliedConfiguration() {
        ApiResponseRepository repository = mock(ApiResponseRepository.class);
        ResponseMatcher matcher = new ResponseMatcher(repository, new ObjectMapper());
        ApiResponse response = new ApiResponse(); response.setId("saved"); response.setConditions("{\"conditions\":[]}");
        List<ApiResponse> supplied = Collections.unmodifiableList(Collections.singletonList(response));
        assertSame(response, matcher.previewEnabled(supplied, "CONDITION", new MockHttpServletRequest()));
        assertEquals("saved", response.getId()); assertEquals("{\"conditions\":[]}", response.getConditions());
        assertEquals(1, supplied.size()); verifyNoInteractions(repository);
    }

    @Test void quietContextIsFinallyClearedAfterFailureAndNestedValueIsRestored() {
        ApiResponseRepository repository = mock(ApiResponseRepository.class);
        ResponseMatcher matcher = new ResponseMatcher(repository, new ObjectMapper());
        @SuppressWarnings("unchecked") ThreadLocal<Boolean> quiet = (ThreadLocal<Boolean>) ReflectionTestUtils.getField(matcher, "previewQuiet");
        assertNotNull(quiet); assertNull(quiet.get());
        assertThrows(NullPointerException.class, () -> matcher.previewEnabled(null, "CONDITION", new MockHttpServletRequest()));
        assertNull(quiet.get());
        quiet.set(Boolean.FALSE);
        try {
            matcher.previewEnabled(Collections.emptyList(), "CONDITION", new MockHttpServletRequest());
            assertEquals(Boolean.FALSE, quiet.get());
            quiet.set(Boolean.TRUE);
            assertThrows(NullPointerException.class, () -> matcher.previewEnabled(null, "CONDITION", new MockHttpServletRequest()));
            assertEquals(Boolean.TRUE, quiet.get());
        } finally { quiet.remove(); }
        Logger logger = (Logger) LoggerFactory.getLogger(ResponseMatcher.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            when(repository.findEnabledByApiId("normal")).thenReturn(Collections.emptyList());
            matcher.matchWithMode("normal", "CONDITION", new MockHttpServletRequest());
            assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains("normal")));
        } finally { logger.detachAppender(logs); logs.stop(); }
    }
}
