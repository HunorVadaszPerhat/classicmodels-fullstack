package com.hunor.classicmodelsbackend.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tiny utility for "who is currently making this request?".
 *
 * <p>Reads from Spring Security's {@link SecurityContextHolder}, which
 * stores the authenticated principal in a {@code ThreadLocal} for the
 * duration of the request. The {@code JwtAuthFilter} (Stage 3 of our
 * Spring Security setup) populates that context on every request that
 * carries a valid JWT.</p>
 *
 * <h3>Why a static method?</h3>
 *
 * <p>The repositories that need this aren't Spring beans in the
 * traditional sense — they want to know "who's calling this code"
 * without taking on a {@code SecurityContext} dependency in their
 * constructor. A static method fits that "context-aware utility"
 * shape cleanly.</p>
 *
 * <p>The downside of statics is they're hard to mock in tests. For
 * production-grade code you'd inject something like
 * {@code Supplier<String>} or a {@code AuditorAware<String>} bean
 * (which is exactly what Spring Data JPA does). For our learning
 * version, the static is fine and obvious.</p>
 */
public final class CurrentUser {

    private CurrentUser() { /* static-only */ }

    /**
     * The username of the currently authenticated user, or
     * {@code "system"} if no one is logged in (e.g., the request was
     * unauthenticated, or this code is running outside a request
     * context like during a startup migration).
     */
    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }
}
