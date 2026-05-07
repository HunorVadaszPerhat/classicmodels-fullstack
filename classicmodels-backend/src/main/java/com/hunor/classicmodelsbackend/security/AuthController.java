package com.hunor.classicmodelsbackend.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Authentication endpoints for the stateless / JWT setup.
 *
 * <p>Compare with the Stage 2 version: there's no session, no
 * SecurityContextRepository, no setting of cookies. Login returns the
 * token, and the client is responsible for sending it back on
 * subsequent requests.</p>
 *
 * <h3>The flow</h3>
 *
 * <ol>
 *   <li>{@code POST /auth/login} → AuthenticationManager validates the
 *       credentials → on success, JwtService issues a token →
 *       returned in the response body.</li>
 *   <li>Angular stores the token (we'll put it in localStorage) and
 *       attaches it as {@code Authorization: Bearer ...} on every
 *       subsequent request.</li>
 *   <li>{@link JwtAuthFilter} verifies the token and populates the
 *       security context per request. No server-side state.</li>
 *   <li>{@code GET /auth/me} reads the current {@link Authentication}
 *       from the security context — populated by the filter on the
 *       way in — and returns it.</li>
 *   <li>{@code POST /auth/logout} doesn't really do anything backend-
 *       side: there's no session to invalidate, no token denylist (we
 *       could add one but it'd reintroduce server state). Logout is a
 *       client-side action: the Angular code throws away the token.
 *       The endpoint exists so the API surface looks the same as in
 *       Stage 2 from the client's perspective.</li>
 * </ol>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthController(AuthenticationManager authManager, JwtService jwt) {
        this.authManager = authManager;
        this.jwt = jwt;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, String username, List<String> roles) {}
    public record UserInfo(String username, List<String> roles) {}

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest body) {
        Authentication authReq =
                UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password());
        Authentication authResult = authManager.authenticate(authReq);

        List<String> roles = authResult.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String token = jwt.issue(authResult.getName(), roles);

        return ResponseEntity.ok(new LoginResponse(token, authResult.getName(), roles));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // Stateless: we have nothing to invalidate. The client should
        // delete its token. Returning 200 keeps the API symmetric with
        // Stage 2 so the front-end doesn't need a special case.
        return ResponseEntity.ok(Map.of("status", "logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfo> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new UserInfo(
                auth.getName(),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
        ));
    }
}
