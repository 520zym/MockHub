package com.mockhub.mock.controller;

import com.mockhub.common.model.Result;
import com.mockhub.mock.model.dto.ApiMatchPreviewRequest;
import com.mockhub.mock.model.dto.ApiMatchPreviewResult;
import com.mockhub.mock.service.ApiMatchPreviewService;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/** Protected by the existing /api/** JWT security rule. No dispatch, save or logging. */
@RestController
public class ApiMatchPreviewController {
    private final ApiMatchPreviewService service;
    public ApiMatchPreviewController(ApiMatchPreviewService service) { this.service = service; }

    @PostMapping("/api/apis/preview-match")
    public Result<ApiMatchPreviewResult> preview(@RequestBody ApiMatchPreviewRequest request,
                                                HttpServletRequest httpRequest) {
        return Result.ok(service.preview(request, httpRequest));
    }

    /** Do not let JSON-binding exceptions log the request body via the generic advice. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> invalidJson() { return Result.error(40001, "试配请求 JSON 格式无效"); }
}
