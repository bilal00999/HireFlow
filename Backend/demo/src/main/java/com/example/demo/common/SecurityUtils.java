package com.example.demo.common;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Convenience accessors for the currently authenticated principal.
 * The JWT filter stores the user/company id (a UUID string) as the principal.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** The authenticated principal's id, or null if the request is anonymous. */
    public static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }

    /** Returns the authenticated role name without the ROLE_ prefix, or null for anonymous requests. */
    public static String currentRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }

        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.startsWith("ROLE_")) {
                return value.substring("ROLE_".length());
            }
        }

        return null;
    }
}
