package com.gymtracker.dto;

public class HealthTipResponse {

    private final String tip;

    public HealthTipResponse(String tip) {
        this.tip = tip;
    }

    public String getTip() {
        return tip;
    }
}
