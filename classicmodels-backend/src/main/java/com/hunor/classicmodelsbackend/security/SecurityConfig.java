package com.hunor.classicmodelsbackend.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * STAGE 3 — Stateless JWT.
 *
 * <p>Differences from Stage 2:</p>
 * <ul>
 *   <li>{@code SessionCreationPolicy.STATELESS} — Spring Security will
 *       not create or read a session. Each request stands alone.</li>
 *   <li>Custom {@link JwtAuthFilter} placed before the username/password
 *       filter. It reads the {@code Authorization: Bearer} header,
 *       validates the JWT, and populates the security context.</li>
 *   <li>CSRF disabled. CSRF defends against cookie-mounted attacks; with
 *       JWTs in headers (not cookies) there's nothing to forge.</li>
 *   <li>The auth controller now returns a token instead of setting a
 *       session cookie — see {@link AuthController}.</li>
 * </ul>
 */
@Configuration
@Slf4j
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2LoginSuccessHandler oauth2SuccessHandler;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          OAuth2LoginSuccessHandler oauth2SuccessHandler,
                          ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.clientRegistrations = clientRegistrations;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // IF_REQUIRED rather than STATELESS — Spring's OAuth2 login
            // filter (Feature 15) needs a short-lived session to store
            // the `state` nonce + PKCE values across the redirect to
            // Google. With STATELESS, that storage is silently dropped,
            // and when the callback arrives Spring can't verify state →
            // our failure handler returns 401.
            //
            // IF_REQUIRED means a session is created only when something
            // explicitly needs it. The JWT path never asks for a session,
            // so it stays effectively stateless — only the OAuth2
            // round-trip uses a session, and only for the few seconds
            // between the redirect to Google and the callback back.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .csrf(csrf -> csrf.disable())  // No cookies → no CSRF surface.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // STOMP over WebSocket. See WebSocketConfig for the
                // auth-related caveat: WS is unauthenticated in this
                // learning version. Production would add a STOMP
                // channel interceptor reading the JWT from CONNECT.
                .requestMatchers("/ws/**").permitAll()
                // OAuth2 init + callback URLs need to be reachable
                // before authentication has happened — they're how
                // unauthenticated users start the sign-in flow and
                // come back from Google.
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // Actuator endpoints (Feature 16):
                //   - /health, /info, /prometheus are public so load
                //     balancers, k8s probes, and Prometheus scrapers
                //     can reach them without credentials. In a real
                //     production cluster you'd typically restrict
                //     /prometheus to the metrics-server's network
                //     segment via firewall rules rather than via
                //     application auth.
                //   - Everything else under /actuator/** (env, beans,
                //     mappings, threaddump, loggers) leaks sensitive
                //     details and requires ADMIN.
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            ;

        // OAuth2 login wires the authorization-code flow:
        //   GET /oauth2/authorization/google  → redirect to Google
        //   GET /login/oauth2/code/google     → handle Google's callback
        // Spring's auto-config creates the filters and the AuthenticationManager;
        // we just plug in our custom success handler that mints a JWT and
        // 302s back to the SPA.
        //
        // Activated CONDITIONALLY — only if a ClientRegistrationRepository
        // bean exists in the context. That bean is auto-configured only
        // when at least one valid OAuth2 client is registered (e.g. by
        // the 'local' profile loading application-local.yml). Without it,
        // skipping oauth2Login() lets the app start in profiles that
        // don't carry Google credentials (CI, demo, prod-without-OAuth).
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                .successHandler(oauth2SuccessHandler)
                .failureHandler((req, res, e) -> {
                    // Log the full exception (cause + stack) so we can see
                    // exactly why Google rejected us during the callback —
                    // invalid_client, invalid_grant, TLS issues, etc.
                    log.error("OAuth2 login failed: {}", e.getMessage(), e);
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                            "OAuth2 login failed: " + e.getMessage());
                })
            );
        }

        http
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                        res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            )
            // Slot our JWT filter in before the standard username/password
            // filter. By the time any subsequent filter (authorization)
            // runs, the security context is already populated if the
            // request had a valid token.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN", "USER")
                .build();
        UserDetails user = User.builder()
                .username("user")
                .password(encoder.encode("user123"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(admin, user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
