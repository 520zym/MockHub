package com.mockhub.mock.controller;

import com.mockhub.mock.service.MockDispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MockDispatchController 的 WSDL 分支路由测试。
 * 直接 mock MockDispatchService，验证路由分派（serveWsdl vs dispatch）正确。
 */
class MockDispatchControllerWsdlIT {

    private MockHttpServletRequest buildRequest(String method, String uri, String query) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setQueryString(query);
        req.setAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                uri);
        return req;
    }

    @Test
    void getWithWsdlParamRoutesToServeWsdl() {
        MockDispatchService svc = mock(MockDispatchService.class);
        when(svc.serveWsdl(anyString(), anyString(), any()))
                .thenReturn(new ResponseEntity<String>("<wsdl/>", HttpStatus.OK));
        MockDispatchController controller = new MockDispatchController(svc);

        MockHttpServletRequest req = buildRequest("GET", "/mock/FOC/ck/release", "wsdl");
        ResponseEntity<String> resp = controller.handleMockRequest("FOC", req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(svc).serveWsdl(eq("FOC"), eq("/ck/release"), any());
        verify(svc, never()).dispatch(anyString(), anyString(), anyString(), any());
    }

    @Test
    void getWithoutWsdlParamRoutesToDispatch() {
        MockDispatchService svc = mock(MockDispatchService.class);
        when(svc.dispatch(anyString(), anyString(), anyString(), any()))
                .thenReturn(new ResponseEntity<String>("", HttpStatus.NOT_FOUND));
        MockDispatchController controller = new MockDispatchController(svc);

        MockHttpServletRequest req = buildRequest("GET", "/mock/FOC/ck/release", "foo=bar");
        controller.handleMockRequest("FOC", req);

        verify(svc).dispatch(eq("FOC"), eq("GET"), eq("/ck/release"), any());
        verify(svc, never()).serveWsdl(anyString(), anyString(), any());
    }

    @Test
    void postWithWsdlParamRoutesToDispatch() {
        MockDispatchService svc = mock(MockDispatchService.class);
        when(svc.dispatch(anyString(), anyString(), anyString(), any()))
                .thenReturn(new ResponseEntity<String>("", HttpStatus.OK));
        MockDispatchController controller = new MockDispatchController(svc);

        MockHttpServletRequest req = buildRequest("POST", "/mock/FOC/ck/release", "wsdl");
        controller.handleMockRequest("FOC", req);

        verify(svc).dispatch(eq("FOC"), eq("POST"), eq("/ck/release"), any());
        verify(svc, never()).serveWsdl(anyString(), anyString(), any());
    }

    @Test
    void queryParamNamedMywsdlvalueDoesNotTrigger() {
        MockDispatchService svc = mock(MockDispatchService.class);
        when(svc.dispatch(anyString(), anyString(), anyString(), any()))
                .thenReturn(new ResponseEntity<String>("", HttpStatus.OK));
        MockDispatchController controller = new MockDispatchController(svc);

        MockHttpServletRequest req = buildRequest("GET", "/mock/FOC/ck/release", "foo=mywsdlvalue");
        controller.handleMockRequest("FOC", req);

        verify(svc).dispatch(anyString(), anyString(), anyString(), any());
        verify(svc, never()).serveWsdl(anyString(), anyString(), any());
    }

    @Test
    void uppercaseWSDLParamTriggers() {
        MockDispatchService svc = mock(MockDispatchService.class);
        when(svc.serveWsdl(anyString(), anyString(), any()))
                .thenReturn(new ResponseEntity<String>("<wsdl/>", HttpStatus.OK));
        MockDispatchController controller = new MockDispatchController(svc);

        MockHttpServletRequest req = buildRequest("GET", "/mock/FOC/ck/release", "WSDL");
        controller.handleMockRequest("FOC", req);

        verify(svc).serveWsdl(anyString(), anyString(), any());
    }
}
