package com.hunor.classicmodelsbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Issue + verify HS256-signed JWTs.
 *
 * <p>HS256 is symmetric: the same secret signs and verifies. That's
 * appropriate when one server both issues and validates tokens (our
 * setup). If you ever wanted multiple distinct services to verify
 * tokens issued by a central auth server, you'd switch to RS256
 * (asymmetric — private key signs, public key verifies).</p>
 *
 * <h3>What's in the token</h3>
 *
 * <pre>
 *   header  : { "alg": "HS256", "typ": "JWT" }
 *   payload : {
 *     "sub":  "&lt;username&gt;",     ← who the token represents
 *     "roles": ["ROLE_ADMIN", ...], ← copy of authorities at issue time
 *     "iat":  &lt;issued-at epoch&gt;,
 *     "exp":  &lt;expiry epoch&gt;
 *   }
 *   signature: HMAC-SHA256(header + payload, secret)
 * </pre>
 *
 * <p>Anyone holding the token can decode the payload (it's just base64;
 * the signature only verifies authenticity, not confidentiality). So
 * never put secrets in a JWT.</p>
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        // hmacShaKeyFor requires the byte array to be at least the
        // algorithm's key size (256 bits = 32 bytes for HS256). Short
        // secrets fail loudly here at startup, which is what you want.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * Issue a token for the given user and authorities. Authorities are
     * stored as a list of strings under the "roles" claim.
     */
    public String issue(String username, Collection<String> authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("roles", authorities)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verify the token's signature and expiry, returning the claims if
     * valid. Throws {@link io.jsonwebtoken.JwtException} on invalid
     * signature, expired token, malformed payload, etc.
     */
    public Claims verify(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    /**
     * Convenience: pull the roles claim back out as a list of strings.
     * Returns an empty list if the claim is missing or malformed.
     */
    public List<String> rolesOf(Claims claims) {
        Object raw = claims.get("roles");
        if (raw instanceof Collection<?> c) {
            return c.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }
}
