package com.image.logs;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.aspectj.lang.annotation.Pointcut;

import java.time.LocalDateTime;
import java.util.Arrays;

@Aspect
@Component
@ConditionalOnProperty(prefix = "enable.common", name = "logging", havingValue = "true", matchIfMissing = false)
public class Log {

    private static final Logger log = LoggerFactory.getLogger(Log.class);

    @Pointcut("execution(public * com.image..*.*(..)) && !within(com.image.logs..*)")
    public void applicationPointcut() {}

    @Before("applicationPointcut()")
    public void beforeMethod(JoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        log.info("BEFORE - {}.{} - args: {} - time: {}",
                className, methodName, Arrays.toString(args), LocalDateTime.now());
    }

    @After("applicationPointcut()")
    public void afterMethod(JoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        log.info("AFTER - {}.{} - time: {}",
                className, methodName, LocalDateTime.now());
    }
}
