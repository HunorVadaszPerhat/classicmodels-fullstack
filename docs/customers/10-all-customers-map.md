# Feature C10 — All-customers map

## What we built

A new page at `/customers/map` that plots every active, geocoded
customer as a marker on a single Leaflet map. Markers are
colour-coded by sales-rep assignment status — blue for "has a rep,"
orange for "no rep, territory gap" — so a sales manager can spot
coverage holes at a glance. Clicking a marker pops up the customer's
name, city/country, status, and a link to the full detail page.

A status bar at the top reports "X of Y active customers geocoded"
with a hint about the per-customer Geocode button on the detail
page, so users understand why some customers don't appear and how to
fix that. A small legend on the side spells out what the colours
mean and counts each segment.

This is the customer-side application of F3's office map pattern —
the existing `MarkersMapComponent` (multi-marker, bounds-fitting) is
reused with a tiny extension to support per-marker icon classes for
the status colours. The new pattern, specific to C10, is **many
points colour-coded at scale**, plus the C9-vs-C10 design tension
finally being paid down.

Files touched:

- `classicmodels-backend/src/main/java/.../dto/customer/CustomerMapPointDTO.java` (new)
- `classicmodels-backend/src/main/java/.../repository/CustomerRepository.java` — `findAllMapPoints`, `countAllActive`
- `classicmodels-backend/src/main/java/.../service/CustomerService.java` — `findAllMapPoints`, `countActiveCustomers`, expanded cache evictions
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java` — `/map-points`, `/active-count`
- `classicmodels-ui/src/app/customers/customer.service.ts` — `mapPoints()`, `activeCount()`, `CustomerMapPoint`
- `classicmodels-ui/src/app/customers/customer-map.component.ts` (new)
- `classicmodels-ui/src/app/customers/customer.routes.ts` — route registration
- `classicmodels-ui/src/app/customers/customer-list.component.html` — Map toolbar button
- `classicmodels-ui/src/app/shared/markers-map.component.ts` — per-marker `iconClass` support
- `classicmodels-ui/src/styles.scss` — global marker colour CSS

## Why this is worth learning

Three concepts converge. **Many-points-on-one-map** as a UI pattern —
the questions you ask of a multi-point map ("where are these things,
geographically? where are the clusters? where are the gaps?") differ
from those you ask of a single-point map ("show me where this thing
is"). **The cheap-projection DTO** for list-of-points endpoints — the
full row carries 19 fields, the map needs 7, and at scale the
difference matters. **Status-colour markers via `L.divIcon` + global
CSS** — keeping marker styling in CSS rather than runtime-built
icons makes it easy to retheme and impossible to forget escaping.

The new lesson, specific to Customer, is the **C9-vs-C10 design
tension** finally biting. C9 picked lazy on-demand geocoding; C10
inherits an empty map on day one and surfaces that fact transparently
("X of Y geocoded — use the Geocode button to fill in the rest")
rather than hiding it.

## Background

### Why a separate page from the list

The list (C2) is built for **browsing rows** — pagination, sort,
search, row-level actions. The map answers a fundamentally different
question: **"where are these customers, geographically?"**

Two views over the same data, optimised for different tasks. Same
pattern as offices got in F3 (a global office map separate from the
office list). Forcing both questions into one screen would either
clutter the list or cripple the map.

The two views still share the underlying data — both subscribe to
the same `CustomerEventsService`, so any create/update/delete/
geocode triggers both pages to refresh. Cross-tab consistency for
free.

### The cheap-projection DTO

`CustomerResponseDTO` carries 19 fields including audit timestamps,
credit limits, the optimistic-lock version. The map needs 7:

```java
public record CustomerMapPointDTO(
        int customerNumber,
        String customerName,
        String city,
        String country,
        BigDecimal lat,
        BigDecimal lng,
        boolean hasSalesRep
) {}
```

At 122 customers the saving is small. At 10k customers it matters:
serialising 10k × 19 fields to JSON is several megabytes; 10k × 7 is
under one. Parse cost on the client scales the same way.

The `hasSalesRep` flag is computed in the SQL itself
(`(salesRepEmployeeNumber IS NOT NULL) AS hasSalesRep`) so we never
load the FK just to discard it. Same idea for any future status flag
— compute it in the SELECT, expose only the boolean.

The wider lesson: **when a request will return many rows, design a
purpose-specific DTO**. The "one-true-DTO" approach (always serialise
the full row) is convenient until the row gets fat enough to matter.

References:

- [Spring docs — Records as DTOs](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html)
- [Mark Seemann — DTO design](https://blog.ploeh.dk/2014/12/16/zone-of-ceremony/) — the "ceremony cost" view of one-DTO-per-purpose

### Filter at the SQL boundary

The endpoint returns only **active** customers (matching the
`WHERE active = 1` filter the list uses) AND only those with non-null
`lat`/`lng` (un-geocoded customers can't be plotted):

```sql
SELECT customerNumber, customerName, city, country, lat, lng,
       (salesRepEmployeeNumber IS NOT NULL) AS hasSalesRep
