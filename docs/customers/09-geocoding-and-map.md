# Feature C9 — Address geocoding + per-customer map

## What we built

A "Geocode address" button on the customer detail page. Click it, and
the backend turns the customer's address into latitude/longitude using
**Nominatim** — OpenStreetMap's free geocoding service — and saves the
result to the database. The map appears (or repositions) without a
page reload.

Customers ship with `lat` and `lng` columns set to `NULL` (V8
migration). Each customer is geocoded lazily — on the first button
click — and the result persists for all subsequent visits. The button
becomes "Re-geocode address" once coordinates are present, for cases
where the address has been corrected and you want to refresh.

This is the customer-side application of F4, which built the office
geocoder. The geocoding service itself is reused unchanged; what's
new is the customer-specific lookup chain (full address → city +
country → country) and a slightly different UX shape (lazy on every
customer, vs. seed-pre-populated for the small office set).

Files touched:

- `classicmodels-backend/src/main/resources/db/migration/V8__customer_coordinates.sql` (new)
- `classicmodels-backend/src/main/java/.../model/Customer.java`
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerResponseDTO.java`
- `classicmodels-backend/src/main/java/.../repository/CustomerRepository.java`
- `classicmodels-backend/src/main/java/.../service/CustomerService.java`
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java`
- `classicmodels-ui/src/app/customers/customer.model.ts`
- `classicmodels-ui/src/app/customers/customer.service.ts`
- `classicmodels-ui/src/app/customers/customer-detail.component.ts`
- `classicmodels-ui/src/app/customers/customer-detail.component.html`

No new geocoder service, no new map component — both are reused
straight out of `geocoding/GeocodingService.java` and
`shared/mini-map.component.ts`.

## Why this is worth learning

Three things converge. **Reusing an existing infrastructure bean
across entities** — the `GeocodingService` we wrote for offices in
F4 needed zero changes; `CustomerService` just injects it and calls
`geocode(query)`. **The per-entity fallback chain** — the *queries*
you'd build for a customer differ slightly from those for an office
(no "office code" disambiguator, slightly different field set), but
the *shape* of the chain is identical. **The "lazy backfill" pattern
for expensive lookups** — when each call costs you one second of rate
limit, populating on demand from a UI button is the friendliest way
to amortise the cost across days of normal usage.

The new lesson, specific to C9, is the **C9-vs-C10 design tension**.
On-demand geocoding works perfectly for one customer at a time but
breaks for "show every customer on a map" (C10), which would need
122 sequential 1-second calls. C10 will have to either pre-fill via
a one-shot batch script or render only the customers that have
already been geocoded. The choice we make in C9 (lazy, on-demand)
forces that choice in C10.

## Background

### Why lazy on-demand instead of seed-populated

V3 pre-seeded the 7 office coordinates with hand-picked city
centroids. That's fine for 7 rows you control; for 122 customers
across 30+ countries it isn't worth the manual lookup. The customer
migration ships the columns NULL and lets the application populate
them on demand — the trade-off being that on day one, no customer
has a map.

Why not auto-geocode on customer save? Because:

- **Rate limits.** Nominatim allows 1 req/sec/IP. Bulk-creating 100
  customers via the seed import would hammer the API and get the IP
  banned.
- **Latency.** Adding ~1s of network round-trip to every customer
  save makes the form feel sluggish.
- **Non-blocking failure mode.** A geocoder hiccup shouldn't refuse
  the customer create. Lazy means the user creates first; the geocode
  is a separate (retriable) action.

If you really need bulk geocoding, run your own Nominatim instance
or use a paid service (Mapbox, Geocodio). For incremental learning-
project usage, on-demand is the right shape.

References:

