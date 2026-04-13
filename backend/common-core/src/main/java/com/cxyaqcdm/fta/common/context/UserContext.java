package com.cxyaqcdm.fta.common.context;

import lombok.Data;

@Data
public class UserContext {

    private String userId;
    private String username;
    private String role;

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void setCurrentUser(UserContext userContext) {
        CONTEXT.set(userContext);
    }

    public static UserContext getCurrentUser() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
