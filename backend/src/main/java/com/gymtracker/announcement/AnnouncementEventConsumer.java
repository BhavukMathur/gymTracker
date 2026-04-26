package com.gymtracker.announcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymtracker.config.KafkaAnnouncementConfig;

@Component
public class AnnouncementEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementEventConsumer.class);

    private final AnnouncementRepository announcementRepository;
    private final ObjectMapper objectMapper;

    public AnnouncementEventConsumer(
            AnnouncementRepository announcementRepository,
            ObjectMapper objectMapper) {
        this.announcementRepository = announcementRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaAnnouncementConfig.ANNOUNCEMENT_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "announcementKafkaListenerContainerFactory")
    @Transactional
    public void onAnnouncement(
            @Payload String json,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        AnnouncementPublishedEvent event;
        try {
            event = objectMapper.readValue(json, AnnouncementPublishedEvent.class);
        } catch (JsonProcessingException e) {
            log.warn("invalid announcement json: {}", e.getMessage());
            return;
        }
        if (event.getEventId() == null) {
            log.warn("announcement event missing eventId, key={}", key);
            return;
        }
        if (announcementRepository.findByEventId(event.getEventId()).isPresent()) {
            return;
        }
        Announcement row = new Announcement();
        row.setEventId(event.getEventId());
        row.setTitle(event.getTitle() != null ? event.getTitle() : "");
        row.setBody(event.getBody() != null ? event.getBody() : "");
        row.setCreatedBy(event.getCreatedBy() != null ? event.getCreatedBy() : "system");
        row.setCreatedAt(event.getPublishedAt() != null ? event.getPublishedAt() : java.time.Instant.now());
        try {
            announcementRepository.save(row);
        } catch (DataIntegrityViolationException ex) {
            log.debug("dedupe on eventId={}", event.getEventId());
        }
    }
}
