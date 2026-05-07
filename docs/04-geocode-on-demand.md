# Feature 04 — Geocode an office on demand

## What we built

A "Geocode address" button on the office detail page. Click it, and
the backend turns the office's address into latitude/longitude using
**Nominatim** — OpenStreetMap's free geocoding service — and saves the
result to the database. The map appears (or repositions) without a
page reload.

This is the first feature where our backend talks to an *external* HTTP
service. That's a small step for the codebase but a substantial step
for what you're learning: sending HTTP requests *from* a Spring app,
parsing JSON returned from someone else's API, and being a polite
consumer of free services.

Files touched:

- `classicmodels-backend/src/main/java/.../geocoding/GeocodingService.java` (new)
- `classicmodels-backend/src/main/java/.../service/OfficeService.java`
- `classicmodels-backend/src/main/java/.../repository/OfficeRepository.java`
- `classicmodels-backend/src/main/java/.../controller/OfficeController.java`
- `classicmodels-ui/src/app/offices/office.service.ts`
- `classicmodels-ui/src/app/offices/office-detail.component.ts`
- `classicmodels-ui/src/app/offices/office-detail.component.html`

## Why this is worth learning

Three concepts for the price of one feature. **HTTP clients in Spring**
— how to call external services without writing raw `HttpURLConnection`
plumbing. **External-API etiquette** — User-Agent headers, rate limits,
graceful failure when the third party is down. **The "external service
+ persistence" pattern** — fetch from outside, transform, save locally
so future requests don't need the round trip.

## Background

### Geocoding

The process of turning a human-readable place name or address into
geographic coordinates. The reverse operation (lat/lng → address) is
called **reverse geocoding**.

### Nominatim — OpenStreetMap's free geocoder

A web service operated by the OSM Foundation that wraps OSM data into
a search API. Free to use within their fair-use policy:

- **1 request per second per IP, max.** No parallel requests.
- **Identifying User-Agent header required.** Generic library
  identifiers (Java/Apache HttpClient/etc.) are blocked.
- **No bulk geocoding.** If you need lots of geocoding, run your own
  Nominatim instance or use a paid provider (Mapbox, Geocodio, …).

We meet these rules effortlessly because geocoding only happens when a
user clicks the button — manual, one office at a time. If you tried
to geocode 500 offices in a loop you'd get rate-limited (and
rightly so).

References:

