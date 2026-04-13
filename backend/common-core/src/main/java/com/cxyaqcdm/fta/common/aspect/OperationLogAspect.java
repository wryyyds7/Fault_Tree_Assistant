package com.cxyaqcdm.fta.common.aspect;

import com.cxyaqcdm.fta.common.client.LogServiceClient;
import com.cxyaqcdm.fta.common.context.UserContext;
import com.cxyaqcdm.fta.common.entity.OperationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLogAspect {

    private final LogServiceClient logServiceClient;

    @Pointcut("execution(* com.cxyaqcdm.fta..controller..*.*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        OperationLog operationLog = new OperationLog();
        operationLog.setServiceName(extractServiceName(className));
        operationLog.setOperationType(methodName);
        operationLog.setRequestMethod("HTTP");
        operationLog.setRequestPath(joinPoint.getSignature().toShortString());
        operationLog.setRequestParams(buildRequestParams(joinPoint));
        operationLog.setLogLevel("INFO");
        operationLog.setCreateTime(LocalDateTime.now());

        UserContext userContext = UserContext.getCurrentUser();
        if (userContext != null) {
            operationLog.setUserId(userContext.getUserId());
            operationLog.setUsername(userContext.getUsername());
        }

        Object result = null;
        Integer responseStatus = 200;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            responseStatus = 500;
            operationLog.setLogLevel("ERROR");
            operationLog.setOperationDetail("Error: " + e.getMessage());
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            operationLog.setExecutionTime(executionTime);
            operationLog.setResponseStatus(responseStatus);

            try {
                logServiceClient.writeLog(operationLog);
            } catch (Exception e) {
                log.error("Failed to write operation log", e);
            }
        }
    }

    private String extractServiceName(String className) {
        if (className.endsWith("Controller")) {
            return className.substring(0, className.length() - 10);
        }
        return className;
    }

    private String buildRequestParams(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            Object arg = args[i];
            if (arg != null) {
                String argStr = arg.toString();
                if (argStr.length() > 500) {
                    argStr = argStr.substring(0, 500) + "...";
                }
                sb.append("param").append(i).append(":").append(argStr);
            } else {
                sb.append("param").append(i).append(":null");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
