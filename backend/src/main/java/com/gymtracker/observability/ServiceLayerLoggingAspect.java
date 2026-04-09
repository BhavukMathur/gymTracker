package com.gymtracker.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting logs around {@link com.gymtracker.service} methods (AOP).
 */
@Aspect
@Component
public class ServiceLayerLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceLayerLoggingAspect.class);

    @Around("execution(* com.gymtracker.service..*(..))")
    public Object logAroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        String sig = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        log.info("[AOP] before {} args.length={}", sig, args.length);
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            log.info("[AOP] after {} durationMs={} returnType={}", sig, durationMs,
                    result != null ? result.getClass().getSimpleName() : "void");
            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            log.warn("[AOP] exception {} durationMs={} type={}", sig, durationMs, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
