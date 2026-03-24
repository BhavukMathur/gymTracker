package com.gymtracker.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gymtracker.dto.AttendanceRequest;
import com.gymtracker.model.Attendance;
import com.gymtracker.model.User;
import com.gymtracker.repository.AttendanceRepository;
import com.gymtracker.repository.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceController(UserRepository userRepository, AttendanceRepository attendanceRepository) {
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @PostMapping
    public Map<String, Object> saveAttendance(@Valid @RequestBody AttendanceRequest request, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Attendance attendance = attendanceRepository.findByUserAndAttendanceDate(user, request.getDate())
                .orElseGet(Attendance::new);
        attendance.setUser(user);
        attendance.setAttendanceDate(request.getDate());
        attendance.setAttended(request.isAttended());
        attendanceRepository.save(attendance);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", request.getDate());
        response.put("attended", request.isAttended());
        response.put("message", "Attendance saved");
        return response;
    }

    @GetMapping("/month")
    public Map<String, Boolean> getMonthAttendance(@RequestParam int year, @RequestParam int month, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        Map<String, Boolean> attendanceMap = new LinkedHashMap<>();
        attendanceRepository.findByUserAndAttendanceDateBetween(user, start, end)
                .forEach(record -> attendanceMap.put(record.getAttendanceDate().toString(), record.isAttended()));
        return attendanceMap;
    }
}
