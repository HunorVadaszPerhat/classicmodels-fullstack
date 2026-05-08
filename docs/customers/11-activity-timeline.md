# Feature C11 — Unified Customer Activity timeline

## What we built

A new page at `/customers/:id/activity` that shows one customer's
orders **and** payments interleaved into a single date-sorted feed,
with header aggregations on top and a chip group to filter to one
type at a time.

Header: four stat tiles — lifetime spend, total paid, outstanding
balance (highlighted when > 0), and last activity date. Below
that, three filter chips (All / Orders / Payments) with counts.
Below that, the timeline: each row carries a date column, a
type chip ("Order" with cart icon / "Payment" with payments icon),
and a kind-specific body. Order rows show item count + total +
fulfilment-status badge; payment rows show the check number and
amount.

The endpoint is one round trip: `GET /customers/{id}/activity`
returns a wrapper with the summary and the merged item list,
sorted by date descending. The frontend filters client-side via
the chip group — no second request to switch views.

This is the most genuinely customer-specific feature in Section B.
The pattern — heterogeneous merge, polymorphic rendering via a
`kind` discriminator — generalises to any "feed of mixed event
types" (notifications, audit logs, activity streams, support
tickets). Adding a third event type later is a one-list-and-merge
change, not a rearchitecture.

Files touched:

- `classicmodels-backend/src/main/java/.../dto/customer/CustomerActivityItemDTO.java` (new)
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerActivitySummaryDTO.java` (new)
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerActivityDTO.java` (new)
- `classicmodels-backend/src/main/java/.../service/CustomerActivityService.java` (new)
- `classicmodels-backend/src/main/java/.../service/CustomerService.java` — `customerActivity` cache eviction
- `classicmodels-backend/src/main/java/.../service/OrderService.java` — same eviction
- `classicmodels-backend/src/main/java/.../service/PaymentService.java` — same eviction
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java` — `/activity` endpoint
- `classicmodels-ui/src/app/customers/customer.service.ts` — types + `getActivity()`
- `classicmodels-ui/src/app/customers/customer-activity.component.ts` (new)
- `classicmodels-ui/src/app/customers/customer.routes.ts` — route registration
- `classicmodels-ui/src/app/customers/customer-detail.component.html` — Activity quick-action

## Why this is worth learning

Three concepts converge. **Heterogeneous merge** — combining two
sources whose row shapes don't match into one sorted stream, with a
discriminator that lets consumers tell them apart. **Discriminated
unions on the wire** as the simplest way to represent
"one-of-several kinds" in JSON. **Polymorphic row rendering with
`@switch`** — the TypeScript narrowing rules let each branch read
kind-specific fields without `!` assertions, so the template is
both readable and type-safe.

The new lesson, specific to Customer, is **designing for a future
third kind**. The bones we're laying down today (a flat DTO with a
`kind` discriminator, a service that fetches per-kind lists and
merges client-side) make adding "notes" or "support tickets" later
an additive change — one new query, one new constructor on the DTO,
one new `@case` branch in the template. Versus the alternative
(separate "Orders" and "Payments" pages) where adding a third kind
would mean a third page and three nav-bar buttons.

## Background

### Sealed interface vs. flat record with discriminator

Two natural shapes for "this is one of N kinds":

**Option A — sealed interface + per-kind record.** Type-safe at the
Java level; each record can declare only the fields it actually
needs.

```java
sealed interface ActivityItem permits OrderActivity, PaymentActivity {}
record OrderActivity(LocalDate date, int orderNumber, ...) implements ActivityItem {}
record PaymentActivity(LocalDate date, String checkNumber, ...) implements ActivityItem {}
```

Cost: serialising this through Jackson needs `@JsonTypeInfo` and
`@JsonSubTypes` annotations, and the frontend has to model the same
discriminated-union shape in TypeScript. Both ends do extra work.

**Option B — flat record with a `kind` discriminator.** What we
picked. One record with a `kind` string field plus all per-kind
fields nullable.

```java
record CustomerActivityItemDTO(
    String kind,                  // "ORDER" or "PAYMENT"
    LocalDate activityDate,
    Integer orderNumber,          // nullable, set when kind=ORDER
    String orderStatus,           // ditto
    BigDecimal orderTotal,
    Integer itemCount,
    String checkNumber,           // nullable, set when kind=PAYMENT
    BigDecimal paymentAmount
) {}
```

Less type-safe on the Java side — nothing stops a caller from
constructing an order row with payment fields filled in. We
mitigate that with named-constructor static factories
(`CustomerActivityItemDTO.order(...)` and `.payment(...)`) so
in practice the only callers always go through the right shape.

Frontend treats this as a TypeScript discriminated union via the
`kind` field — `if (item.kind === 'ORDER')` narrows the type
naturally, and TypeScript flags reads of payment fields inside that
branch. Same type safety as Option A at the consumer's end, with
zero serialisation ceremony.

For an internal API where one team controls both ends, the flat
shape wins on simplicity. Worth revisiting if a fifth or sixth kind
shows up, or if a public consumer needs strict typing.

References:

- [TypeScript handbook — Discriminated Unions](https://www.typescriptlang.org/docs/handbook/2/narrowing.html#discriminated-unions)
- [Jackson — Polymorphic deserialization](https://www.baeldung.com/jackson-inheritance) — Option A's serialisation story
- [JEP 409 — Sealed Classes](https://openjdk.org/jeps/409) — for reference

### Two queries + Java merge vs. one UNION ALL

Two ways to ship this from the database:

**Option A — UNION ALL.** One round trip. Each side of the UNION
NULL-pads the columns it doesn't have. Order rows want a `SUM` from
a JOIN, payment rows don't aggregate, and the result is a query
that reads like apologetic boilerplate.

**Option B — two queries, merged in Java.** What we picked. Each
query does one thing — orders with their totals, payments with
their amounts — and the merge is six lines of Java with
`Stream.sorted(Comparator.comparing(...).reversed())`.

```java
List<CustomerActivityItemDTO> items = new ArrayList<>(orders.size() + payments.size());
items.addAll(orders);
items.addAll(payments);
items.sort(Comparator.comparing(CustomerActivityItemDTO::activityDate).reversed());
```

The two-query approach also makes a third event type (notes,
support tickets) trivially easy to add — one new query, one new
`addAll`, the existing sort handles the merge. UNION ALL would
require padding every NULL column for every existing kind on every
new addition.

Throughput-wise, two round trips cost ~2ms on localhost. For per-
customer activity that gets called once per page open, the cost is
invisible. If this ever became a hot path on a high-latency database
connection, UNION ALL would be the right optimisation — but make
that change with a profiler in hand, not speculatively.

Reference: [PostgreSQL docs — Combining queries](https://www.postgresql.org/docs/current/queries-union.html) — UNION semantics for when you do need them

### `LEFT JOIN` to keep zero-line orders visible

The orders query joins to `orderdetails` to compute the total and
item count:

```sql
SELECT o.orderNumber, o.orderDate, o.status,
       COALESCE(SUM(od.priceEach * od.quantityOrdered), 0) AS orderTotal,
       COUNT(od.productCode) AS itemCount
