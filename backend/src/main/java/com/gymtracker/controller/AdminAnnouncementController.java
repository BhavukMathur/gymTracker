package com.gymtracker.controller;

import java.util.concurrent.ExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gymtracker.announcement.AnnouncementEventPublisher;
import com.gymtracker.dto.AnnouncementRequest;
import com.gymtracker.dto.PublishAnnouncementResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/announcements")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnnouncementController {

    private final AnnouncementEventPublisher announcementEventPublisher;

    public AdminAnnouncementController(AnnouncementEventPublisher announcementEventPublisher) {
        this.announcementEventPublisher = announcementEventPublisher;
    }

    @PostMapping
    public ResponseEntity<PublishAnnouncementResult> publish(
            @Valid @RequestBody AnnouncementRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String createdBy = authentication.getName();
        try {
            String eventId = announcementEventPublisher.publish(request, createdBy);
            return ResponseEntity
                    .accepted()
                    .body(new PublishAnnouncementResult(eventId, "Event accepted for processing"));
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (ExecutionException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
