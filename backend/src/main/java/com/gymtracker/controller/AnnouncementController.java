package com.gymtracker.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gymtracker.announcement.AnnouncementRepository;
import com.gymtracker.dto.AnnouncementResponse;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private static final int MAX_ANNOUNCEMENTS = 50;

    private final AnnouncementRepository announcementRepository;

    public AnnouncementController(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    @GetMapping
    public List<AnnouncementResponse> list() {
        return announcementRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, MAX_ANNOUNCEMENTS))
                .stream()
                .map(AnnouncementResponse::from)
                .toList();
    }
}