FROM customers
WHERE active = 1
  AND lat IS NOT NULL
  AND lng IS NOT NULL
```

Both filters belong at the SQL level, not the application level.
Filtering in Java would mean shipping rows we'll discard, which
defeats the whole point of the cheap-projection DTO. Filtering in
SQL keeps the wire payload small and lets the database use its
index on `active` (V7).

### The C9-vs-C10 tension

C9 chose lazy on-demand geocoding. The trade-off: on day one, no
customer has coordinates, so the map is empty. There are several
ways to handle this:

| Strategy | Day-one map | Cost | Verdict |
|---|---|---|---|
| Pre-seed lat/lng in V8 | Complete | Manual lookup × 122 | Heavy |
| Auto-geocode on customer save | Complete-ish | ~1s per save, hits Nominatim every time | Slow + rude |
| **Lazy + transparent indicator** | Empty, but progress visible | One per click | What we picked |
| Bulk admin "Geocode all" button | Complete after one click | Walks list with 1.1s pacing | Reasonable follow-up |

We picked option 3: the map shows what's been geocoded, the page
prominently reports "X of Y geocoded," and the user can fill in
gaps via the per-customer Geocode button. The full bulk-geocode
admin action is a reasonable C-feature follow-up that we deliberately
left out of C10's scope to keep the feature focused on "many points
on one map."

The transparent-empty-state approach is also defensible on its own
merits: it surfaces the data-quality problem rather than hiding it.
A user who sees "5 of 122 geocoded" immediately understands the data
isn't complete; a user looking at a map with 5 dots and no context
might assume it's just a small dataset.

### Reusing `MarkersMapComponent` — minimal extension

F3 built `MarkersMapComponent` for offices: takes a `MapPoint[]`,
renders one default Leaflet pin per point, fits bounds. C10 needed
*per-marker* icon variants, which the original component didn't
support.

Two options:

**(a) Branch in two — separate `CustomerMarkersMap`.** Copy-paste
the bounds-fitting logic, add status colours. Quick, but creates
duplication that has to be kept in sync.

**(b) Extend the shared component.** Add an optional `iconClass`
field to `MapPoint`; when present, render an `L.divIcon` with that
class instead of the default pin. The change is ~10 lines, doesn't
break any existing caller, and any future entity that needs status
colours just sets `iconClass`.

Option (b) is right when the new behaviour is genuinely a
generalisation — "the marker can have a custom icon" is more
general than "always use the default pin." The existing single-icon
behaviour falls out naturally as the `iconClass` is undefined branch.

```ts
const icon = p.iconClass
  ? L.divIcon({
      className: p.iconClass,
      iconSize: [16, 16],
      iconAnchor: [8, 8],
      popupAnchor: [0, -8],
    })
  : defaultIcon;
