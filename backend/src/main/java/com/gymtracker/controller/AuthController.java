package com.gymtracker.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gymtracker.dto.LoginRequest;
import com.gymtracker.dto.LoginResponse;
import com.gymtracker.dto.RegisterRequest;
import com.gymtracker.model.User;
import com.gymtracker.model.UserRole;
import com.gymtracker.repository.UserRepository;
import com.gymtracker.security.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, UserDetailsService userDetailsService,
            JwtUtil jwtUtil, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("[SLF4J] login attempt username={}", request.getUsername());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException ex) {
            log.info("[SLF4J] login failed (bad credentials) username={}", request.getUsername());
            throw new UnauthorizedException();
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        String token = jwtUtil.generateToken(userDetails);
        log.info("[SLF4J] login success username={}", request.getUsername());
        return new LoginResponse(token);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        log.info("[SLF4J] register attempt username={}", request.getUsername());
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            log.info("[SLF4J] register rejected (username taken) username={}", request.getUsername());
            throw new UsernameTakenException();
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);
        userRepository.save(user);

        log.info("[SLF4J] register success username={}", user.getUsername());
        return Map.of(
                "username", user.getUsername(),
                "message", "User registered");
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    private static class UsernameTakenException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class UnauthorizedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
