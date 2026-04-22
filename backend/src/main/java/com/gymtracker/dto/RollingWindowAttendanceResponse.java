package com.gymtracker.dto;

import java.util.Map;

/**
 * Read-only view of attendance from {@code startDate} through {@code endDate} inclusive.
 * The window ends on "today" in the server's default time zone; {@code days} is the requested
 * length after clamping (1–366).
 */
public class RollingWindowAttendanceResponse {

    private final String startDate;
    private final String endDate;
    private final int days;
    private final int presentDays;
    private final int markedDays;
    private final Map<String, Boolean> entries;

    public RollingWindowAttendanceResponse(
            String startDate,
            String endDate,
            int days,
            int presentDays,
            int markedDays,
            Map<String, Boolean> entries) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.presentDays = presentDays;
        this.markedDays = markedDays;
        this.entries = entries;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public int getDays() {
        return days;
    }

    public int getPresentDays() {
        return presentDays;
    }

    public int getMarkedDays() {
        return markedDays;
    }

    public Map<String, Boolean> getEntries() {
        return entries;
    }
}
