package com.gymtracker.dto;

public class PublishAnnouncementResult {

    private final String eventId;
    private final String message;

    public PublishAnnouncementResult(String eventId, String message) {
        this.eventId = eventId;
        this.message = message;
    }

    public String getEventId() {
        return eventId;
    }

    public String getMessage() {
        return message;
    }
}
