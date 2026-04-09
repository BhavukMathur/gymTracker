package com.gymtracker.graphql;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import com.gymtracker.graphql.dto.AttendanceDay;
import com.gymtracker.graphql.dto.AttendanceSavePayload;
import com.gymtracker.model.User;
import com.gymtracker.repository.UserRepository;
import com.gymtracker.service.AttendanceService;

@Controller
public class AttendanceGraphqlController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceGraphqlController.class);

    private final UserRepository userRepository;
    private final AttendanceService attendanceService;

    public AttendanceGraphqlController(UserRepository userRepository, AttendanceService attendanceService) {
        this.userRepository = userRepository;
        this.attendanceService = attendanceService;
    }

    @QueryMapping
    public List<AttendanceDay> attendanceMonth(@Argument int year, @Argument int month) {
        User user = requireUser();
        log.info("[SLF4J] GraphQL attendanceMonth year={} month={} user={}", year, month, user.getUsername());
        return attendanceService.listAttendanceDays(user, year, month);
    }

    @MutationMapping
    public AttendanceSavePayload saveAttendance(@Argument String date, @Argument boolean attended) {
        User user = requireUser();
        LocalDate localDate = LocalDate.parse(date);
        log.info("[SLF4J] GraphQL saveAttendance date={} attended={} user={}", date, attended, user.getUsername());
        attendanceService.upsertAttendance(user, localDate, attended);
        return new AttendanceSavePayload(localDate.toString(), attended, "Attendance saved");
    }

    private User requireUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
