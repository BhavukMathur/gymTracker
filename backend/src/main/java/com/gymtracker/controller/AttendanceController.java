package com.gymtracker.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gymtracker.dto.AttendanceRequest;
import com.gymtracker.dto.RollingWindowAttendanceResponse;
import com.gymtracker.model.User;
import com.gymtracker.repository.UserRepository;
import com.gymtracker.service.AttendanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/attendance")
@SecurityRequirement(name = "bearer-jwt")
public class AttendanceController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceController.class);

    private final UserRepository userRepository;
    private final AttendanceService attendanceService;

    public AttendanceController(UserRepository userRepository, AttendanceService attendanceService) {
        this.userRepository = userRepository;
        this.attendanceService = attendanceService;
    }

    @PostMapping
    public Map<String, Object> saveAttendance(@Valid @RequestBody AttendanceRequest request, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        log.info("[SLF4J] REST saveAttendance user={} date={} attended={}", principal.getName(), request.getDate(),
                request.isAttended());
        return attendanceService.saveAttendance(user, request);
    }

    @GetMapping("/month")
    public Map<String, Boolean> getMonthAttendance(@RequestParam int year, @RequestParam int month, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        log.info("[SLF4J] REST getMonthAttendance user={} year={} month={}", principal.getName(), year, month);
        return attendanceService.getMonthAttendance(user, year, month);
    }

    @GetMapping("/rolling")
    @Operation(summary = "Rolling window attendance", description = "Read-only: attendance for the last N calendar days through today (inclusive).")
    public RollingWindowAttendanceResponse getRollingAttendance(
            @RequestParam(name = "days", defaultValue = "30") @Min(1) @Max(366) int days,
            Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        log.info("[SLF4J] REST getRollingAttendance user={} days={}", principal.getName(), days);
        return attendanceService.getRollingWindow(user, days);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> clearAttendance(@RequestParam LocalDate date, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        log.info("[SLF4J] REST clearAttendance user={} date={}", principal.getName(), date);
        return attendanceService.clearAttendance(user, date);
    }
}
