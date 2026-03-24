package com.gymtracker.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public class AttendanceRequest {
    @NotNull
    private LocalDate date;

    private boolean attended;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public boolean isAttended() {
        return attended;
    }

    public void setAttended(boolean attended) {
        this.attended = attended;
    }
}
