package com.farmcrop.auth.controller;

import com.farmcrop.auth.service.FirebaseLoginService;
import com.farmcrop.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the login page which sign-in methods the server can actually verify, so it never offers a dead button. */
@RestController
@RequestMapping("/api/v1/public")
public class AuthProvidersController {

    public record AuthProvidersResponse(boolean password, boolean google, boolean phone) {
    }

    private final FirebaseLoginService firebaseLoginService;

    public AuthProvidersController(FirebaseLoginService firebaseLoginService) {
        this.firebaseLoginService = firebaseLoginService;
    }

    @GetMapping("/auth-providers")
    public ApiResponse<AuthProvidersResponse> providers() {
        boolean firebase = firebaseLoginService.enabled();
        return ApiResponse.ok(new AuthProvidersResponse(true, firebase, firebase));
    }
}
