package com.gymtracker.dto;

import java.time.Instant;

import com.gymtracker.announcement.Announcement;

public class AnnouncementResponse {

    private Long id;
    private String title;
    private String body;
    private String createdBy;
    private Instant createdAt;

    public static AnnouncementResponse from(Announcement a) {
        AnnouncementResponse r = new AnnouncementResponse();
        r.id = a.getId();
        r.title = a.getTitle();
        r.body = a.getBody();
        r.createdBy = a.getCreatedBy();
        r.createdAt = a.getCreatedAt();
        return r;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
