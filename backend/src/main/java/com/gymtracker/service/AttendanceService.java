package com.gymtracker.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.gymtracker.dto.AttendanceRequest;
import com.gymtracker.graphql.dto.AttendanceDay;
import com.gymtracker.model.Attendance;
import com.gymtracker.model.User;
import com.gymtracker.repository.AttendanceRepository;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    public AttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public Map<String, Object> saveAttendance(User user, AttendanceRequest request) {
        upsertAttendance(user, request.getDate(), request.isAttended());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", request.getDate());
        response.put("attended", request.isAttended());
        response.put("message", "Attendance saved");
        return response;
    }

    public void upsertAttendance(User user, LocalDate date, boolean attended) {
        Attendance attendance = attendanceRepository.findByUserAndAttendanceDate(user, date)
                .orElseGet(Attendance::new);
        attendance.setUser(user);
        attendance.setAttendanceDate(date);
        attendance.setAttended(attended);
        attendanceRepository.save(attendance);
    }

    public Map<String, Boolean> getMonthAttendance(User user, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        Map<String, Boolean> attendanceMap = new LinkedHashMap<>();
        attendanceRepository.findByUserAndAttendanceDateBetween(user, start, end)
                .forEach(record -> attendanceMap.put(record.getAttendanceDate().toString(), record.isAttended()));
        return attendanceMap;
    }

    public List<AttendanceDay> listAttendanceDays(User user, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        return attendanceRepository.findByUserAndAttendanceDateBetween(user, start, end).stream()
                .map(a -> new AttendanceDay(a.getAttendanceDate().toString(), a.isAttended()))
                .toList();
    }

    public Map<String, Object> clearAttendance(User user, LocalDate date) {
        attendanceRepository.findByUserAndAttendanceDate(user, date)
                .ifPresent(attendanceRepository::delete);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", date);
        response.put("message", "Attendance cleared");
        return response;
    }
}