FROM orders o
LEFT JOIN orderdetails od ON od.orderNumber = o.orderNumber
WHERE o.customerNumber = ?
GROUP BY o.orderNumber, o.orderDate, o.status
```

`LEFT JOIN` (vs. `INNER JOIN`) matters for orders that haven't had
their line items entered yet — the order row exists but no
`orderdetails` rows reference it. With `INNER JOIN` such an order
disappears from the result; with `LEFT JOIN` it appears with
`orderTotal = 0` and `itemCount = 0`. The `COALESCE(SUM, 0)` covers
the `SUM(NULL)` case — without it, an empty group returns NULL
which would surface as a Java `BigDecimal null` in the DTO.

Reference: [Use The Index, Luke! — JOIN types](https://use-the-index-luke.com/sql/join) — visual comparison of inner / left / right / full

### Cross-cutting cache eviction

Activity data depends on three tables: `customers`, `orders`,
`payments`. The `customerActivity` cache must be invalidated when
any of those changes:

- `CustomerService.create/update/delete/bulkDelete/createBulk/geocode` — already had `@CacheEvict` lists; we added `customerActivity`.
- `OrderService.create/update/delete/createBulk` — adds `customerActivity` to its existing eviction lists.
- `PaymentService.create/update/delete/createBulk` — same.

Skipping any of these would produce stale activity timelines until
the cache aged out. The `allEntries = true` flag means the cache is
wiped wholesale on every write, which is correct but coarse — every
mutation invalidates every customer's cached activity, even
customers unrelated to the change. For our scale this is fine; for
high write volume the right refinement is a per-customer key
(`@CacheEvict(key = "#customerNumber")`) but that requires the
write methods to know the customer id, which `OrderService.update`
does (via the order) but `OrderService.bulkDelete` doesn't without
extra lookups.

> When a feature spans multiple entities, **the eviction story is
> the feature's hidden complexity**. Adding the cache is one line;
> wiring up every cross-cutting eviction is N more.

References:

- [Spring docs — Cache eviction](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-evict)
- [Spring docs — `@CacheEvict(allEntries=true)` semantics](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-evict)

### Polymorphic rendering with `@switch`

The template uses Angular's `@switch` block to branch on
`item.kind`:

```html
@switch (item.kind) {
  @case ('ORDER') {
    <div>#{{ item.orderNumber }} — {{ item.orderStatus }}</div>
    <div>{{ item.itemCount }} items · {{ formatMoney(item.orderTotal) }}</div>
  }
  @case ('PAYMENT') {
    <div>Check {{ item.checkNumber }}</div>
    <div>{{ formatMoney(item.paymentAmount) }}</div>
  }
}
```

TypeScript's discriminated-union narrowing means each branch knows
which fields are non-null. Inside `@case ('ORDER')`, `item.orderNumber`
has type `number` (not `number | null`). Without the discriminator,
every read would need a `!` non-null assertion, which is ugly and
loses the help the compiler can give you.

Reference: [Angular docs — Built-in control flow](https://angular.dev/guide/templates/control-flow#switch)

### The status-badge colour map

Order statuses in the seed data are: `Shipped`, `Resolved`,
`In Process`, `On Hold`, `Disputed`, `Cancelled`. The page renders
each status as a small pill-shaped badge with colour-coded
background:

| Status | Background | Reading |
|---|---|---|
| Shipped, Resolved | green | "all good" |
| In Process, On Hold | orange | "needs attention" |
| Disputed, Cancelled | red | "something's wrong" |

This is a tiny mapping but makes the timeline scannable — a long
list of orders with a stripe of red sticks out instantly. The CSS
classes are `.status-shipped`, `.status-in-process`, etc., generated
from `'status-' + item.orderStatus?.toLowerCase()` in the template.
Status names with spaces (`In Process`) become `.status-in-process`
naturally because the lowercase doesn't strip whitespace — but
`In Process` becomes `in process` (with a space) which doesn't match
`.status-in-process`. **Bug warning**: this works for the seed
data's specific status names (no embedded spaces in the ones we
colour) but a status like `On Hold` would slip through as the
default grey because of the space. If you add a new multi-word
status, normalise the class name (`.toLowerCase().replace(/\s+/g, '-')`).

### One round trip, two stat sets

The endpoint returns both the timeline items AND the summary in a
single response wrapper:

```ts
interface CustomerActivity {
  summary: CustomerActivitySummary;
  items: CustomerActivityItem[];
}
```

The alternative is two endpoints (`/activity` + `/activity/summary`)
which the frontend would `forkJoin`. Two round trips, twice the
HTTP overhead, no real upside. Wrapping them keeps the API shape
purposeful and the client code linear.

The wrapper pattern reads as "everything you need to render this
page" — same shape as `CustomerLifetimeValueDTO` (where the order
history list lives next to the RFM scores). Both are deliberately
"the page model in one shape," not "every entity ever serialised
once."

Reference: [Backend for Frontend pattern](https://samnewman.io/patterns/architectural/bff/) — the principle of shaping endpoints around the consumer's needs

### Stat-tile responsive grid

The four stat tiles use `grid-template-columns: repeat(auto-fit, minmax(160px, 1fr))`,
which gives:

- 4 columns when the viewport is wide enough for 4 × 160px + gaps.
- 3 columns when not. 2 when not. 1 when narrow.

No media queries. No JavaScript. The grid algorithm computes the
breakpoints from the `minmax` constraint. This is one of CSS Grid's
genuinely magical features — the layout adapts to the available
space without the developer having to enumerate breakpoints.

References:

- [MDN — `grid-template-columns: repeat(auto-fit, minmax(...))`](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_grid_layout/Auto-placement_in_grid_layout)
- [CSS-Tricks — `auto-fit` vs `auto-fill`](https://css-tricks.com/auto-sizing-columns-css-grid-auto-fill-vs-auto-fit/)

### Why `mat-button-toggle-group` instead of `mat-chip-listbox`

Both Material widgets can do "select one of three." The chip
listbox reads visually like filter chips (which is what the spec
called for), but its API is awkward for a simple "value === 'ORDER'"
case — it always emits arrays even in single-select mode.

The button-toggle group is a button-like widget that maps cleanly
to a single value, exposes `[value]` / `(change)` two-way binding,
and the visual difference from chips is small at this size. We keep
the chip-shaped affordance (the count suffixes "(N)" inside each
button) without paying the chip-listbox tax.

Reference: [Angular Material — MatButtonToggleGroup](https://material.angular.io/components/button-toggle/overview)

## The code, walked through

### Service composes per-kind queries + merge

```java
public CustomerActivityDTO findActivityForCustomer(int customerNumber) {
    List<CustomerActivityItemDTO> orders = findOrders(customerNumber);
    List<CustomerActivityItemDTO> payments = findPayments(customerNumber);

    List<CustomerActivityItemDTO> items = new ArrayList<>(orders.size() + payments.size());
    items.addAll(orders);
    items.addAll(payments);
    items.sort(Comparator.comparing(CustomerActivityItemDTO::activityDate).reversed());

    CustomerActivitySummaryDTO summary = buildSummary(orders, payments);
    return new CustomerActivityDTO(summary, items);
}
```

Two sources, one merge, one summary pass. Adding a third kind would
be: one more `findX(...)`, one more `addAll`, the sort handles the
rest, and `buildSummary` gets one more loop. Linear cost in the
number of kinds.

### Named-constructor static factories

```java
public static CustomerActivityItemDTO order(...) {
    return new CustomerActivityItemDTO("ORDER", ..., null, null);
}

