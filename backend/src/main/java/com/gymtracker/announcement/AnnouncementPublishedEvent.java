package com.gymtracker.announcement;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kafka payload for {@link KafkaAnnouncementConfig#ANNOUNCEMENT_TOPIC}.
 */
public class AnnouncementPublishedEvent {

    private String eventId;
    private String title;
    private String body;
    @JsonProperty("createdBy")
    private String createdBy;
    @JsonProperty("publishedAt")
    private Instant publishedAt;

    public AnnouncementPublishedEvent() {
    }

    public AnnouncementPublishedEvent(String eventId, String title, String body, String createdBy, Instant publishedAt) {
        this.eventId = eventId;
        this.title = title;
        this.body = body;
        this.createdBy = createdBy;
        this.publishedAt = publishedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnouncementPublishedEvent that = (AnnouncementPublishedEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(eventId);
    }
}
