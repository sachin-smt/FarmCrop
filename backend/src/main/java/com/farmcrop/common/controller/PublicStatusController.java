package com.farmcrop.common.controller;

import com.farmcrop.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight public endpoint the frontend uses to confirm the API is reachable. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicStatusController {

    public record StatusResponse(String service, String apiVersion, String status) {
    }

    @GetMapping("/status")
    public ApiResponse<StatusResponse> status() {
        return ApiResponse.ok(new StatusResponse("farmcrop-backend", "v1", "UP"));
    }
}