public static CustomerActivityItemDTO payment(...) {
    return new CustomerActivityItemDTO("PAYMENT", null, null, null, null, ...);
}
```

These keep the call sites readable and stop anyone from
accidentally constructing an order row with payment fields set.
Java doesn't have struct-literal syntax (`{ kind: 'ORDER', ... }`)
so factories are the cleanest substitute.

### Frontend filter as a `computed`

```ts
filter = signal<'ALL' | 'ORDER' | 'PAYMENT'>('ALL');

visibleItems = computed<CustomerActivityItem[]>(() => {
  const a = this.activity();
  if (!a) return [];
  const f = this.filter();
  if (f === 'ALL') return a.items;
  return a.items.filter(item => item.kind === f);
});
```

One source signal (`activity`), one filter signal (`filter`), one
derivation. Switching the chip selection re-runs the filter
lazily — only the visible-items view recomputes; the underlying
data, the summary, and the chip counts all stay put. Adding a
fourth filter option costs nothing.

### Outstanding-balance highlight via `[class.warn]`

```html
<div class="stat" [class.warn]="hasOutstanding(a)">
  <div class="stat-label">Outstanding</div>
  <div class="stat-value money">{{ formatMoney(a.summary.outstandingBalance) }}</div>
  ...
</div>
```

```ts
hasOutstanding(a: CustomerActivity): boolean {
  return Number(a.summary.outstandingBalance) > 0;
}
```

The warn class swaps the tile's neutral grey background for a
muted yellow. Subtle enough that it doesn't feel like an alarm,
visible enough that a user scanning multiple customers can spot
"this one owes money" instantly.

The string-to-number cast is safe because `outstandingBalance`
arrives from the backend as a Jackson-serialised BigDecimal (always
a parseable numeric string). If the value were missing, `Number(undefined)`
would be `NaN` which `> 0` would evaluate `false` — wrong direction
for a "no outstanding" customer, right direction here (no
flag = no warning).

## How to test

### Happy path — full activity feed

1. Open any customer's detail page (e.g. customer 103).
2. Click **Activity** in the quick-actions row.
3. You should see:
   - Title: "Atelier graphique — activity"
   - Four stat tiles. Lifetime spend > 0, total paid > 0, outstanding
     should be small or zero, last activity date populated.
   - Chip group: "All (N)", "Orders (M)", "Payments (M)" with counts.
   - Timeline list, most recent first, with order rows showing
     status badges and payment rows showing check numbers.

### Filter chips

1. Click **Orders** chip. Timeline narrows to order rows only.
   Header stat tiles unchanged.
2. Click **Payments**. Timeline shows only payments.
3. Click **All**. Full mixed timeline restored.

### Outstanding-balance warning

1. Find a customer whose lifetimeSpend > totalPaid (most do — the
   seed data isn't fully reconciled).
2. Open the activity page. The "Outstanding" tile should have a
   yellow background.
3. Find a customer with no orders or fully paid. Tile background
   stays grey.

### Empty states

1. Pick a brand-new customer (or temporarily delete all their
   orders/payments via the API).
2. Open Activity. Header tiles show zeros and dashes for dates.
3. Filter to Orders → "No activity in this view." Same for Payments.

### Direct API check

```bash
curl -s -H "Authorization: Bearer $JWT" \
  http://localhost:9090/api/v1/customers/103/activity | jq '.summary, (.items | length)'
