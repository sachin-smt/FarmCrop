package com.farmcrop.auth.bootstrap;

import com.farmcrop.common.enums.UserRole;
import com.farmcrop.common.validation.ValidationPatterns;
import com.farmcrop.user.entity.Role;
import com.farmcrop.user.entity.User;
import com.farmcrop.user.repository.RoleRepository;
import com.farmcrop.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the first ADMIN account from environment configuration, exactly once.
 * <ul>
 *   <li>Does nothing if any ADMIN already exists (never resets or overwrites an admin on restart).</li>
 *   <li>Never promotes an existing account: if the username/email is already taken, it refuses.</li>
 *   <li>The password is only hashed; it is never logged.</li>
 * </ul>
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    public enum Outcome { ADMIN_ALREADY_EXISTS, NOT_CONFIGURED, INVALID_CONFIGURATION, IDENTITY_TAKEN, CREATED }

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(AdminBootstrapProperties properties, UserRepository userRepository,
                                RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrap(properties);
    }

    @Transactional
    public Outcome bootstrap(AdminBootstrapProperties config) {
        if (userRepository.existsWithRole(UserRole.ADMIN)) {
            log.debug("Admin bootstrap skipped: an admin account already exists.");
            return Outcome.ADMIN_ALREADY_EXISTS;
        }
        if (config == null || !config.isComplete()) {
            log.warn("No admin account exists. Set FARMCROP_ADMIN_USERNAME, FARMCROP_ADMIN_EMAIL and "
                    + "FARMCROP_ADMIN_PASSWORD (in the environment or backend/.env) and restart to create one.");
            return Outcome.NOT_CONFIGURED;
        }

        String username = config.username().trim().toLowerCase(Locale.ROOT);
        String email = config.email().trim().toLowerCase(Locale.ROOT);
        if (!username.matches(ValidationPatterns.USERNAME) || !email.contains("@")
                || config.password().length() < MIN_ADMIN_PASSWORD_LENGTH || config.password().length() > 72) {
            log.error("Admin bootstrap refused: username must match the username rules, email must be valid, "
                    + "and the password must be {}-72 characters.", MIN_ADMIN_PASSWORD_LENGTH);
            return Outcome.INVALID_CONFIGURATION;
        }
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            log.error("Admin bootstrap refused: the configured username or email belongs to an existing account. "
                    + "Existing accounts are never promoted automatically; choose a different admin identity.");
            return Outcome.IDENTITY_TAKEN;
        }

        User admin = new User("FarmCrop Administrator", username, email, null, passwordEncoder.encode(config.password()));
        admin.grantRole(role(UserRole.ADMIN));
        userRepository.saveAndFlush(admin);
        log.info("Initial admin account '{}' created. You may now remove FARMCROP_ADMIN_PASSWORD from the environment.", username);
        return Outcome.CREATED;
    }

    private Role role(UserRole role) {
        return roleRepository.findByRoleName(role).orElseThrow(() -> new IllegalStateException("Role " + role + " missing"));
    }
}
