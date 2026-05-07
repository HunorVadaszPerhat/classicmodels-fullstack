package com.hunor.classicmodelsbackend.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Address-to-coordinates lookup via Nominatim (the OpenStreetMap
 * Foundation's free geocoding service).
 *
 * <h3>About Nominatim's usage policy</h3>
 *
 * <p>Nominatim is free, but their fair-use rules are strict:</p>
 * <ul>
 *   <li><b>Maximum 1 request per second per IP.</b> No parallel requests.</li>
 *   <li><b>Identifying User-Agent header is required.</b> Generic
 *       libraries (Java, Apache HttpClient, etc.) are blocked. Set
 *       something specific — ideally including your app name and an
 *       email so the OSM team can contact you about issues.</li>
 *   <li><b>No bulk geocoding.</b> If you need lots of geocoding,
 *       run your own Nominatim instance or use a paid provider.</li>
 * </ul>
 *
 * <p>For this learning project we use Nominatim sparingly — only on
 * the explicit "Geocode this office" button click, never automatically
 * — so we stay well within fair-use limits. See:
 * https://operations.osmfoundation.org/policies/nominatim/</p>
 *
 * <h3>Why RestClient and not RestTemplate or WebClient?</h3>
 *
 * <p>Three Spring HTTP clients exist, and people frequently ask which
 * to pick:</p>
 * <ul>
 *   <li>{@code RestTemplate} — original, blocking, in maintenance mode.
 *       Fine for legacy code; not recommended for new work.</li>
 *   <li>{@code WebClient} — reactive (returns Mono/Flux), powerful but
 *       adds the Reactor learning curve. Use it if you're already
 *       reactive end-to-end.</li>
 *   <li>{@code RestClient} (Spring 6.1+) — modern blocking API with a
 *       fluent builder. Same simplicity as RestTemplate, same shape
 *       as WebClient. Recommended default for new code unless you
 *       need reactive.</li>
 * </ul>
 */
@Service
@Slf4j
public class GeocodingService {

    /**
     * Spring's {@code RestClient}, configured at construction time with
     * the Nominatim base URL and our identifying User-Agent. Reused for
     * every request from this service — building a new one per call
     * would also work but wastes the connection pool.
     */
    private final RestClient restClient;

    public GeocodingService(
            @Value("${app.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${app.geocoding.user-agent:ClassicModels-Learning-App/1.0}") String userAgent,
            @Value("${app.geocoding.trust-all-certs:false}") boolean trustAllCerts) {

        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent);

        if (trustAllCerts) {
            // DEVELOPMENT-ONLY ESCAPE HATCH for corporate networks that
            // run TLS-intercepting proxies. The proxy re-signs every
            // HTTPS response with a private root CA the JVM's default
            // trust store doesn't include, so calls fail with
            // "PKIX path building failed". The proper fix is to import
            // the corporate root CA into the JVM cacerts; if that's
            // not possible, this flag tells our RestClient to skip
            // verification altogether. Off by default; never enable
            // in production. See application.yml for the property.
            log.warn("TLS certificate verification is DISABLED for geocoding. " +
                    "This is a development workaround only — never enable in production.");
            builder.requestFactory(buildTrustAllRequestFactory());
        }

        this.restClient = builder.build();
    }

    /**
     * Build an {@link JdkClientHttpRequestFactory} backed by an
     * {@link HttpClient} whose SSL context trusts every certificate.
     *
     * <p>The chain: a "trust-all" {@link X509TrustManager} → an
     * {@link SSLContext} initialised with that manager → an HttpClient
     * configured with that context → Spring's request factory wrapping
     * the HttpClient. {@code RestClient} uses the factory for outbound
     * calls and never validates server certs.</p>
     *
     * <p>Yes, this is exactly what you should never do in production.
     * The point of TLS verification is to ensure you're talking to who
     * you think you're talking to. Skipping it makes you trivially
     * vulnerable to a real (not corporate) MITM attack.</p>
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
            throw new IllegalStateException("Failed to build trust-all SSL context", e);
        }
    }

    /**
     * Look up coordinates for a free-form address query.
     *
     * @param query  human-readable address, e.g. "Marina Bay, Singapore"
     *               or "1600 Amphitheatre Parkway, Mountain View"
     * @return the first matching result's lat/lng, or empty if Nominatim
     *         couldn't find anything or the call failed
     */
    public Optional<Coordinates> geocode(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        log.info("Geocoding address: {}", query);

        try {
            // GET /search?q=<query>&format=json&limit=1
            //
            // We ask for at most 1 result. Nominatim ranks results so
            // the top hit is the most likely match. If you want
            // disambiguation UX, ask for limit=5 and let the user pick.
            NominatimResult[] results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(NominatimResult[].class);

            if (results == null || results.length == 0) {
                log.warn("Nominatim returned no results for: {}", query);
                return Optional.empty();
            }

            NominatimResult top = results[0];
            // Nominatim's response uses STRING lat/lon (an unfortunate
            // legacy choice). Parse to double here so callers get
            // numeric coordinates.
            try {
                double lat = Double.parseDouble(top.lat());
                double lng = Double.parseDouble(top.lon());
                log.info("Geocoded '{}' → ({}, {}) — '{}'",
                        query, lat, lng, top.displayName());
                return Optional.of(new Coordinates(lat, lng, top.displayName()));
            } catch (NumberFormatException e) {
                log.warn("Nominatim returned unparseable coordinates: lat={} lon={}",
                        top.lat(), top.lon());
                return Optional.empty();
            }

        } catch (RestClientException e) {
            // Network error, 5xx, parse failure — anything that prevents
            // us from getting an answer. Log and return empty rather
            // than propagating: callers can surface a "could not
            // geocode" message and life goes on.
            log.warn("Geocoding call failed for '{}': {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Successful geocoding result. Display name comes from Nominatim
     * and looks like "1, Amphitheatre Parkway, Mountain View, …, USA".
     * Useful for confirming "yes, this is the address I expected" in
     * the UI.
     */
    public record Coordinates(double lat, double lng, String displayName) {}

    /**
     * Subset of Nominatim's response we actually care about. The real
     * response has ~15 fields (osm_id, place_id, importance, …); we
     * ignore them. {@code @JsonIgnoreProperties(ignoreUnknown = true)}
     * tells Jackson not to fail when it sees fields we haven't declared.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(
            String lat,
            String lon,
            // Nominatim uses snake_case in JSON. We map it to
            // displayName via a constructor-arg name match — Jackson
            // does not need an explicit @JsonProperty here because
            // Spring's default ObjectMapper has a SNAKE_CASE PropertyNamingStrategy
            // applied automatically… wait, it doesn't.
            // We DO need @JsonProperty on this one.
            @com.fasterxml.jackson.annotation.JsonProperty("display_name")
            String displayName
    ) {}
}