```

Reference: [Leaflet docs — DivIcon](https://leafletjs.com/reference.html#divicon)

### `L.divIcon` + global CSS

`L.divIcon` wraps an empty `<div>` (or your supplied HTML) with the
class name you give it. The actual styling is your CSS. This
matters for component-scoped Angular styles:

> Leaflet creates the marker DOM elements **outside** Angular's
> component tree. Component-scoped CSS (the default in Angular) is
> generated with attribute selectors that the marker's div doesn't
> carry, so the styles never apply.

The fix is **global CSS**:

```scss
/* src/styles.scss */
.customer-marker-assigned   { background: #1976d2; }
.customer-marker-unassigned { background: #f57c00; }
```

These class names are the public surface of the component's design.
Anyone changing the colours edits one file; any new entity wanting
its own colour-coded markers adds its own classes here without
touching `MarkersMapComponent`.

References:

- [Angular — View Encapsulation](https://angular.dev/guide/components/styling#view-encapsulation)
- [Leaflet — Custom icons](https://leafletjs.com/examples/custom-icons/)

### Bounds-fitting via `L.FeatureGroup`

When you have N markers and want the map to "show all of them
nicely," you need to:

1. Compute the smallest rectangle (in lat/lng) that contains every
   marker.
2. Pan + zoom the map so that rectangle fits the viewport with some
   padding.

Leaflet has a primitive for the first part: `L.FeatureGroup`
collects layers and exposes `getBounds()`. The map itself has
`fitBounds(latLngBounds, { padding, maxZoom })` for the second part.

```ts
const bounds = this.markersGroup.getBounds();
this.map.fitBounds(bounds, {
  padding: [40, 40],
  maxZoom: 12,
});
```

The `maxZoom` matters: without it, a single marker would cause
`fitBounds` to zoom to street level (because a single point has zero
extent). Capping at city level keeps the view sensible for any number
of markers.

Reference: [Leaflet — `fitBounds`](https://leafletjs.com/reference.html#map-fitbounds)

### What about marker clustering?

For 122 customers spread across 30+ countries, individual markers
work fine — most cities have one or two customers and the visual
overlap is minimal. Where overlap exists (Paris, NYC, Tokyo) the
user can zoom in.

For larger datasets — 10k, 100k+ markers — clustering becomes
essential. The canonical solution is the
[`leaflet.markercluster`](https://github.com/Leaflet/Leaflet.markercluster)
plugin, which groups nearby markers into a count-bubble that
expands on zoom-in. Drop-in compatible with Leaflet; would slot
into `MarkersMapComponent` by wrapping the markers in
`L.markerClusterGroup()` instead of `L.featureGroup()`.

We deliberately didn't add the dependency for C10 — at the current
scale it's overkill, and the npm package + types add ~50KB to the
bundle for no visible benefit. When the data outgrows the
unclustered approach, the upgrade is one swap of `featureGroup()` →
`markerClusterGroup()` plus a script + CSS import.

Reference: [Leaflet.markercluster — README](https://github.com/Leaflet/Leaflet.markercluster#readme)

### Cache eviction at the new boundaries

C10 introduces two new caches:

```java
@Cacheable(cacheNames = "customersMapPoints")
public List<CustomerMapPointDTO> findAllMapPoints() { ... }

@Cacheable(cacheNames = "customersActiveCount")
public long countActiveCustomers() { ... }
```

Every existing mutation method (`create`, `update`, `delete`,
`bulkDelete`, `geocode`, `createBulk`) has had its `@CacheEvict`
list extended to include both. Skipping any one of them would
produce stale map data:

- A new customer wouldn't appear on the map until the cache aged out.
- A geocode wouldn't drop a marker on the next page load.
- A soft-delete wouldn't remove the marker.

This is the same cache-discipline lesson C2 paid for: **every cache
needs an eviction policy on every mutation that affects it**, and
the simplest correct policy is `allEntries = true` on every write.
Spending a re-fetch is cheaper than serving stale data.

### Live updates via the events stream

The map subscribes to the same `CustomerEventsService` (C7) the list
does:

```ts
this.eventsSub = this.liveEvents.events$.subscribe(() => this.load());
```

Any mutation to a customer — by you, by a teammate in another
browser tab, by a server-side scheduled job — triggers a refresh.
Cross-tab consistency without polling. The events fire DELETED for
both SOFT and DEEP_CASCADE deletes (see C7), which is right for the
map: in both cases the marker should disappear.

### XSS-safe popup HTML

Leaflet renders the popup body as raw HTML. The popup includes the
customer name and city/country, both of which originate from
user-controlled data. Same escape function as the customer-detail
page:

```ts
const esc = (s: string | undefined | null) =>
  (s ?? '').replace(/[&<>"']/g, ch => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[ch]!));
```

Inlined for the third time in the codebase now (mini-map detail
popup, customer-detail popup, customer-map popup). Worth pulling
into a shared `escapeHtml(s)` utility on the next pass — three
callers is the threshold where the abstraction earns its keep.

Reference: [OWASP — XSS prevention cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)

### Plain `<a href>` inside a Leaflet popup

The popup includes a "View details →" link to the customer's detail
page. We use a plain `<a href="/customers/103">` rather than Angular's
`routerLink` because the popup is rendered by Leaflet *outside* the
Angular DOM tree — Angular directives don't apply.

The plain link still works as a router navigation because Angular's
router intercepts same-origin clicks and turns them into in-app
navigations. The user gets the soft-navigation experience even
though the markup is raw HTML.

References:

- [Angular Router — `RouterLinkWithHref`](https://angular.dev/api/router/RouterLink)
- [Mozilla — `<a>` element](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/a)

### Route ordering: `'map'` before `':id'`

The Customer routes file now has a `'map'` segment that must come
**before** the bare `':id'` route — otherwise Angular's matcher
would treat `'map'` as an id and try to load the detail page for
"customer 'map'." Same lesson as the C6 backend route-ordering bug
from a few sessions back.

```ts
{ path: '', component: CustomerListComponent },
{ path: 'new', component: CustomerFormComponent },
{ path: 'map', component: CustomerMapComponent },        // ← before :id
{ path: ':id/lifetime-value', component: CustomerLifetimeValueComponent },
{ path: ':id/edit', component: CustomerFormComponent },
{ path: ':id', component: CustomerDetailComponent }
```

Angular's router doesn't have a regex constraint for path params (the
backend's `{id:\\d+}` trick has no direct frontend equivalent), so
explicit ordering is the clean fix.

References:

- [Angular Router — Route definition](https://angular.dev/guide/routing/define-routes)
- [Angular Router — Route matching](https://angular.dev/guide/routing/common-router-tasks#defining-routes)

## The code, walked through

### Computed signals derive everything from `mapPoints()`

```ts
mapPoints = signal<CustomerMapPoint[]>([]);

assignedCount   = computed(() => this.mapPoints().filter(p => p.hasSalesRep).length);
unassignedCount = computed(() => this.mapPoints().filter(p => !p.hasSalesRep).length);

markerPoints = computed<MapPoint[]>(() =>
  this.mapPoints().map(p => ({
    id: p.customerNumber,
    lat: Number(p.lat),
    lng: Number(p.lng),
    iconClass: p.hasSalesRep
      ? 'customer-marker-assigned'
      : 'customer-marker-unassigned',
    popupHtml: this.buildPopup(p),
  })));
```

One source-of-truth signal (`mapPoints`); three derivations
(`assignedCount`, `unassignedCount`, `markerPoints`). When data
arrives via `load()` and `mapPoints.set(...)` fires, all three
recompute lazily on next read. Adding a fourth derivation later
costs one `computed` call and zero coordination.

### `forkJoin` to load both endpoints in parallel

```ts
forkJoin({
  points: this.customers.mapPoints(),
  total: this.customers.activeCount(),
}).subscribe({
  next: ({ points, total }) => {
    this.mapPoints.set(points);
    this.activeCount.set(total);
    this.loading.set(false);
  },
  error: ...
});
```

The two endpoints are independent — one returns an array of map
points, the other returns a count — so we run them in parallel.
Total wall time is the slower of the two, not the sum. Same pattern
the customer-detail page used to load customer + sales rep
together.

### Status bar + legend share the breakdown

```html
<div class="status-bar">
  <strong>{{ mapPoints().length }}</strong> of
  <strong>{{ activeCount() }}</strong> active customers geocoded
</div>

<div class="legend">
  <div class="legend-row">
    <span class="legend-dot customer-marker-assigned"></span>
    <strong>{{ assignedCount() }}</strong> assigned
  </div>
  <div class="legend-row">
    <span class="legend-dot customer-marker-unassigned"></span>
    <strong>{{ unassignedCount() }}</strong> unassigned
  </div>
</div>
```

The legend dots reuse the same CSS classes the markers use, so they
will always match colour-wise — change the marker colour in
`styles.scss` and the legend updates automatically. No second copy
of the colour values to keep in sync.

## How to test

### First load — empty map

1. Restart backend (Flyway runs V8 if it didn't already).
2. Sign in. Navigate to **Customers**.
3. Click the **Map** button in the toolbar.
4. The page should show:
   - "0 of 122 active customers geocoded" (or similar).
   - Empty-state hint: "No geocoded customers yet. Open any
     customer's detail page and click Geocode address to add them
     to the map."
   - Legend with "0 assigned" and "0 unassigned" rows.

### Geocode a few customers

1. Open three customers' detail pages and click Geocode on each.
2. Return to the map. Should show "3 of 122 active customers
   geocoded" with three markers visible. Bounds-fit zooms to show
   all three.
3. Click a marker — popup shows the customer's name, city/country,
   sales-rep status, and a "View details →" link.
4. Click the link — should soft-navigate to the customer's detail
   page (no full reload).

### Status colour split

1. Find a customer with a sales rep (most do) and one without (set
   `salesRepEmployeeNumber = NULL` for one if needed). Geocode both.
2. Map shows one blue marker and one orange marker.
3. The legend reflects the count: "1 assigned · 1 unassigned."

### Live updates across tabs

1. Open the map in window A.
2. Open the same customer's detail page in window B and click
   Geocode (or change a customer's sales rep via the form).
3. Window A's map refreshes within ~100ms — new marker appears /
   colour changes.

### Soft-delete removes a marker

1. Geocode a customer, confirm marker appears on the map.
2. Soft-delete that customer from the list page.
3. Map refreshes (live event), marker is gone, count drops by one.

### Direct API check

```bash
curl -s -H "Authorization: Bearer $JWT" \
  http://localhost:9090/api/v1/customers/map-points | jq '. | length'
# → number of geocoded active customers

curl -s -H "Authorization: Bearer $JWT" \
  http://localhost:9090/api/v1/customers/active-count
# → total active customer count
```

## What you just learned

- **Many-points-on-one-map** as a pattern distinct from
  single-marker maps — different questions, different UX.
- **Cheap-projection DTOs** for endpoints that return many rows —
  designing the response shape for the consumer's actual needs
  rather than dumping every column.
- **Filter at the SQL boundary** — the database is the right place
  to drop rows you'll never use, not the application.
- **Extending a shared component minimally** — adding `iconClass` to
  `MapPoint` generalises `MarkersMapComponent` without breaking any
  existing caller.
- **`L.divIcon` + global CSS** as the way to do styled markers in a
  component-encapsulated framework, since Leaflet renders DOM
  outside the framework's tree.
- **Bounds-fitting via `L.FeatureGroup.getBounds()` + `fitBounds`**,
  with a `maxZoom` cap to prevent over-zooming a single point.
- **Cache discipline at every new boundary** — adding a `@Cacheable`
  method means adding it to every existing `@CacheEvict` list.
- **Plain HTML `<a href>` inside a Leaflet popup** still soft-
  navigates because Angular's router intercepts same-origin clicks.
- **The C9-vs-C10 design tension** — when an early choice (lazy
  geocoding) shapes a later feature (empty-on-day-one map), surfacing
  the gap transparently beats hiding it.

## Study materials

### Leaflet — multi-marker patterns

- [Leaflet — Quick start](https://leafletjs.com/examples/quick-start/)
- [Leaflet — DivIcon](https://leafletjs.com/reference.html#divicon)
- [Leaflet — `fitBounds`](https://leafletjs.com/reference.html#map-fitbounds)
- [Leaflet — Custom icons](https://leafletjs.com/examples/custom-icons/)

### Marker clustering (for when you outgrow plain markers)

- [Leaflet.markercluster — README](https://github.com/Leaflet/Leaflet.markercluster#readme) — canonical clustering plugin
- [Leaflet.markercluster — examples](https://leaflet.github.io/Leaflet.markercluster/example/marker-clustering-many-markers.html) — visual demos at 50k+ markers

### Component design

- [Refactoring — Extract Method / Extract Component](https://refactoring.com/catalog/extractFunction.html) — when to generalise a shared component
- [Joel Spolsky — Things you should never do, part I](https://www.joelonsoftware.com/2000/04/06/things-you-should-never-do-part-i/) — on "rewriting" vs "extending"

### Angular component styling

- [Angular — View Encapsulation](https://angular.dev/guide/components/styling#view-encapsulation)
- [Angular — Global vs component styles](https://angular.dev/guide/components/styling)

### XSS prevention

- [OWASP — XSS prevention cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [MDN — `String.prototype.replace`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/replace)
