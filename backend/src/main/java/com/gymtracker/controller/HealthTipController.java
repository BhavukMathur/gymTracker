package com.gymtracker.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gymtracker.dto.HealthTipResponse;
import com.gymtracker.service.HealthTipService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/health-tips")
@Tag(name = "Health tips", description = "Lightweight, non-clinical wellness tips (randomized)")
public class HealthTipController {

    private final HealthTipService healthTipService;

    public HealthTipController(HealthTipService healthTipService) {
        this.healthTipService = healthTipService;
    }

    @GetMapping
    @Operation(summary = "Health tip", description = "Returns one general wellness or fitness habit tip, chosen at random from a static list")
    public HealthTipResponse randomTip() {
        return new HealthTipResponse(healthTipService.randomTip());
    }
}