- [Nominatim — Documentation](https://nominatim.org/release-docs/latest/api/Search/)
- [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/)

### Spring's `RestClient`

The current best HTTP client for Spring backends. Three options exist
in modern Spring; here's how to pick:

| Client          | Spring version | Style       | Use when |
|-----------------|---------------|-------------|----------|
| `RestTemplate`  | 1.0+          | Blocking    | Maintaining old code |
| `WebClient`     | 5.0+          | Reactive    | Already reactive end-to-end |
| **`RestClient`**| **6.1+**      | **Blocking**| **Default for new code** |

`RestClient` has a fluent builder API (like WebClient) but is
synchronous (like RestTemplate). It's effectively "what RestTemplate
should have been from the start," and Spring is moving towards it as
the recommended default.

Quick example:

```java
RestClient client = RestClient.builder()
    .baseUrl("https://api.example.com")
    .defaultHeader("User-Agent", "MyApp/1.0")
    .build();

User user = client.get()
    .uri("/users/{id}", 42)
    .retrieve()
    .body(User.class);
```

References:

- [Spring docs — RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
- [Spring blog — RestClient introduction](https://spring.io/blog/2023/07/13/new-in-spring-6-1-restclient)
- [Baeldung — RestClient](https://www.baeldung.com/spring-boot-restclient)

### Why deserialise into a record?

When Nominatim returns JSON, Jackson (the JSON library Spring uses)
needs a Java type to deserialise into. Records are the simplest fit:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
private record NominatimResult(
    String lat,
    String lon,
    @JsonProperty("display_name") String displayName
) {}
```

Three things to know:

1. `@JsonIgnoreProperties(ignoreUnknown = true)` tells Jackson to
   skip JSON fields we don't have record components for. Nominatim's
   real response has ~15 fields; we only care about three.
2. `@JsonProperty("display_name")` maps the snake_case JSON field to
   our camelCase record component.
3. `lat` and `lon` are STRINGS in Nominatim's JSON (an unfortunate
   legacy choice). We parse them to `double` ourselves.

References:

- [Jackson — Annotations overview](https://github.com/FasterXML/jackson-annotations/wiki)
- [Baeldung — `@JsonIgnoreProperties`](https://www.baeldung.com/jackson-annotations#bd-jsonignoreproperties)

### Fallback queries when geocoding fails

Nominatim is forgiving but not magic. Real addresses fail to parse
all the time, especially across cultures: the Tokyo office's address
in our seed data ("4-1 Kioicho, Chiyoda-Ku 102-8578, Japan") doesn't
match Nominatim's training data because Japanese addresses are
structured back-to-front compared to Western ones, and Nominatim
expects the Western pattern.

The fix is a *fallback chain*: try the most specific query first;
if it fails, try less specific. Our service tries three rungs in
order:

1. **Full address.** Best precision when it works — pins the marker
   on the actual building.
2. **City + country.** Almost always succeeds; lands on the city
   centroid, which is "good enough" for "where in the world is this
   office."
3. **Country alone.** Last-ditch — returns a point in the middle of
   the country. Coarse but at least non-zero.

The first hit wins. We only throw when even "Country" fails to match,
which usually means Nominatim itself is unreachable.

```java
private GeocodingService.Coordinates geocodeWithFallbacks(Office office) {
    List<String> queries = buildFallbackQueries(office);  // most-specific first
    for (String query : queries) {
        var attempt = geocoding.geocode(query);
        if (attempt.isPresent()) return attempt.get();
    }
    throw new RuntimeException("Geocoding returned no result for any of: " + queries);
}
```

This pattern is general — it applies to any external API where you'd
rather get a "good enough" answer than throw on the first miss.

### Corporate networks and TLS interception

If your geocoding calls fail with `PKIX path building failed:
sun.security.provider.certpath.SunCertPathBuilderException: unable to
find valid certification path to requested target`, you're almost
certainly behind a corporate TLS-intercepting proxy.

These proxies (Zscaler, Symantec, Bluecoat, etc.) sit between your
laptop and the internet, perform a man-in-the-middle on every HTTPS
connection so they can inspect traffic, and re-sign each response
with a private root CA. Your browser trusts that CA because IT
installed it in the OS trust store on day one. Your JVM, however,
maintains its own separate trust store (`$JAVA_HOME/lib/security/cacerts`)
that doesn't include the corporate root, so it sees a chain it can't
verify and refuses the connection.

**Two fixes:**

**(a) The proper one** — import the corporate root CA into the JVM
trust store:

```powershell
# Get the .crt file from your IT or by exporting from the OS trust
# store. Then:
keytool -import -trustcacerts -alias corp-root `
        -keystore "$env:JAVA_HOME/lib/security/cacerts" -storepass changeit `
        -file path\to\corp-root.crt
```

Restart, the error goes away. This fixes the JVM globally, so every
Java app on the laptop can now reach corporate-intercepted HTTPS.

**(b) The development workaround** — disable certificate verification
for our geocoder's HTTP client. This is exactly what
{@code app.geocoding.trust-all-certs=true} does. We build a custom
`SSLContext` whose `TrustManager` accepts every certificate, then
plug it into the `HttpClient` that backs our `RestClient`:

```java
TrustManager[] trustAll = new TrustManager[]{
    new X509TrustManager() {
        public void checkServerTrusted(X509Certificate[] chain, String authType) { /* trust */ }
        public void checkClientTrusted(X509Certificate[] chain, String authType) { /* trust */ }
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
};
SSLContext ctx = SSLContext.getInstance("TLS");
ctx.init(null, trustAll, new SecureRandom());

HttpClient httpClient = HttpClient.newBuilder()
        .sslContext(ctx)
        .build();
RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory(httpClient))
        ...
```

Yes, this is exactly what production code should never do. The point
of TLS verification is to ensure you're talking to who you think
you're talking to. Skipping it makes you vulnerable to a real (not
corporate) MITM attack — anyone on your network could impersonate
Nominatim and feed you bogus coordinates. For a personal dev laptop
behind your employer's proxy, the risk is theoretical and the
convenience is real; for anything that handles user data or runs
unattended, fix it properly with option (a).

References:

- [Oracle — keytool reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html)
- [Oracle — Java cacerts file](https://docs.oracle.com/en/java/javase/21/security/java-pki-programmers-guide.html)
- [OWASP — Certificate and Public Key Pinning](https://cheatsheetseries.owasp.org/cheatsheets/Pinning_Cheat_Sheet.html)

### The "external service + persistence" pattern

Whenever you call a third-party API, decide once:

1. **Pull-through cache.** Cache the response in your own DB. Future
   requests use the local copy. Saves money/quota and stays available
   even when the third party is down. We use this here — geocode once,
   store the lat/lng, never call Nominatim again for that address.

2. **Pass-through.** Just relay every request to the upstream API.
   Simpler but more expensive and fragile.

For data that doesn't change often (geographic coordinates,
country codes, currency lists), pull-through is almost always right.
For data that changes constantly (stock prices, weather), pass-through
is the only sensible option.

## The code, walked through

### GeocodingService — the only new file

```java
@Service
public class GeocodingService {

    private final RestClient restClient;

    public GeocodingService(
            @Value("${app.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${app.geocoding.user-agent:ClassicModels-Learning-App/1.0}") String userAgent) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    public Optional<Coordinates> geocode(String query) {
        ...
        NominatimResult[] results = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("limit", 1)
                        .build())
                .retrieve()
                .body(NominatimResult[].class);
        ...
    }
}
```

Three things worth noticing:

- The base URL and User-Agent are externalised as `@Value` properties.
  You can override them in `application.yml` or via env vars without
  touching code — useful for pointing at a self-hosted Nominatim, for
  testing, or for changing the User-Agent without rebuilding.
- `Optional<Coordinates>` as the return type makes the "no result"
  case explicit and forces the caller to handle it.
- All errors (network, 5xx, JSON parse) become an empty `Optional`.
  The caller decides what to surface.

### OfficeService — orchestrate

```java
public OfficeResponseDTO geocode(String code) {
    Office office = repo.findById(code)
            .orElseThrow(() -> new RuntimeException("Office " + code + " not found"));

    String query = buildAddressQuery(office);

    var result = geocoding.geocode(query)
            .orElseThrow(() -> new RuntimeException(
                    "Geocoding returned no result for: " + query));

    repo.updateCoordinates(code, result.lat(), result.lng());

    Office updated = repo.findById(code).orElseThrow();
    return mapper.toResponseDTO(updated);
}
```

The service composes three small steps: read, geocode, write. Each
step has a clear failure mode (404, geocoder fail, DB fail). The
re-read at the end ensures the response reflects the persisted state,
not the in-memory mutation we did locally.

### Frontend — small button + state-tracking signals

```ts
runGeocode() {
  const o = this.office();
  if (!o || this.geocoding()) return;

  this.geocoding.set(true);
  this.geocodeMessage.set(undefined);

  this.officeService.geocode(o.officeCode).subscribe({
    next: updated => {
      this.office.set(updated);
      this.geocoding.set(false);
      this.geocodeMessage.set('Geocoded successfully. Map updated.');
    },
    error: err => {
      this.geocoding.set(false);
      this.geocodeMessage.set(`Geocoding failed: ${err?.error?.message ?? 'Unknown error'}`);
    },
  });
}
```

`geocoding` is a "request in flight" boolean signal — the button binds
its `disabled` attribute to it. `geocodeMessage` is a transient
status string that appears above the actions row after the call
finishes. Both are signals so the template updates reactively without
manual change detection.

## How to test

### Happy path (existing seed office)

1. Restart the backend.
2. Sign in.
3. **Offices → click any city** (e.g., Sydney).
4. Click **Geocode address**.
5. After 1–2 seconds, the map updates (you'll see "Geocoded
   successfully. Map updated." above the action row). The new
   coordinates are now in the database, replacing the seed data.

### Empty case (un-geocoded office)

1. **Offices → New Office**.
2. Fill in `addressLine1=350 5th Ave, city=New York, country=United States`,
   give it some unique code, save.
3. Open the new office's detail page. No map (lat/lng is null).
4. Click **Geocode address**.
5. Map appears with a marker pinned at the Empire State Building.

### Error path

Disconnect from the internet, click the button. The status line shows
"Geocoding failed: …" with the underlying error message. The office's
existing coordinates (if any) are unchanged.

### Curl

```bash
curl -X POST http://localhost:9090/api/v1/offices/6/geocode \
  -H 'Authorization: Bearer <your-token>'
```

Returns the updated office JSON.

## What you just learned

- **`RestClient`** as the modern default for synchronous HTTP from
  Spring backends, and how it differs from `RestTemplate` and
  `WebClient`.
- **JSON deserialisation into records** with `@JsonIgnoreProperties`
  and `@JsonProperty`.
- **External-API etiquette** — User-Agent headers, rate limits, the
  importance of being a polite consumer of free services.
- **The "fetch externally, store locally" pattern** that turns
  expensive or fragile third-party data into cheap repeatable reads.
- **Externalising configuration** with `@Value("${prop:default}")`
  for properties you want operators to tune without code changes.

## Study materials

### Spring HTTP clients

- [Spring docs — Rest Clients overview](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Spring docs — `RestClient`](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
- [Spring blog — RestClient introduction](https://spring.io/blog/2023/07/13/new-in-spring-6-1-restclient) — narrative explanation
- [Baeldung — RestClient guide](https://www.baeldung.com/spring-boot-restclient)
- [Baeldung — RestClient vs WebClient vs RestTemplate](https://www.baeldung.com/spring-boot-restclient-vs-webclient-vs-resttemplate)

### Jackson (JSON in Java)

- [Jackson documentation](https://github.com/FasterXML/jackson-docs)
- [Baeldung — Jackson annotations](https://www.baeldung.com/jackson-annotations)
- [Baeldung — Records and Jackson](https://www.baeldung.com/jackson-deserialize-records)

### OpenStreetMap & Nominatim

- [Nominatim — API documentation](https://nominatim.org/release-docs/latest/api/Overview/)
- [Nominatim — Usage policy](https://operations.osmfoundation.org/policies/nominatim/)
- [Wikipedia — Geocoding](https://en.wikipedia.org/wiki/Address_geocoding)
- [Self-hosting Nominatim](https://nominatim.org/release-docs/latest/admin/Installation/) — for when you outgrow the free service

### Externalised configuration

- [Spring docs — `@Value`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html)
- [Spring Boot — Externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Baeldung — `@Value`](https://www.baeldung.com/spring-value-annotation)

### Optional in Java

- [Oracle — Optional API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html)
- [Stuart Marks — "Optional: The Mother of All Bikesheds"](https://www.youtube.com/watch?v=Ej0sss6cq14) — the JDK
  Optional designer's own talk on when to use it (and when not to)
