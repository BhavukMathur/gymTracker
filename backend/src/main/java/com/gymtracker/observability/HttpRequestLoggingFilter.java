package com.gymtracker.observability;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet-layer logging: runs for every HTTP exchange (REST and GraphQL).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    private static final String MDC_REQUEST_ID = "requestId";

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public HttpRequestLoggingFilter(MeterRegistry meterRegistry, Tracer tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        long start = System.nanoTime();
        String method = request.getMethod();
        String query = request.getQueryString();

        log.info("[Filter] incoming {} {}{}", method, uri, query != null ? "?" + query : "");

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            int status = response.getStatus();
            log.info("[Filter] completed {} {} status={} durationMs={}", method, uri, status, durationMs);

            String traceId = "n/a";
            String spanId = "n/a";
            var current = tracer.currentSpan();
            if (current != null) {
                traceId = current.context().traceId();
                spanId = current.context().spanId();
            }
            log.info("[Tracing] traceId={} spanId={} (Micrometer Brave bridge; see MDC traceId/spanId in Logback pattern)",
                    traceId, spanId);

            meterRegistry.counter("gymtracker.observability.demo.requests",
                    "method", method,
                    "uri", normalizeUri(uri),
                    "status", String.valueOf(status)).increment();
            log.info("[Actuator/Micrometer] incremented counter gymtracker.observability.demo.requests method={} uri={} status={}",
                    method, uri, status);

            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private static String normalizeUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "unknown";
        }
        if (uri.length() > 120) {
            return uri.substring(0, 120) + "…";
        }
        return uri;
    }
}
