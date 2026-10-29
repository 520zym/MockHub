package com.mockhub.auth.controller;

import com.mockhub.auth.model.UpdateProfileRequest;
import com.mockhub.auth.model.UserInfo;
import com.mockhub.auth.model.WebSessionInfo;
import com.mockhub.auth.service.AuthService;
import com.mockhub.common.model.Result;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final AuthService auth;
    public MeController(AuthService auth) { this.auth = auth; }

    @PatchMapping("/profile")
    public Result<UserInfo> updateProfile(@RequestBody UpdateProfileRequest request) {
        return Result.ok(auth.updateProfile(request));
    }
    @GetMapping("/sessions")
    public Result<List<WebSessionInfo>> sessions() { return Result.ok(auth.listSessions()); }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> revoke(@PathVariable String id) { auth.revokeSession(id); return Result.ok(); }

    @PostMapping("/revoke-web-sessions")
    public Result<Void> revokeWebSessions() { auth.revokeWebSessions(); return Result.ok(); }
}
