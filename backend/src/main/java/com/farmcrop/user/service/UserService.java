package com.farmcrop.user.service;

import com.farmcrop.common.exception.ApiException;
import com.farmcrop.common.exception.ResourceNotFoundException;
import com.farmcrop.common.response.ApiResponse.FieldError;
import com.farmcrop.common.validation.ValidationPatterns;
import com.farmcrop.user.dto.UpdateProfileRequest;
import com.farmcrop.user.dto.UserProfileResponse;
import com.farmcrop.user.entity.User;
import com.farmcrop.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(load(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = load(userId);
        String phone = ValidationPatterns.normalizePhone(request.phone());
        if (phone != null && !phone.matches(ValidationPatterns.PHONE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields.",
                    List.of(new FieldError("phone", "Enter a valid phone number (10-15 digits).")));
        }
        if (phone != null && userRepository.existsByPhoneInAndIdNot(ValidationPatterns.equivalentPhones(phone), userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "Some details are already registered.",
                    List.of(new FieldError("phone", "An account with this phone number already exists.")));
        }
        String language = request.preferredLanguage();
        if (language != null && !language.isBlank()) {
            if (!ValidationPatterns.LANGUAGES.contains(language)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields.",
                        List.of(new FieldError("preferredLanguage", "Unsupported language.")));
            }
            user.setPreferredLanguage(language);
        }
        user.setFullName(request.fullName().trim());
        // (List.of(...).contains(null) throws, hence the explicit null check.)
        if (user.getPhone() == null || !ValidationPatterns.equivalentPhones(phone).contains(user.getPhone())) {
            // A different number has not been verified by OTP.
            user.setPhoneVerified(false);
        }
        user.setPhone(phone);
        return UserProfileResponse.from(userRepository.saveAndFlush(user));
    }

    private User load(Long userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
    }
}
