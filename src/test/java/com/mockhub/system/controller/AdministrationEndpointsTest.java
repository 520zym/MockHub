package com.mockhub.system.controller;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.GlobalExceptionHandler;
import com.mockhub.system.model.dto.*;
import com.mockhub.system.model.entity.*;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Collections;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdministrationEndpointsTest {
    TeamServiceImpl teams; UserServiceImpl users; MockMvc mvc;
    @BeforeEach void setup() {
        teams=mock(TeamServiceImpl.class);users=mock(UserServiceImpl.class);
        TeamController tc=new TeamController();ReflectionTestUtils.setField(tc,"teamService",teams);
        UserController uc=new UserController();ReflectionTestUtils.setField(uc,"userService",users);ReflectionTestUtils.setField(uc,"teamRepository",mock(TeamRepository.class));
        mvc=MockMvcBuilders.standaloneSetup(tc,uc).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void statusPatchDeserializesAndReturnsAccountStatusWithoutCredentialFields() throws Exception {
        User target=new User();target.setId("u");target.setUsername("target");target.setStatus("DISABLED");target.setPasswordHash("secret");
        when(users.updateStatus(eq("u"),any())).thenAnswer(call -> { org.junit.jupiter.api.Assertions.assertEquals("DISABLED",((UpdateUserStatusRequest)call.getArgument(1)).getStatus());return target; });
        when(users.findTeamRoles("u")).thenReturn(Collections.emptyList());
        mvc.perform(patch("/api/users/u/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(users).updateStatus(eq("u"),any());
    }
    @Test void safetyFailureIsNotReportedAsStatusSuccess() throws Exception {
        when(users.updateStatus(eq("u"),any())).thenThrow(new BizException(40901,"last admin"));
        mvc.perform(patch("/api/users/u/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40901));
    }
    @Test void teamStatusPatchAndLeaveHaveExplicitRoutes() throws Exception {
        Team t=new Team();t.setId("t");t.setStatus("ARCHIVED");
        when(teams.updateStatus(eq("t"),any())).thenAnswer(call -> {org.junit.jupiter.api.Assertions.assertEquals("ARCHIVED",((UpdateTeamStatusRequest)call.getArgument(1)).getStatus());return t;});
        mvc.perform(patch("/api/teams/t/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mvc.perform(post("/api/teams/t/leave")).andExpect(jsonPath("$.code").value(0));verify(teams).leave("t");
    }
    @Test void candidatesRequireUsernameQueryAndExposeMinimalFields() throws Exception {
        when(teams.memberCandidates("t","exact")).thenReturn(Collections.singletonList(new MemberCandidateVO("u","exact","User")));
        mvc.perform(get("/api/teams/t/member-candidates").param("username","exact"))
                .andExpect(jsonPath("$.data[0].userId").value("u")).andExpect(jsonPath("$.data[0].username").value("exact"))
                .andExpect(jsonPath("$.data[0].globalRole").doesNotExist()).andExpect(jsonPath("$.data[0].status").doesNotExist());
        verify(teams).memberCandidates("t","exact");
    }
}
