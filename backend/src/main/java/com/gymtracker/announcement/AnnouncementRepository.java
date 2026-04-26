package com.gymtracker.announcement;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Optional<Announcement> findByEventId(String eventId);

    List<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