- [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/)
- [Nominatim self-hosting](https://nominatim.org/release-docs/latest/admin/Installation/) — for when you outgrow the free tier

### The fallback chain — same shape, different inputs

Office and customer both have the "address might not parse" problem,
and both use the same three-rung approach:

```
1. Full address  →  good precision when it works
2. City + country →  always succeeds for any reasonable city
3. Country alone →  last-ditch country centroid
```

The customer-specific code lives in `CustomerService.buildFallbackQueries`
and `buildAddressQuery`. They're nearly identical to their office
equivalents, modulo the fields they read (no `officeCode` for
customer, but otherwise same shape).

Could we have refactored these into a shared `AddressQueryBuilder`?
Yes. Two reasons not to (yet):

- The field set IS slightly different per entity (offices don't have
  postalCode the same way; customers don't have officeCode).
- The right abstraction for "build a fallback chain from any address-
  shaped object" is a small interface — premature without a third
  caller forcing the shape.

When C10 uses the same chain, or a third entity (Order with its
shipping address?) wants in, that's the right time to extract.

### `RestClient` and `GeocodingService` — no changes

The injection is two lines — add the field, add the constructor param:

```java
private final GeocodingService geocoding;

public CustomerService(..., GeocodingService geocoding) {
    ...
    this.geocoding = geocoding;
}
```

Spring already manages `GeocodingService` as a singleton bean (it's
`@Service`-annotated); customer just becomes a second consumer.
No connection-pool concerns, no per-tenant configuration — Nominatim
is a single global service, and the client is shared.

This is the "service is a Spring bean" payoff — building services as
beans rather than as utility classes makes them trivial to compose
across entities. F4 paid the bean-design cost; C9 collects the
dividend.

References:

- [Spring docs — Bean scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring docs — `@Service`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/stereotype-annotations.html)

### `BigDecimal(10,7)` for coordinates

The schema uses `DECIMAL(10, 7)`:

- **10 total digits, 7 after the decimal point.** That gives a max
  value of `999.9999999`, comfortably above ±180° (longitude limit)
  and ±90° (latitude limit).
- **7 fractional digits** is precise to ~1cm at the equator. Wildly
  more than needed for "where in the world is this customer," but
  it's the conventional choice and the storage cost is trivial.

The Java side reads the columns as `BigDecimal` to preserve the
precision. The frontend converts to `number` via `Number(c.lat)` for
Leaflet, accepting the small precision loss because Leaflet uses
plain JS numbers anyway.

Reference: [MySQL — DECIMAL data type](https://dev.mysql.com/doc/refman/8.0/en/fixed-point-types.html)

### Wire format: BigDecimal as JSON string

When Jackson serialises `BigDecimal`, it writes the value as a JSON
**string** by default, not a number. So `c.lat` on the frontend is
typed as `string | null`, and the detail component parses with
`Number(c.lat)` once into a `coords` computed signal:

```ts
coords = computed<{ lat: number; lng: number } | null>(() => {
  const c = this.customer();
  if (!c?.lat || !c?.lng) return null;
  const lat = Number(c.lat);
  const lng = Number(c.lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return { lat, lng };
});
```

The string-vs-number choice is deliberate on Jackson's side —
serialising large `BigDecimal` values as JSON numbers can lose
precision in some clients. We don't strictly need the precision for
coordinates, but matching the upstream convention is cheaper than
adding a `@JsonFormat(shape = NUMBER)` annotation everywhere.

Reference: [Jackson — BigDecimal serialization](https://www.baeldung.com/jackson-bigdecimal)

### Three render states for the map block

The template handles three cases without a heavyweight conditional
ladder:

| State | What's rendered |
|---|---|
| Coordinates present | `<app-mini-map>` with the popup |
| No coordinates yet | "No coordinates stored. Click Geocode address." |
| Geocode in flight | The button shows a spinner; the existing map (or empty state) stays put |

The button label adapts: "Geocode address" when no coords, "Re-geocode
address" when coords already exist. Same button, different verb —
keeps the affordance discoverable for both first-time geocoding and
correcting a stale lookup.

### The popup HTML escape

The mini-map's `popupHtml` is rendered as raw HTML by Leaflet. Any
unescaped `< > & " '` in the customer name or city would be
interpreted as markup. The detail component escapes once, in a
local helper:

```ts
const esc = (s: string | undefined | null) =>
  (s ?? '').replace(/[&<>"']/g, ch => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[ch]!));
return `<strong>${esc(c.customerName)}</strong><br>${esc(c.city)}, ${esc(c.country)}`;
```

This is the same escape function the employee detail page uses for
its marker popup. Inlined here rather than imported because (a) it's
five lines, (b) shared escape utilities tend to grow into bigger
sanitisers that drag in other concerns. When we have three callers,
extract.

Reference: [OWASP — XSS prevention cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)

### Why the geocode broadcasts a `CustomerEvent.UPDATED`

`CustomerService.geocode` ends with:

```java
broadcast(CustomerEvent.Type.UPDATED, id);
```

A geocode is structurally an UPDATE — the row changed. Any other tab
showing this customer (or its row in the list) should see the new
coordinates after re-fetch. The list page doesn't render the map, so
in practice the event is mostly a no-op there, but consistency
matters: every server-side mutation of customer state fires an
event. Tomorrow when something *does* depend on it (e.g. C10's all-
customers map), the event is already there.

## The code, walked through

### Service-side geocode flow

```java
@CacheEvict(cacheNames = {"customers", "customersAll", "customersPaged"}, allEntries = true)
public CustomerResponseDTO geocode(int id) {
    Customer customer = repo.findById(id)
            .orElseThrow(() -> new RuntimeException("Customer " + id + " not found"));

    var result = geocodeWithFallbacks(customer);
    repo.updateCoordinates(id, result.lat(), result.lng());

    Customer updated = repo.findById(id).orElseThrow();
    var response = mapper.toResponseDTO(updated);

    broadcast(CustomerEvent.Type.UPDATED, id);
    return response;
}
```

Five steps, in order:

1. Look up the customer (404-shaped if missing).
2. Run the fallback chain through Nominatim.
3. Persist the lat/lng via the targeted UPDATE (no version check —
   geocode isn't a content edit).
4. Re-read so the response carries the persisted state.
5. Broadcast UPDATED so any open tab refreshes.

`@CacheEvict(allEntries = true)` invalidates all three customer
caches because the geocode visibly changes the row, and the cached
versions would carry stale (null) coordinates.

### Targeted repository update

```java
public boolean updateCoordinates(int id, double lat, double lng) {
    String sql = "UPDATE customers SET lat = ?, lng = ? WHERE customerNumber = ?";
    ...
}
```

Two SET fields, one WHERE predicate. Doesn't touch `version`,
`updatedAt`, or `updatedBy` — none of those should change for a
geocode. The full `update(Customer c)` method is wrong for this case
because it'd re-write every column and bump the version.

### Frontend handler with three reactive signals

```ts
runGeocode(): void {
  const c = this.customer();
  if (!c || this.geocoding()) return;

  this.geocoding.set(true);
  this.geocodeMessage.set(undefined);
  this.geocodeError.set(false);

  this.customers.geocode(c.customerNumber).subscribe({
    next: updated => {
      this.customer.set(updated);
      this.geocoding.set(false);
      this.geocodeMessage.set('Geocoded successfully. Map updated.');
    },
    error: err => {
      this.geocoding.set(false);
      this.geocodeError.set(true);
      this.geocodeMessage.set(`Geocoding failed: ${err?.error?.detail ?? err?.message ?? 'Unknown error'}`);
    },
  });
}
```

Three signals manage the UX:

- `geocoding` — "request in flight." The button binds `[disabled]` to
  it; the template shows a spinner instead of the icon.
- `geocodeMessage` — transient status string, set after the call
  finishes (success or fail). Cleared at the start of the next call.
- `geocodeError` — flag toggling the `.error` class on the message
  div. Without this we'd have to detect the leading "Geocoding failed:"
  in the message string, which is fragile.

Setting `customer.set(updated)` after success means the `coords`
computed signal recomputes; the template re-evaluates `@if (coords())`
and renders (or repositions) the `<app-mini-map>` automatically.

## How to test

### Happy path — first geocode

1. Restart the backend (Flyway runs V8).
2. Sign in. Open any customer's detail page.
3. The Address section shows: "No coordinates stored. Click Geocode
   address to look up this customer's location."
4. Click **Geocode address**. Button shows spinner + "Geocoding…".
5. After ~1s: map appears with a marker pinned at (or near) the
   customer's address. Status reads "Geocoded successfully. Map updated."
6. The button label changes to "Re-geocode address."
7. Verify in the DB:
   ```sql
   SELECT customerNumber, lat, lng FROM customers WHERE customerNumber = 103;
   -- → both populated
   ```

### Happy path — re-geocode

1. Open the same customer's detail page (the coordinates are now
   present).
2. Map renders immediately. Button reads "Re-geocode address."
3. Click. New geocode runs; coordinates may shift slightly (Nominatim
   sometimes returns a marginally different result on repeat queries).

### Empty / weird address

1. Edit a customer to have a deliberately nonsense `addressLine1`
   (e.g., "ABCDEFG NOWHERE").
2. Click Geocode. The full address probably fails; the city+country
   fallback catches it; you land on the city centre.
3. The popup still labels the marker correctly with the customer's
   name.

### Geocoder unreachable

1. Disconnect from the internet (or set
   `app.geocoding.base-url=http://localhost:9999/nope` in
   application.yml temporarily).
2. Click Geocode. Status shows "Geocoding failed: …" in red.
3. The customer's existing coordinates (if any) are unchanged.

### Direct API check

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
     'http://localhost:9090/api/v1/customers/103/geocode' | jq '.lat, .lng'
```

## What you just learned

- **Reusing an infrastructure bean across entities** — `GeocodingService`
  built for offices needed zero changes; customer just becomes another
  consumer.
- **The lazy-backfill pattern** — skip the upfront cost of bulk
  pre-population, let user actions populate on demand, persist the
  result so the cost is paid once per row.
- **Per-entity fallback chains** — same shape as the office version
  (full → city+country → country), tailored to the customer's field
  set.
- **`BigDecimal` <-> JSON string round-trip** — Jackson's default for
  `BigDecimal` preserves precision at the cost of a `Number(...)`
  parse on the client.
- **Three render states for the map block** — coords present, coords
  absent, request in flight — with the button label adapting between
  "Geocode" and "Re-geocode."
- **Inline HTML escape** for marker popups, repeated rather than
  shared until the third caller appears.

## Study materials

### External APIs in Spring

- [Spring docs — RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
- [Spring blog — RestClient introduction](https://spring.io/blog/2023/07/13/new-in-spring-6-1-restclient)
- [Baeldung — RestClient guide](https://www.baeldung.com/spring-boot-restclient)

### Geocoding & Nominatim

- [Nominatim — API documentation](https://nominatim.org/release-docs/latest/api/Overview/)
- [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/)
- [Wikipedia — Geocoding](https://en.wikipedia.org/wiki/Address_geocoding)

### Leaflet

- [Leaflet — Quick start guide](https://leafletjs.com/examples/quick-start/)
- [Leaflet — Marker API](https://leafletjs.com/reference.html#marker)
- [Leaflet — Popup API](https://leafletjs.com/reference.html#popup)

### XSS-safe HTML construction

- [OWASP — XSS prevention cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [MDN — `String.prototype.replace`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/replace)

### Coordinate precision

- [Wikipedia — Decimal degrees precision](https://en.wikipedia.org/wiki/Decimal_degrees#Precision)
- [MySQL — DECIMAL data type](https://dev.mysql.com/doc/refman/8.0/en/fixed-point-types.html)
