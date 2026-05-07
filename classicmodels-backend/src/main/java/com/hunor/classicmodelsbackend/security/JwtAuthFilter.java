package com.hunor.classicmodelsbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Custom Servlet filter that runs before Spring Security's authorization
 * checks on every request. Its job:
 *
 * <ol>
 *   <li>Look for an {@code Authorization: Bearer ...} header.</li>
 *   <li>If present, verify the JWT via {@link JwtService}.</li>
 *   <li>On success, build an {@link UsernamePasswordAuthenticationToken}
 *       from the token's claims and put it in the
 *       {@link SecurityContextHolder}, signalling "this request is
 *       authenticated as user X with roles Y."</li>
 *   <li>If the header is missing or the token is bad, do nothing — the
 *       request continues unauthenticated, and Spring Security's
 *       authorization filter will reject it with 401 if the endpoint
 *       requires auth.</li>
 * </ol>
 *
 * <p>{@link OncePerRequestFilter} guarantees the filter only runs once
 * per request even if the chain re-enters somehow. It's the standard
 * base class for custom auth filters.</p>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = jwt.verify(token);
                String username = claims.getSubject();
                List<SimpleGrantedAuthority> authorities = jwt.rolesOf(claims).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                UsernamePasswordAuthenticationToken auth =
                        UsernamePasswordAuthenticationToken.authenticated(
                                username, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));

                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException ex) {
                // Bad/expired token. Don't authenticate, but don't fail
                // hard either — fall through and let the authorization
                // filter reject with 401 if the endpoint demands auth.
                // We deliberately don't log at WARN here because brute-
                // force / scanner traffic would spam logs.
                logger.debug("JWT validation failed: " + ex.getMessage());
            }
        }

        chain.doFilter(req, res);
    }
}
