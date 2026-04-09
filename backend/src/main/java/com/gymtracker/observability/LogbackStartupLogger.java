package com.gymtracker.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Demonstrates that application messages flow through Logback (see {@code logback-spring.xml}).
 */
@Component
public class LogbackStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LogbackStartupLogger.class);

    @Override
    public void run(ApplicationArguments args) {
        log.info("[Logback] GymTracker logging uses logback-spring.xml (appenders, levels, MDC keys: requestId, traceId, spanId)");
        log.info("[Actuator] Micrometer metrics: GET /actuator/metrics, GET /actuator/prometheus; per-request counter gymtracker.observability.demo.requests is logged as [Actuator/Micrometer] in HttpRequestLoggingFilter");
    }
}
