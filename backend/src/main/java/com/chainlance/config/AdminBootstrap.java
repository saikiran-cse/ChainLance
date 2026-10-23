package com.chainlance.config;

import com.chainlance.entity.User;
import com.chainlance.entity.enums.Role;
import com.chainlance.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the platform ADMIN login on first start when ADMIN_EMAIL and ADMIN_PASSWORD are both set.
 * It never changes an existing account. If only one of the two is set, nothing is created and a warning is logged.
 * This is the website login only: it holds no wallet or private key, and on-chain admin power
 * needs the separate admin MetaMask wallet.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final AdminBootstrapProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AdminBootstrapProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.email() == null ? "" : properties.email().trim().toLowerCase(Locale.ROOT);
        String password = properties.password() == null ? "" : properties.password();

        if (email.isEmpty() && password.isEmpty()) {
            return;
        }
        if (email.isEmpty() || password.isEmpty()) {
            log.warn("Admin bootstrap skipped: set BOTH ADMIN_EMAIL and ADMIN_PASSWORD to create the admin login.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (users.existsByEmail(email)) {
            log.info("Admin bootstrap: a user with the configured admin email already exists; nothing changed.");
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName("Platform Admin");
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.info("Admin bootstrap: created ADMIN login for the configured admin email.");
    }
}
