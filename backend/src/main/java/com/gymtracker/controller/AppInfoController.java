package com.gymtracker.controller;

import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AppInfoController {

    private final Environment environment;

    public AppInfoController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/profile")
    public Map<String, String> activeProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length > 0) {
            return Map.of("profile", active[0]);
        }
        String[] defaults = environment.getDefaultProfiles();
        if (defaults.length > 0) {
            return Map.of("profile", defaults[0]);
        }
        return Map.of("profile", "default");
    }
}
