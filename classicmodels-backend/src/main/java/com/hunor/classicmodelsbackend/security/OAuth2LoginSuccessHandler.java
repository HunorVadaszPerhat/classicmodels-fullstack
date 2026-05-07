package com.hunor.classicmodelsbackend.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridge between "Google said yes" and "the rest of the app uses our
 * own JWTs."
 *
 * <h3>Why a custom handler at all?</h3>
 *
 * <p>Spring Security's default OAuth2 success behavior is to drop the
 * user on a server-rendered page. That's fine for a server-side MVC
 * app — useless for a single-page app whose authenticated state lives
 * client-side as a JWT in localStorage.</p>
 *
 * <p>So we intercept the success: extract the user info Google gave
 * us, mint our own JWT exactly like {@code POST /auth/login} does for
 * username/password, and redirect the browser back to the SPA with
 * the token in a URL fragment.</p>
 *
 * <h3>Why URL fragment, not query string?</h3>
 *
 * <p>{@code /login/oauth2/success#token=ABC123} vs.
 * {@code /login/oauth2/success?token=ABC123}.</p>
 *
 * <p>Query parameters are sent to servers (proxies, the SPA's host
 * server, any redirect target) and end up in access logs. URL
 * fragments are <i>not</i> sent in the HTTP request — they're a
 * client-only construct. JavaScript reads {@code window.location.hash}
 * and the server never sees the value. For a sensitive token, fragment
 * is the safer default.</p>
 *
 * <h3>What about a refresh token / cookie?</h3>
 *
 * <p>The same trade-off space as Stage 3 of the auth feature: we keep
 * the SPA stateless and store the access token in localStorage. A
 * production system might issue a short-lived access token plus a
 * refresh token in an HttpOnly cookie. Out of scope for this teaching
 * project; doc points to the alternatives.</p>
 *
 * <h3>Provisioning</h3>
 *
 * <p>This handler does NOT verify the email exists in our own user
 * table — we treat any Google-authenticated user as a valid app user
 * with the {@code USER} role. Real apps usually do one of:</p>
 *
 * <ul>
 *   <li><b>Auto-provision</b> — create a row on first sign-in (what
 *       we'd do here in production).</li>
 *   <li><b>Pre-provision</b> — only allowed if an admin has already
 *       added the email to the app's user table.</li>
 *   <li><b>Allow-list domain</b> — accept any user with @yourcompany.com.</li>
 * </ul>
 */
@Component
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwt;
    private final String successRedirect;

    public OAuth2LoginSuccessHandler(
            JwtService jwt,
            @Value("${app.oauth2.success-redirect:/login/oauth2/success}") String successRedirect) {
        this.jwt = jwt;
        this.successRedirect = successRedirect;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // The principal is an OAuth2User holding all the attributes
        // Google returned (sub, email, name, picture, locale, etc.).
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();

        // Email is the natural cross-system identifier. Google
        // guarantees email in the userinfo response when the "email"
        // scope is granted; we requested it in application.yml.
        String email = principal.getAttribute("email");
        if (email == null) {
            log.warn("OAuth2 login succeeded but no email in attributes; falling back to subject");
            email = principal.getAttribute("sub");
        }

        // For learning, every OAuth2-authenticated user gets ROLE_USER.
        // Production code might map roles based on email allow-lists,
        // a user table, or claims that come from the IdP.
        List<String> roles = List.of("ROLE_USER");
        String token = jwt.issue(email, roles);

        log.info("Issued JWT for OAuth2 user {} (roles={})", email, roles);

        // sendRedirect forces a 302 with the encoded URL. The token
        // lives in the fragment so it never hits a server log.
        // URL-encode the token defensively even though Base64URL is
        // already URL-safe — belt-and-braces against future format
        // changes.
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String target  = successRedirect + "#token=" + encoded;
        response.sendRedirect(target);
    }
}
