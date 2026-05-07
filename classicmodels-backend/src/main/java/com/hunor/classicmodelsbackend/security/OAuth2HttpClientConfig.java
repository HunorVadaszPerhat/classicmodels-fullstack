package com.hunor.classicmodelsbackend.security;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;

import lombok.extern.slf4j.Slf4j;

/**
 * Replaces Spring Security's default OAuth2 token-exchange and
 * userinfo-fetch HTTP clients with versions that can optionally
 * skip TLS certificate verification.
 *
 * <h3>Why this exists</h3>
 *
 * <p>Spring Security's OAuth2 client makes two server-to-server HTTPS
 * calls during a successful login:</p>
 *
 * <ol>
 *   <li>Token exchange — POST {@code https://oauth2.googleapis.com/token}
 *       with the {@code code} we got from the auth callback, plus our
 *       client_id and client_secret. Returns an access token + ID token.</li>
 *   <li>User info — GET {@code https://openidconnect.googleapis.com/v1/userinfo}
 *       with the access token. Returns email, name, sub, etc.</li>
 * </ol>
 *
 * <p>On corporate networks that run a TLS-intercepting proxy (Zscaler,
 * Bluecoat, Netskope, etc.), both of those calls fail with
 * {@code PKIX path building failed} — the proxy re-signs every TLS
 * response with a private root CA the JVM doesn't trust. Same problem
 * we hit on Nominatim in Feature 4, same workaround pattern.</p>
 *
 * <p>If {@code app.oauth2.trust-all-certs=true}, this configuration
 * exposes overridden {@link OAuth2AccessTokenResponseClient} and
 * {@link OAuth2UserService} beans whose underlying HTTP machinery
 * trusts every certificate. Spring Security picks them up
 * automatically — it auto-wires whatever beans of those types are in
 * the context, falling back to defaults if none are.</p>
 *
 * <p>If the flag is off (the default), this class doesn't define the
 * beans at all and Spring Security uses its own defaults. So the
 * cost on a normal-network machine is zero.</p>
 *
 * <h3>Production hardening</h3>
 *
 * <p>The proper fix is to import the corporate root CA into the JVM's
 * {@code cacerts} (or use a custom trust store via
 * {@code javax.net.ssl.trustStore}). The flag below is purely a
 * developer-machine convenience.</p>
 */
@Configuration
@Slf4j
public class OAuth2HttpClientConfig {

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oauth2TokenResponseClient(
            @Value("${app.oauth2.trust-all-certs:false}") boolean trustAllCerts) {

        var client = new DefaultAuthorizationCodeTokenResponseClient();
        if (trustAllCerts) {
            log.warn("TLS certificate verification is DISABLED for OAuth2 token exchange. " +
                    "This is a development workaround only — never enable in production.");
            // Spring still uses a RestTemplate internally for the token
            // endpoint call (RestClient under the hood would also work
            // but the v6 API still expects a RestOperations). Build a
            // minimal RestTemplate with the trust-all factory plus the
            // converters Spring's default would have set up.
            RestTemplate restTemplate = new RestTemplate(java.util.List.of(
                    new FormHttpMessageConverter(),
                    new OAuth2AccessTokenResponseHttpMessageConverter()
            ));
            restTemplate.setRequestFactory(buildTrustAllRequestFactory());
            restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
            client.setRestOperations(restTemplate);
        }
        return client;
    }

    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService(
            @Value("${app.oauth2.trust-all-certs:false}") boolean trustAllCerts) {

        var service = new DefaultOAuth2UserService();
        if (trustAllCerts) {
            // The userinfo endpoint also goes through the corporate
            // proxy — trust-all here too. Same justification as above.
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setRequestFactory(buildTrustAllRequestFactory());
            service.setRestOperations(restTemplate);
        }
        return service;
    }

    /**
     * Builds a Spring HTTP request factory whose underlying
     * {@link HttpClient} accepts every TLS certificate. Identical
     * pattern to {@code GeocodingService.buildTrustAllRequestFactory}.
     */
    private static JdkClientHttpRequestFactory buildTrustAllRequestFactory() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { /* trust everyone */ }
                        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { /* trust everyone */ }
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());

            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(ctx)
                    .build();
            return new JdkClientHttpRequestFactory(httpClient);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build trust-all SSL context for OAuth2", e);
        }
    }
}
