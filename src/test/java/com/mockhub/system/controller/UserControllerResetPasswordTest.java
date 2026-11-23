package com.mockhub.system.controller;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.GlobalExceptionHandler;
import com.mockhub.system.service.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerResetPasswordTest {

    private UserServiceImpl service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(UserServiceImpl.class);
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void resetShouldDelegateRawPasswordOnly() throws Exception {
        mvc.perform(post("/api/users/target/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(service).resetPassword("target", "new-password");
        verifyNoMoreInteractions(service);
    }

    @Test
    void resetShouldLetServiceAuthorizeBeforePasswordValidation() throws Exception {
        doThrow(new BizException(40101, "无操作权限")).when(service).resetPassword("missing", "short");

        mvc.perform(post("/api/users/missing/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"short\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));

        verify(service).resetPassword("missing", "short");
        verifyNoMoreInteractions(service);
    }

    @Test
    void resetShouldDelegateMissingPasswordAndPreserveBusinessError() throws Exception {
        doThrow(new BizException(40000, "新密码长度不能少于6位")).when(service).resetPassword("target", null);

        mvc.perform(post("/api/users/target/reset-password")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verify(service).resetPassword("target", null);
        verifyNoMoreInteractions(service);
    }
}
