package com.gymtracker.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring MVC interceptor: runs around controller execution (not pure servlet static paths the same way).
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String ATTR_START_NS = RequestLoggingInterceptor.class.getName() + ".startNs";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START_NS, System.nanoTime());
        log.info("[Interceptor] preHandle {} {} handler={}", request.getMethod(), request.getRequestURI(), handler);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            @Nullable Exception ex) {
        Long startNs = (Long) request.getAttribute(ATTR_START_NS);
        long durationMs = startNs != null ? (System.nanoTime() - startNs) / 1_000_000L : -1L;
        log.info("[Interceptor] afterCompletion {} {} status={} durationMs={} exception={}",
                request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, ex != null);
    }
}
