package com.ming.agent12306.plan.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.agent12306.plan.model.PlanStepContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

@Aspect
@Component
public class PlanStepLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(PlanStepLoggingAspect.class);
    private static final int MAX_LOG_LENGTH = 400;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(planStepLog)")
    public Object around(ProceedingJoinPoint joinPoint, PlanStepLog planStepLog) throws Throwable {
        String sessionId = extractSessionId(joinPoint.getArgs());
        String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        String input = abbreviate(Arrays.stream(joinPoint.getArgs())
                .filter(arg -> !(arg instanceof PlanStepContext))
                .map(this::serialize)
                .collect(Collectors.joining(", ")));

        log.info("[plan-aop] session={} step={} method={} input={}", sessionId, planStepLog.value().code(), methodName, input);
        try {
            Object result = joinPoint.proceed();
            log.info("[plan-aop] session={} step={} method={} output={}", sessionId, planStepLog.value().code(), methodName, abbreviate(serialize(result)));
            return result;
        } catch (Throwable ex) {
            log.error("[plan-aop] session={} step={} method={} error={}", sessionId, planStepLog.value().code(), methodName, ex.getMessage(), ex);
            throw ex;
        }
    }

    private String extractSessionId(Object[] args) {
        if (args == null) {
            return "unknown";
        }
        for (Object arg : args) {
            if (arg instanceof PlanStepContext context && StringUtils.hasText(context.sessionId())) {
                return context.sessionId();
            }
        }
        return "unknown";
    }

    private String serialize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "无";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > MAX_LOG_LENGTH ? normalized.substring(0, MAX_LOG_LENGTH) + "..." : normalized;
    }
}
