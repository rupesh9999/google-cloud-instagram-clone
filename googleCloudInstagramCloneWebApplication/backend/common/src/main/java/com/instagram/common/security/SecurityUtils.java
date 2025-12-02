package com.instagram.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public static UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }

    public static String getCurrentUserId() {
        UserPrincipal principal = getCurrentUser();
        return principal != null ? principal.getId() : null;
    }

    public static String getCurrentUsername() {
        UserPrincipal principal = getCurrentUser();
        return principal != null ? principal.getUsername() : null;
    }

    public static boolean isAuthenticated() {
        return getCurrentUser() != null;
    }
}
