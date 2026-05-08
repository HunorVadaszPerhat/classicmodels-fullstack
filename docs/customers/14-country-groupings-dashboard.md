# Feature C14 — Country groupings on the sales dashboard

## What we built

A new "Top 10 countries by revenue" chart on the sales dashboard,
plus the drill-back path that turns a click on any country bar into
a filtered customer list. The chart shows revenue per country
ranked highest-first; the tooltip carries the customer count
("$1.2M · 12 customers") so the bar's economic weight and the
underlying account count are visible in one glance. Clicking a bar
navigates to `/customers?country=France`, where the existing list
page picks up the URL param, applies it as a filter, and shows a
removable chip above the table so the active filter is visible
and reversible.

This is the dashboard's first chart whose primary value is **the
drill-down**, not the standalone visualisation. The backend was
extended in two complementary directions:

1. **Aggregation** — a new `revenueByCountry` SQL grouped by
   `customers.country` joined to orders + orderdetails. Bundled
   into the existing `SalesDashboardDTO` so the dashboard page
   still does one round trip.
2. **Filtering** — `/customers/find-all-paged` gained an optional
   `?country=` parameter that AND-composes with the existing
   `?search=` filter on the dynamic WHERE clause.

Files touched:

- `classicmodels-backend/src/main/java/.../dto/dashboard/SalesDashboardDTO.java` — `CountryRevenue` nested record + new field
- `classicmodels-backend/src/main/java/.../repository/DashboardRepository.java` — `revenueByCountry()` SQL
- `classicmodels-backend/src/main/java/.../service/DashboardService.java` — wires the new field into the snapshot
- `classicmodels-backend/src/main/java/.../repository/CustomerRepository.java` — `findAllPaged` and `countAll` get a `country` param
- `classicmodels-backend/src/main/java/.../service/CustomerService.java` — threads `country` through; cache key extended
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java` — `?country=` query param
- `classicmodels-ui/src/app/dashboard/dashboard.service.ts` — extended `SalesDashboard` type
- `classicmodels-ui/src/app/dashboard/dashboard.component.ts` + `.html` — new chart canvas, render method, click handler
- `classicmodels-ui/src/app/customers/customer.service.ts` — `country?` on `listPaged` opts
- `classicmodels-ui/src/app/customers/customer-list.component.ts` + `.html` — read `?country=`, render chip, clear handler

## Why this is worth learning

Three concepts converge.

**Drill-down as the chart's purpose.** Most charts on a dashboard
are read-only visualisations — you look, you take it in, you make
a mental note. A drill-down chart is different: the visualisation
*invites the next click*. The country bar isn't just "France
contributes 21% of revenue"; it's "click here to see those 12
customers." That's a different design constraint — the chart needs
to encode enough information to make the click decision easy
(hence the count in the tooltip), and the click target needs to
land on the right page.

**Cross-feature URL contracts.** The dashboard says "go to
`/customers?country=France`" — that's a URL contract between the
chart and the list page. The list page agrees to honour that
contract by reading the param on init and applying it as a
filter. Neither component knows about the other's
internals; they just agree on the URL shape. Future features —
the all-customers map, the credit-alerts page — could opt into the
same contract by reading the same query param.

**AND-composing optional filters.** Adding a `country` parameter
to a list endpoint that already has `search` means writing
SQL that handles all four cases: no filter, search only, country
only, both. The dynamic WHERE pattern from C2 generalises naturally
— each filter is one more `if` block that appends `AND ...` to the
WHERE plus a binding. New filters land for the cost of a copy-paste.

The new lesson, specific to Customer, is **the backend / frontend
parameter loop** — when a chart drill-back works, you have a
backend filter (server-side WHERE), a URL contract (the query
param), a frontend filter state (the chip), and a frontend
service argument (the `country?` field on `listPaged`). All four
have to agree, and in the same direction, or the click does
nothing or the wrong thing.

## Background

### Aggregation in SQL: why GROUP BY country directly

```sql
SELECT c.country,
       SUM(od.quantityOrdered * od.priceEach) AS revenue,
       COUNT(DISTINCT c.customerNumber)        AS customerCount
  FROM customers c
  JOIN orders o      ON o.customerNumber = c.customerNumber
  JOIN orderdetails od ON od.orderNumber  = o.orderNumber
 WHERE c.active = 1
 GROUP BY c.country
 ORDER BY revenue DESC
