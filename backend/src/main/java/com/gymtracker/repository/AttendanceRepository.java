package com.gymtracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gymtracker.model.Attendance;
import com.gymtracker.model.User;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByUserAndAttendanceDate(User user, LocalDate attendanceDate);

    List<Attendance> findByUserAndAttendanceDateBetween(User user, LocalDate start, LocalDate end);
}
