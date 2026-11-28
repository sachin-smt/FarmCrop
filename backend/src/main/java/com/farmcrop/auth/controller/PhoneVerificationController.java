package com.farmcrop.auth.controller;

import com.farmcrop.auth.dto.FirebaseSignInRequest;
import com.farmcrop.auth.service.FirebaseLoginService;
import com.farmcrop.common.response.ApiResponse;
import com.farmcrop.security.AuthenticatedUser;
import com.farmcrop.user.dto.UserProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed-in users verify their mobile number with an SMS OTP (Firebase phone auth). Requires a valid
 * FarmCrop access token; the account comes from the token and the number from the verified Firebase token.
 */
@RestController
@RequestMapping("/api/v1/users/me/phone")
public class PhoneVerificationController {

    private final FirebaseLoginService firebaseLoginService;

    public PhoneVerificationController(FirebaseLoginService firebaseLoginService) {
        this.firebaseLoginService = firebaseLoginService;
    }

    @PostMapping("/verify")
    public ApiResponse<UserProfileResponse> verify(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @RequestBody FirebaseSignInRequest request,
                                                   HttpServletRequest http) {
        return ApiResponse.ok(firebaseLoginService.verifyPhoneForUser(user.id(), request.idToken(), http.getRemoteAddr()),
                "Your mobile number is verified.");
    }
}