```

`customers.country` is a free-form `VARCHAR(50)` column, not a
foreign-key reference to a country reference table. That makes
the GROUP BY direct: one row per literal country string.

**`COUNT(DISTINCT customerNumber)`** rather than `COUNT(*)` because
each customer appears once per order, and a customer with 50
orders shouldn't count as 50 customers. DISTINCT collapses them
back to one.

**`WHERE c.active = 1`** to match the rest of the dashboard's
filtering — terminated customers shouldn't contribute to a
country's apparent revenue.

The column being free-form is a design weakness in the original
schema: "USA" and "Usa" and "U.S.A." would land in three buckets.
At the seed-data scale these inconsistencies don't show up, but
in real data you'd want a country reference table or an
ISO-3166 normalisation step. We don't fix it here because the
fix is bigger than C14's scope.

References:

- [MySQL — GROUP BY](https://dev.mysql.com/doc/refman/8.0/en/group-by-modifiers.html)
- [Use The Index, Luke! — GROUP BY performance](https://use-the-index-luke.com/sql/aggregations/group-by)
- [ISO 3166 country codes](https://www.iso.org/iso-3166-country-codes.html) — what a real country reference table would use

### Why no region-level grouping

The plan title says "country / region groupings." We implemented
country only.

Region (Europe, APAC, EMEA, Americas, etc.) is a level of
grouping above country, and there are several "right" ways to
define it — UN regions, World Bank regions, the Eurovision Song
Contest regions, your own sales-territory rollup. None of those
mappings live in the schema, so adding region grouping means
either:

- A reference table mapping country → region (proper but expensive
  if no other feature uses it).
- A hardcoded `CASE` statement in SQL (cheap but hides the
  business logic from the application layer).
- A Java-side mapping helper (lives next to the threshold logic
  in C13 — easy to test, easy to change).

We deferred the decision. C14 ships country-level rollup, which
is genuinely useful on its own; region-level can be a follow-on
once a clear mapping is chosen. The note in the doc is the only
thing we left behind.

### Drill-down via URL query params

```ts
this.router.navigate(['/customers'], {
  queryParams: { country: labels[idx] },
});
```

`router.navigate` with `queryParams` produces `?country=France` in
the URL. The list page reads it on init:

```ts
const initialCountry = this.route.snapshot.queryParamMap.get('country');
if (initialCountry) {
  this.countryFilter.set(initialCountry);
}
```

`snapshot.queryParamMap` is a one-shot read at component
construction. We deliberately don't subscribe to live changes —
the user clears the chip via a button that mutates the local
signal directly. Subscribing would create a feedback loop
("clearing the chip clears the URL → URL change re-clears the
chip → ...") that's harder to reason about than the simple
unidirectional flow.

The drill-down URL is shareable: pasting
`/customers?country=France` into a fresh tab lands you on the
filtered list. That's a small but real win — it means a sales
manager can drop a "look at this list" link into a Slack message
without explaining how to reach it.

References:

- [Angular — Router & query parameters](https://angular.dev/guide/routing/route-information#query-params)
- [MDN — URLSearchParams](https://developer.mozilla.org/en-US/docs/Web/API/URLSearchParams)

### Removable filter chip

The customer list already has a search box; adding a country chip
above the toolbar gives the country filter its own visible
affordance:

```html
@if (countryFilter(); as country) {
  <div class="filter-chips">
    <span class="filter-chip">
      <mat-icon>flag</mat-icon>
      Country: <strong>{{ country }}</strong>
      <button class="filter-chip-clear" (click)="clearCountryFilter()">
        <mat-icon>close</mat-icon>
      </button>
    </span>
  </div>
}
```

Three small UX choices:

- **Always visible when set.** The chip can't be missed; there's
  no "is the filter on?" ambiguity.
- **Always removable.** A small × button on the chip itself, not
  a separate "clear filter" link somewhere. Keeps the affordance
  next to the state.
- **One filter, one chip.** When more facets land later (date
  range, country, sales-rep) each will get its own chip in the
  same strip.

The chip is hand-rolled CSS rather than `mat-chip-listbox` because
this list isn't a multi-select widget — it's a status display
with one removable entry. Material's chip components are designed
for selection, and using them here would mean fighting the
component's idiom for a non-idiomatic use.

Reference: [Material Design — Filter chips](https://m3.material.io/components/chips/guidelines#filter-chips)

### Threading optional filters through the layers

The country filter touches every layer:

```
Frontend list URL ?country=France
  ↓
