package com.gymtracker.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gymtracker.model.User;
import com.gymtracker.repository.UserRepository;

@Configuration
public class SeedDataConfig {

    @Bean
    public CommandLineRunner seedUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("demo").isEmpty()) {
                User user = new User();
                user.setUsername("demo");
                user.setPassword(passwordEncoder.encode("demo123"));
                userRepository.save(user);
            }
        };
    }
}
