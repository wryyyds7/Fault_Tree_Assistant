package com.cxyaqcdm.fta.common.interceptor;

import com.cxyaqcdm.fta.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_KEY = "X-User-Key";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(HEADER_USER_ID);
        String role = request.getHeader(HEADER_USER_ROLE);
        String userKey = request.getHeader(HEADER_USER_KEY);

        if (userId != null && !userId.isEmpty()) {
            UserContext userContext = new UserContext();
            userContext.setUsername(userId);
            userContext.setUserId(userKey != null ? userKey : userId);
            userContext.setRole(role);
            UserContext.setCurrentUser(userContext);
            log.debug("UserContext set: userId={}, userKey={}, role={}", userId, userKey, role);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        UserContext.clear();
    }
}
