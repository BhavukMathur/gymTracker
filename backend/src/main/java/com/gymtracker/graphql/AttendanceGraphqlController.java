package com.gymtracker.graphql;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import com.gymtracker.graphql.dto.AttendanceDay;
import com.gymtracker.graphql.dto.AttendanceSavePayload;
import com.gymtracker.model.Attendance;
import com.gymtracker.model.User;
import com.gymtracker.repository.AttendanceRepository;
import com.gymtracker.repository.UserRepository;

@Controller
public class AttendanceGraphqlController {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceGraphqlController(UserRepository userRepository, AttendanceRepository attendanceRepository) {
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @QueryMapping
    public List<AttendanceDay> attendanceMonth(@Argument int year, @Argument int month) {
        User user = requireUser();
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        return attendanceRepository.findByUserAndAttendanceDateBetween(user, start, end).stream()
                .map(a -> new AttendanceDay(a.getAttendanceDate().toString(), a.isAttended()))
                .toList();
    }

    @MutationMapping
    public AttendanceSavePayload saveAttendance(@Argument String date, @Argument boolean attended) {
        User user = requireUser();
        LocalDate localDate = LocalDate.parse(date);

        Attendance attendance = attendanceRepository.findByUserAndAttendanceDate(user, localDate)
                .orElseGet(Attendance::new);
        attendance.setUser(user);
        attendance.setAttendanceDate(localDate);
        attendance.setAttended(attended);
        attendanceRepository.save(attendance);

        return new AttendanceSavePayload(localDate.toString(), attended, "Attendance saved");
    }

    private User requireUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