```

### Cross-cache eviction

1. Open the activity page in window A — note the order count.
2. In window B, edit one of the customer's orders (change the
   status, save).
3. Reload window A's activity page. The status badge reflects the
   new value (eviction worked). Without the cross-service
   `customerActivity` eviction, the cached activity would still
   show the old status until the cache aged out.

## What you just learned

- **Heterogeneous merge** — combining two row shapes into one
  date-sorted stream by tagging each with a `kind` discriminator.
- **Discriminated unions on the wire** — JSON-friendly polymorphism
  via a flat record with a string discriminator, vs. the more
  ceremonious sealed-interface + Jackson-polymorphic-types
  alternative.
- **Two queries + Java merge over UNION ALL** — when each side wants
  a different shape (GROUP BY vs. plain), splitting into two
  focused queries keeps the SQL readable and makes future
  extension trivial.
- **`LEFT JOIN` + `COALESCE`** to keep degenerate rows visible with
  sensible default aggregates.
- **Cross-cutting cache eviction** as a feature's hidden cost —
  every service whose writes affect the cached projection has to
  evict, or the cache lies.
- **`@switch` for polymorphic row rendering** with TypeScript
  narrowing on the discriminator field.
- **Stat-tile responsive grids** via `repeat(auto-fit, minmax(...))`
  without media queries.
- **Single-endpoint wrapper** combining list + summary so the
  frontend doesn't need to coordinate two parallel requests.
- **Named-constructor static factories** as the cleanest substitute
  for struct-literal construction in Java.

## Study materials

### Discriminated unions and polymorphism

- [TypeScript handbook — Discriminated Unions](https://www.typescriptlang.org/docs/handbook/2/narrowing.html#discriminated-unions)
- [Jackson — Polymorphic deserialization](https://www.baeldung.com/jackson-inheritance)
- [JEP 409 — Sealed Classes](https://openjdk.org/jeps/409)

### Activity feeds as a UI pattern

- [Activity Streams 2.0 spec](https://www.w3.org/TR/activitystreams-core/) — overkill for our case but the canonical reference for the shape
- [GitHub's notification feed](https://github.blog/2017-03-22-introducing-improved-notifications/) — real-world example

### SQL & data shaping

- [PostgreSQL — Combining queries](https://www.postgresql.org/docs/current/queries-union.html) — UNION semantics
- [Use The Index, Luke! — JOIN types](https://use-the-index-luke.com/sql/join)
- [MySQL — `COALESCE` and NULL handling](https://dev.mysql.com/doc/refman/8.0/en/comparison-operators.html#function_coalesce)

### Spring caching across services

- [Spring docs — `@CacheEvict`](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-evict)
- [Spring docs — Cache abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)

### CSS Grid auto-layout

- [MDN — `grid-template-columns: repeat(auto-fit, minmax(...))`](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_grid_layout/Auto-placement_in_grid_layout)
- [CSS-Tricks — `auto-fit` vs `auto-fill`](https://css-tricks.com/auto-sizing-columns-css-grid-auto-fill-vs-auto-fit/)

### Angular control flow

- [Angular — `@switch` and `@case`](https://angular.dev/guide/templates/control-flow#switch)
- [Angular Material — MatButtonToggleGroup](https://material.angular.io/components/button-toggle/overview)
