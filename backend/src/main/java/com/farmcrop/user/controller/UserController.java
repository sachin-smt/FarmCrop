package com.farmcrop.user.controller;

import com.farmcrop.common.response.ApiResponse;
import com.farmcrop.security.AuthenticatedUser;
import com.farmcrop.user.dto.UpdateProfileRequest;
import com.farmcrop.user.dto.UserProfileResponse;
import com.farmcrop.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the signed-in user's own account. The user id always comes from the token, never the request. */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(userService.getProfile(user.id()));
    }

    @PutMapping
    public ApiResponse<UserProfileResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(user.id(), request), "Profile updated.");
    }
}