list component countryFilter signal
  ↓
customer.service.ts listPaged({ country })
  ↓
HTTP query param ?country=France
  ↓
Controller @RequestParam country
  ↓
Service findAllPaged(..., country)
  ↓
Repository findAllPaged(..., country) WHERE LOWER(country) = LOWER(?)
```

Six steps, same value flowing top to bottom. The discipline that
keeps this maintainable is making each layer's parameter list a
faithful reflection of the layer above:

- Anything that's a query param at the HTTP layer is a method
  parameter at the controller, the service, and the repository.
- Optional becomes nullable everywhere; defaults are the same;
  validation is the same.
- The cache key includes the new param (`{#page, #size, #sortBy,
  #asc, #search, #country}`) so different country filters don't
  share a cache slot.

Adding a fourth filter (say `salesRep`) means extending all six
layers in the same way. Each layer gets one more line; nothing
gets refactored.

References:

- [Spring docs — `@RequestParam`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html)
- [Spring docs — Cache abstraction (`@Cacheable` keys)](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)

### Case-insensitive country match

```sql
AND LOWER(country) = LOWER(?)
```

The seed dataset has inconsistent country casing — "USA" in some
rows, "Usa" in others. Case-folding both sides makes the filter
forgiving without normalising the data. The downside is that it
blocks an index on `country` from being used for the filter (the
function call defeats the index), which at our scale is
invisible.

For larger datasets the right fix is to **store** the country
already-normalised — write `country = country.trim().toLowerCase()`
in the repository's `save`/`update`, accept the data-quality
hit on existing rows once via a one-shot UPDATE migration, then
drop the `LOWER()` from the filter. The query becomes index-friendly
and cosmetic-case-difference becomes structurally impossible.

We didn't go that far for C14 because the migration would touch
data the rest of the app reads as-is.

References:

- [MySQL — Index usage and function calls](https://dev.mysql.com/doc/refman/8.0/en/function-optimization.html)
- [Use The Index, Luke! — Functions on indexed columns](https://use-the-index-luke.com/sql/where-clause/functions)

### `revenueByCountry` slot in `SalesDashboardDTO`

The existing dashboard DTO bundled four chart datasets — month,
product line, top customers, orders by status. We added
`revenueByCountry` as a fifth.

Why extend the wrapper rather than make a separate
`/dashboard/countries` endpoint:

- **One round trip.** The dashboard already pays the cost of one
  request; a sixth aggregation runs server-side in milliseconds.
  Splitting would add a second loading spinner and the
  consistency-window concern (between when the totals query and
  the country query ran, did anything change?).
- **Cohesion.** The dashboard is conceptually one snapshot.
  Spreading it across endpoints would gradually erode that.

The endpoint design philosophy here is the same as the C11
activity-page wrapper: **shape the response around what the page
needs, not around what the underlying tables are**.

### Top-N client-side cap

The backend returns every country (28-ish in the seed data). The
frontend caps to 10:

```ts
const top = d.revenueByCountry.slice(0, TOP_N);
```

A 28-row bar chart at dashboard-tile size is unreadable. The cap
is on the frontend rather than the backend because:

- The backend's data is potentially useful elsewhere (different
  consumer, different cap).
- The cap is a presentation concern, not a data concern — same
  rationale as why "show top 10 customers" lives in the backend
  but the actual chart limit is a frontend constant.

If a future feature wants "drill into countries 11–28 too,"
they can use the same data without a backend change. Putting the
cap on the backend would close that door.

## The code, walked through

### Dynamic WHERE with two optional filters

```java
StringBuilder where = new StringBuilder("WHERE active = 1");
List<Object> params = new ArrayList<>();
if (search != null && !search.isBlank()) {
    where.append(" AND (customerName LIKE ? OR contactLastName LIKE ? OR contactFirstName LIKE ?)");
    String like = "%" + search.trim() + "%";
    params.add(like); params.add(like); params.add(like);
}
if (country != null && !country.isBlank()) {
    where.append(" AND LOWER(country) = LOWER(?)");
    params.add(country.trim());
}
```

Two `if` blocks, each one self-contained. Bindings are appended
to `params` in the same order they appear in the SQL, which keeps
the positional `setObject` loop below correct without per-filter
bookkeeping. This is the same C2 dynamic-WHERE pattern grown by
one filter; the third or fourth filter would slot in identically.

### Cache key extension

```java
@Cacheable(cacheNames = "customersPaged",
           key = "{#page, #size, #sortBy, #asc, #search, #country}")
```

Adding `country` to the cache key was a one-line change but a
potentially-load-bearing one. Without it, a request for
`?country=France` would return the cached unfiltered page if a
prior call for the same `(page, size, sort, asc, search)` had
already populated the cache. Subtle, silent, wrong.

The discipline: **every value the SQL depends on belongs in the
cache key.** If you forget one, two different requests share a
slot.

### Click-to-drill chart handler

```ts
onClick: (_event, elements) => {
  if (!elements.length) return;
  const idx = elements[0].index;
  this.router.navigate(['/customers'], {
    queryParams: { country: labels[idx] },
  });
}
```

`elements[0].index` is the bar's index in the data array; we
look up `labels[idx]` to get the country string. `router.navigate`
with `queryParams` produces the URL the list page expects.

The same pattern is on the top-customers chart (which navigates
to a single customer detail). Two different drill-down shapes —
one to a list-with-filter, one to a single-record-detail — both
expressed in five lines of `onClick`.

### Read URL once on init

```ts
ngOnInit() {
  const initialCountry = this.route.snapshot.queryParamMap.get('country');
  if (initialCountry) {
    this.countryFilter.set(initialCountry);
  }
  ...
}
```

`snapshot` is the one-shot read; `queryParamMap` returns a Map-like
container with a `.get()` method that returns the first value or
null. We don't subscribe to live updates because the chip's
"clear" action mutates state directly — there's no need for
URL→state sync.

If a future feature wanted "land on /customers, type a search,
then change the country filter via a back-button," the right
extension is to keep the URL in sync with state changes (i.e.
two-way URL binding). For C14's drill-down → chip pattern, one-way
URL→state on init is enough.

### The chip strip

```html
@if (countryFilter(); as country) {
  <div class="filter-chips">
    <span class="filter-chip">
      <mat-icon>flag</mat-icon>
      Country: <strong>{{ country }}</strong>
      <button class="filter-chip-clear" (click)="clearCountryFilter()">
        <mat-icon>close</mat-icon>
      </button>
    </span>
  </div>
}
```

The outer `@if` keeps the strip out of the DOM entirely when no
filter is active — no empty space, no "0 chips" text. When the
country is set, one chip is rendered with an icon prefix, the
country name in bold, and an inline × button to clear it.

The chip is hand-rolled CSS (not `mat-chip-listbox`) because the
strip isn't a selectable input — it's a status display. Using a
chip-listbox here would mean fighting the component's
keyboard-navigation and selection semantics for a non-selection
use case.

## How to test

### Dashboard chart

1. Restart the backend so the new `revenueByCountry` SQL runs.
2. Navigate to /dashboard. The new "Top 10 countries by revenue"
   chart should appear in the charts grid below the others.
3. Hover over a bar — tooltip shows
   `$NN,NNN · NN customers`.
4. Click the bar for "USA" (or whichever country tops the list).

### Drill-down to filtered list

5. The browser navigates to `/customers?country=USA`. The list
   loads filtered to USA customers only.
6. A blue chip "Country: **USA** ×" appears above the toolbar.
7. The total row count at the bottom of the table reflects only
   USA customers.
8. Click the × on the chip. The chip disappears, the list reloads
   to show all customers, the URL is now `/customers` (the
   previous query param is gone — well, actually, it's still in
   the URL because we don't sync state→URL; but a refresh would
   bring it back, which is the expected behaviour for a
   drill-down link).

### Direct API check

```bash
# Country breakdown
curl -s -H "Authorization: Bearer $JWT" \
  http://localhost:9090/api/v1/dashboard/sales | jq '.revenueByCountry'

# Filtered list
curl -s -H "Authorization: Bearer $JWT" \
  'http://localhost:9090/api/v1/customers/find-all-paged?country=France' \
  | jq '.totalElements, (.content | map(.country))'
```

The second query should report only France entries.

### Search + country together

Type "atelier" in the list's search box while the country chip
"France" is active. The list narrows to French customers whose
name matches "atelier" — the AND-compose works as expected.

## What you just learned

- **Drill-down as a chart's primary purpose** — the click target
  is the value-add, not the visualisation alone.
- **URL contracts as cross-feature glue** — a query param
  shape both ends agree on means neither needs to know the
  other's internals.
- **AND-composing optional filters** with the dynamic-WHERE
  pattern from C2 — each new filter is one more `if` block.
- **Cache key extension discipline** — every value the SQL
  depends on belongs in the cache key, or different requests
  share a slot.
- **Case-insensitive matching with `LOWER()` + LOWER bind**, and
  the index trade-off it implies (versus normalising at write
  time and dropping the function call).
- **Top-N at the presentation layer** — caps that are about
  readability live with the chart, not the data.
- **Single round trip for the dashboard** — extending the
  `SalesDashboardDTO` over splitting into a second endpoint.
- **One-way URL→state on init** as the simplest right answer for
  drill-down landing flows.

## Study materials

### Dashboards & drill-down patterns

- [Tableau — Drill-down basics](https://www.tableau.com/learn/articles/dashboard-design) — opinionated but solid
- [Stephen Few — Information Dashboard Design](https://www.perceptualedge.com/) — the canonical book
- [Material Design — Filter chips](https://m3.material.io/components/chips/guidelines#filter-chips)

### URL-driven state in Angular

- [Angular — Router & query parameters](https://angular.dev/guide/routing/route-information#query-params)
- [Angular — `ActivatedRoute.queryParamMap`](https://angular.dev/api/router/ActivatedRoute#queryParamMap)
- [Tomas Trajan — Angular URL state patterns](https://medium.com/angular-in-depth/) — patterns for syncing URL ↔ component state

### SQL aggregation

- [MySQL — GROUP BY](https://dev.mysql.com/doc/refman/8.0/en/group-by-modifiers.html)
- [PostgreSQL — Aggregate functions](https://www.postgresql.org/docs/current/functions-aggregate.html) — same concepts in a different DB
- [Use The Index, Luke! — Functions on indexed columns](https://use-the-index-luke.com/sql/where-clause/functions) — why `LOWER(col) = ?` skips the index

### Chart.js drill-down

- [Chart.js — Interaction events](https://www.chartjs.org/docs/latest/configuration/interactions.html)
- [Chart.js — Bar chart options](https://www.chartjs.org/docs/latest/charts/bar.html)
