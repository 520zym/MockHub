package com.mockhub.mcp.controller;

import com.mockhub.common.model.Result;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mcp.model.dto.*;
import com.mockhub.mcp.service.McpTokenService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Personal credentials cannot be targeted by userId, including by a system administrator. */
@RestController
@RequestMapping("/api/me/mcp-tokens")
public class PersonalMcpTokenController {
    private final McpTokenService service;
    public PersonalMcpTokenController(McpTokenService service){this.service=service;}
    @GetMapping public Result<List<McpTokenVO>> list(){return Result.ok(service.list());}
    @PostMapping public Result<McpTokenCreateResult> create(@RequestBody McpTokenCreateRequest request){return Result.ok(service.create(SecurityContextUtil.getCurrentUserId(),request));}
    @DeleteMapping("/{id}") public Result<Void> revoke(@PathVariable String id){service.revoke(id);return Result.ok();}
}
