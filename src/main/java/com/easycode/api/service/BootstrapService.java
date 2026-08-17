package com.easycode.api.service;

import com.easycode.api.domain.UserAccount;
import com.easycode.api.domain.enums.Role;
import com.easycode.api.domain.enums.UserStatus;
import com.easycode.api.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN so there is someone to log in as. Idempotent: skips if the
 * address already exists. Unset both env vars once you're in.
 */
@Component
public class BootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${BOOTSTRAP_ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
    private String adminPassword;

    public BootstrapService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            log.info("Bootstrap admin {} already exists — skipping", email);
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setEmail(email);
        admin.setName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setPasswordHash(encoder.encode(adminPassword));
        users.save(admin);
        log.info("Bootstrap admin created: {} — now unset BOOTSTRAP_ADMIN_* and redeploy", email);
    }
}
