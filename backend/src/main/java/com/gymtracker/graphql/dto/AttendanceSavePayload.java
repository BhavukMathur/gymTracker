package com.gymtracker.graphql.dto;

public record AttendanceSavePayload(String date, boolean attended, String message) {
}
