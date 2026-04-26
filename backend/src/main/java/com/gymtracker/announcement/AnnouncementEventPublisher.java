package com.gymtracker.announcement;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymtracker.config.KafkaAnnouncementConfig;
import com.gymtracker.dto.AnnouncementRequest;

@Service
public class AnnouncementEventPublisher {

    private final KafkaTemplate<String, String> announcementKafkaTemplate;
    private final ObjectMapper objectMapper;

    public AnnouncementEventPublisher(
            KafkaTemplate<String, String> announcementKafkaTemplate,
            ObjectMapper objectMapper) {
        this.announcementKafkaTemplate = announcementKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public String publish(AnnouncementRequest request, String createdBy) throws JsonProcessingException, ExecutionException, InterruptedException {
        String eventId = UUID.randomUUID().toString();
        AnnouncementPublishedEvent event = new AnnouncementPublishedEvent(
                eventId,
                request.getTitle().trim(),
                request.getBody().trim(),
                createdBy,
                Instant.now());
        String json = objectMapper.writeValueAsString(event);
        SendResult<String, String> result = announcementKafkaTemplate
                .send(KafkaAnnouncementConfig.ANNOUNCEMENT_TOPIC, eventId, json)
                .get();
        if (result.getRecordMetadata() == null) {
            throw new IllegalStateException("no metadata for sent announcement");
        }
        return eventId;
    }
}
