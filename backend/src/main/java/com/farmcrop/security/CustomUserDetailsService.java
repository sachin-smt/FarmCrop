package com.farmcrop.security;

import com.farmcrop.user.entity.User;
import com.farmcrop.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Password-login lookup by username or email. Account status is deliberately NOT checked here:
 * it is checked after the password matches, so the login endpoint never reveals whether an
 * unauthenticated caller guessed the identifier of a suspended account.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) {
        String identifier = usernameOrEmail == null ? "" : usernameOrEmail.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findForLogin(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return org.springframework.security.core.userdetails.User
                .withUsername(String.valueOf(user.getId()))
                .password(user.getPasswordHash())
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.getRoleName().authority()))
                        .toList())
                .build();
    }
}
