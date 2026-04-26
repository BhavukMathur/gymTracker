package com.gymtracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gymtracker.model.User;
import com.gymtracker.model.UserRole;
import com.gymtracker.repository.UserRepository;

@Configuration
public class SeedDataConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedDataConfig.class);

    @Bean
    public CommandLineRunner seedUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("demo").isEmpty()) {
                User user = new User();
                user.setUsername("demo");
                user.setPassword(passwordEncoder.encode("demo123"));
                user.setRole(UserRole.USER);
                userRepository.save(user);
                log.info("Seeded user username=demo (USER)");
            } else {
                userRepository.findByUsername("demo").ifPresent((u) -> {
                    if (u.getRole() == null) {
                        u.setRole(UserRole.USER);
                        userRepository.save(u);
                    }
                });
            }
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole(UserRole.ADMIN);
                userRepository.save(admin);
                log.info("Seeded user username=admin (ADMIN)");
            } else {
                log.debug("User admin already present; skip seed");
            }
        };
    }
}
