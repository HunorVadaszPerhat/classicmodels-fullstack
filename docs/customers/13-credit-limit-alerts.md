# Feature C13 — Credit-limit "near limit" warning + dashboard tile

## What we built

A computed credit-utilisation status for every active customer,
surfaced in three places:

1. **Customer detail page** — a colour-coded chip on the Credit
   limit row. Orange for "near limit" (≥ 80%), red for "over limit"
   (> 100%), grey "no limit set" for customers without a limit. No
   chip when utilisation is below 80%.
2. **Sales dashboard** — a new "Credit alerts" KPI tile showing
   the combined count, with a sub-line breaking it down into "N
   over · M near." The whole tile is a link to the alerts page.
3. **Credit alerts list page** at `/customers/credit-alerts` —
   a sortable table of every at-risk customer with their credit
   limit, outstanding balance, utilisation %, and status chip.
   Each row links to the customer's detail page.

The status itself is server-computed in a single SQL query that
aggregates orders + payments via two LEFT JOINs and divides
outstanding balance by credit limit. The threshold logic
(`utilisation > 1.0` → OVER_LIMIT, `≥ 0.8` → NEAR_LIMIT) lives in
one Java method so all three surfaces see the same number for the
same row.

Files touched:

- `classicmodels-backend/src/main/java/.../dto/customer/CustomerCreditStatusDTO.java` (new)
- `classicmodels-backend/src/main/java/.../service/CustomerCreditService.java` (new)
- `classicmodels-backend/src/main/java/.../dto/dashboard/SalesDashboardDTO.java` — `CreditAlerts` nested record
- `classicmodels-backend/src/main/java/.../service/DashboardService.java` — wires alert counts into the snapshot
- `classicmodels-backend/src/main/java/.../service/CustomerService.java` — cache evictions
- `classicmodels-backend/src/main/java/.../service/CustomerMergeService.java` — same
- `classicmodels-backend/src/main/java/.../service/OrderService.java` — same
- `classicmodels-backend/src/main/java/.../service/PaymentService.java` — same
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java` — `/credit-status`, `/credit-alerts`
- `classicmodels-ui/src/app/customers/customer.service.ts` — types + getters
- `classicmodels-ui/src/app/customers/customer-detail.component.ts` + `.html` — chip on credit-limit row
- `classicmodels-ui/src/app/customers/customer-credit-alerts.component.ts` (new)
- `classicmodels-ui/src/app/customers/customer.routes.ts` — `/credit-alerts` route
- `classicmodels-ui/src/app/dashboard/dashboard.component.ts` + `.html` — KPI tile
- `classicmodels-ui/src/app/dashboard/dashboard.service.ts` — extended type

## Why this is worth learning

Three concepts converge.

**Computed status from raw fields.** A customer's credit-limit
*number* is a static field on the row; their *utilisation status*
is derived from that field plus all their orders and payments.
Computing the derived view on every read is the right pattern —
caching the result is fine, but persisting a denormalised
"creditStatus" column would mean recomputing it on every order
mutation, which is invariably more work than computing it on read.

**Threshold UI design.** Three categories with colour-coded chips
is the canonical "alert level" pattern — green-amber-red, plus a
neutral "no data" state. The interesting design decision is where
the thresholds *live*: hardcoded constants (this feature),
configurable via `@Value` properties, or stored per-customer as
their own credit-watch level. We picked the simplest of the three
because the threshold-tuning UX would dwarf the actual feature.

**Surfacing the same derived value in multiple places.** Three
surfaces (chip, dashboard tile, alerts list) all show the same
status for the same customer. Three independent computations would
be a maintenance trap — one fix away from drift. One service
method + three callers is the right shape, and Spring's
`@Cacheable` makes the per-customer call cheap on the chip path
without pre-computing.

The new lesson, specific to Customer, is **alert-driven
navigation**. The dashboard tile isn't just a count; it's a *link*
to the actionable view. The user sees "5 over limit" and clicks
through to the list page that shows which customers and lets them
drill down. That's the genuine value-add of a dashboard alert tile
— it's a teleport to the work that needs doing, not a static
KPI display.

## Background

### The aggregation query — two LEFT JOINs in one SQL

Each customer's outstanding balance is the difference between two
sums computed across two child tables:

```sql
SELECT c.customerNumber,
       c.customerName,
       c.creditLimit,
       COALESCE(orders_total.total, 0)
           - COALESCE(payments_total.total, 0)         AS outstandingBalance,
       CASE
           WHEN c.creditLimit IS NULL OR c.creditLimit = 0 THEN NULL
           ELSE (COALESCE(orders_total.total, 0)
                 - COALESCE(payments_total.total, 0)) / c.creditLimit
       END                                              AS utilization
  FROM customers c
  LEFT JOIN (
      SELECT o.customerNumber,
             SUM(od.priceEach * od.quantityOrdered) AS total
        FROM orders o
        JOIN orderdetails od ON od.orderNumber = o.orderNumber
       GROUP BY o.customerNumber
  ) orders_total ON orders_total.customerNumber = c.customerNumber
  LEFT JOIN (
      SELECT customerNumber, SUM(amount) AS total
        FROM payments
       GROUP BY customerNumber
  ) payments_total ON payments_total.customerNumber = c.customerNumber
 WHERE c.active = 1
```

Two derived tables ("CTEs in spirit, subqueries in syntax") — one
sums each customer's order-line totals, the other sums each
customer's payments. LEFT JOIN means a customer with no orders
or no payments still appears (with `total = 0` after the
COALESCE). The outer SELECT subtracts and divides.

Filtering at the SQL boundary keeps the wire payload small: the
alerts endpoint's `HAVING utilization >= 0.80` ships only at-risk
rows. The detail-page chip endpoint runs the same query scoped to
one customer.

References:

- [MySQL — Derived tables](https://dev.mysql.com/doc/refman/8.0/en/derived-tables.html)
- [Use The Index, Luke! — JOIN types](https://use-the-index-luke.com/sql/join)
- [PostgreSQL — Window functions](https://www.postgresql.org/docs/current/tutorial-window.html) — for when you need running totals, not row totals

### Hardcoded thresholds vs. configurability

Three options for "where does the 0.80 / 1.00 threshold come from?":

**(a) Hardcoded** (this feature). One source-of-truth, dead simple,
everyone agrees on the numbers.

**(b) `@Value` from application.yml.** Operations can tune without
a code change. Useful when a tenant decides 0.75 is the right
"watch" line. Two more lines of code.

**(c) Per-customer.** Premium accounts get 0.70 ("watch sooner"),
casual buyers get 0.90 ("only flag when it's serious"). Real
customer-data platforms do this; the UI cost is non-trivial
(threshold field on the customer form, default values for
imports) so it's a real decision, not a free upgrade.

We picked (a). The thresholds are conventional — most "credit
utilisation" UIs you've seen use these or close to it — and
shipping configuration without a use case for it is YAGNI. If
operations ever ask "can we tighten the watch line?", (b) is a
20-minute change.

### Status enum with four states (including `NO_LIMIT`)

```java
public enum Status {
    OK,
    NEAR_LIMIT,
    OVER_LIMIT,
    NO_LIMIT
}
```

Three categories for the meaningful range plus a fourth for
"undefined" — when the customer has no credit limit set, dividing
by zero would either NaN or 500. We catch that case in the SQL
(`CASE WHEN c.creditLimit IS NULL OR c.creditLimit = 0 THEN NULL`)
and translate the `null` utilisation into `NO_LIMIT` in Java.

Why `NO_LIMIT` as a status rather than just leaving the field
null? **Because consumers want to render something.** The chip
"no limit set" tells the user they're looking at a customer
without a credit watch; a missing chip could mean either "no
limit" or "in the OK band." Explicit beats implicit.

### Where the status logic lives — Java, not the database

The category mapping happens in Java:

```java
if (utilization == null) status = NO_LIMIT;
else if (utilization.compareTo(OVER_LIMIT_THRESHOLD) > 0) status = OVER_LIMIT;
else if (utilization.compareTo(NEAR_LIMIT_THRESHOLD) >= 0) status = NEAR_LIMIT;
else status = OK;
```

We *could* push this into a `CASE` expression in SQL and have the
DB return a status string. We don't, because:

- The thresholds change occasionally; pushing them into SQL
  scatters them between Java and a string concatenated into a
  query.
- The Java branch is a four-line switch; the equivalent SQL is
  uglier and harder to test.
- If we ever add option (b) or (c) above, the threshold values
  flow naturally into Java method parameters. Reading them from
  SQL would mean parameterising every CASE branch.

Reference: [Pragmatic Programmer — DRY at the right level](https://pragprog.com/titles/tpp20/the-pragmatic-programmer-20th-anniversary-edition/) — the principle of co-locating logic that varies together

### Cross-cutting cache eviction (again)

Like C11's activity timeline, the credit cache depends on three
tables: `customers` (creditLimit), `orders` (line-item totals),
`payments` (amounts paid). Every service whose writes touch any
of these has to evict the credit caches:

- `CustomerService` — every mutation (create/update/delete/
  bulkDelete/createBulk/geocode).
- `CustomerMergeService` — merge changes both customers + their
  FKs.
- `OrderService` — create/update/delete/createBulk.
- `PaymentService` — same.

Three new caches got added — `customerCreditStatus`,
`customerCreditAlerts`, `customerCreditAlertCounts` — all to every
existing `@CacheEvict` list across four services. That's the same
pattern as C11 said it would be: **cross-table derived views are
cheap to add, and expensive to keep stale-free.**

If this stays a hot spot, the next refinement is per-customer
keyed eviction (only invalidate the affected customer's cache
entry rather than the whole map). For our scale `allEntries =
true` is the simple-but-correct choice.

### `BigDecimal` precision considerations

The aggregations are `SUM(priceEach * quantityOrdered)` and
`SUM(amount)`. Both produce `BigDecimal` totals via JDBC.
Subtracting and dividing keeps the BigDecimal type — important
because using `double` here would round-trip floating-point
precision errors (`0.1 + 0.2 !== 0.3`).

We round utilisation to 4 decimal places in the service before
returning:

```java
BigDecimal rounded = utilization.setScale(4, RoundingMode.HALF_UP);
```

Four decimals is enough precision for "84.32%" rendering and
keeps the JSON payload tidy — without rounding, BigDecimal
serialises with all 12+ digits its internal scale uses, e.g.
`0.847826086956`.

References:

- [Oracle — BigDecimal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [Java — Why BigDecimal for money](https://www.baeldung.com/java-bigdecimal-biginteger)

### Frontend: best-effort credit-status fetch

The detail page already loads the customer + sales rep in a
`forkJoin`. We add the credit-status fetch as a third leg, with a
`catchError(() => of(null))` so a credit-service failure doesn't
fail the whole page:

```ts
const credit$ = this.customers.getCreditStatus(id).pipe(catchError(() => of(null)));

forkJoin({ rep: rep$, credit: credit$ }).subscribe(({ rep, credit }) => {
  this.salesRep.set(rep);
  this.creditStatus.set(credit);
  this.loading.set(false);
});
```

Two best-effort fetches in one parallel batch; a failure in either
just leaves its slot null and the rest of the page still renders.
Same defensive pattern as the sales-rep lookup uses for deleted
employees.

### The whole-card link pattern

The dashboard's Credit-alerts tile wraps the entire card in an `<a>`:

```html
<a class="kpi-link" routerLink="/customers/credit-alerts">
  <mat-card class="kpi-card kpi-alerts">
    ...
  </mat-card>
</a>
```

Wrapping a card in an anchor instead of putting a small link inside
makes the entire card click-able — bigger target, better feel,
fewer "where do I click?" moments. The anchor's `display: block`
and `color: inherit` styling kill the default link visual so the
card still looks like a card (not a blue-underlined-text card).

The hit area being card-sized is especially important on touch:
[Fitts's Law](https://en.wikipedia.org/wiki/Fitts%27s_law) says
a target's clickability grows with its area, and tile-sized cards
are far easier to tap than link-sized text.

Reference: [Inclusive Components — Cards](https://inclusive-components.design/cards/) — the canonical writeup of "card as link" patterns

### Conditional warning border

The tile gets a left-border accent when there ARE alerts, and stays
neutral when zero:

```html
<mat-card class="kpi-card kpi-alerts"
          [class.has-alerts]="(d.creditAlerts.overLimitCount + d.creditAlerts.nearLimitCount) > 0">
```

```css
.kpi-alerts.has-alerts {
  border-left: 4px solid #e65100;
}
.kpi-alerts.has-alerts .kpi-value { color: #e65100; }
```

Subtle — the tile doesn't shout when there's nothing to act on,
and gets just enough visual weight when there is. Same restraint
the customer-detail "Inactive" chip uses: showing the state without
turning it into an alarm.

## How to test

### Make a customer over-limit

1. Pick a customer with a low credit limit (e.g. 21000) and many
   orders. Atelier graphique (#103) is a good candidate.
2. In the DB, temporarily lower the credit limit so the customer
   crosses the threshold:
   ```sql
   UPDATE customers SET creditLimit = 5000 WHERE customerNumber = 103;
   ```
3. Open #103's detail page. The Credit limit row should now show
   a red "Over limit · 18342%" chip (or however high the
   utilisation has gone).
4. Open the dashboard. The Credit alerts tile shows "1 over · 0 near"
   (assuming nothing else is over).
5. Click the tile. You land on the alerts list page with #103 as
   the only row.

Restore the credit limit afterward:

```sql
UPDATE customers SET creditLimit = 21000 WHERE customerNumber = 103;
```

### Direct API tests

```bash
# Per-customer status
curl -s -H "Authorization: Bearer $JWT" \
  http://localhost:9090/api/v1/customers/103/credit-status | jq

# Full alerts list
curl -s -H "Authorization: Bearer $JWT" \
  http://localhost:9090/api/v1/customers/credit-alerts | jq

# Dashboard with credit alerts
curl -s -H "Authorization: Bearer $JWT" \
  http://localhost:9090/api/v1/dashboard/sales | jq '.creditAlerts'
```

### Cache eviction across services

1. Note the credit-alert count on the dashboard.
2. Edit any order's quantities (raising the customer's outstanding
   balance enough to push them over a threshold).
3. Refresh the dashboard. The count should reflect the new state
   immediately — `OrderService.update` fires `@CacheEvict` on
   `customerCreditStatus`, `customerCreditAlerts`, and
   `customerCreditAlertCounts`.
4. Same for paying off a balance — `PaymentService.create` evicts
   the same caches.

### Three surfaces, one number

After the steps above, verify all three surfaces show the same
value for the affected customer:

- Dashboard tile total = (chip-state customers across the alerts page).
- Detail page chip status = the customer's row in the alerts page.

If they ever diverge, an eviction is missing somewhere — that's
the failure signature for cross-cutting cache discipline.

## What you just learned

- **Computed status from raw fields** — derive on read, cache the
  result; never persist a denormalised status column whose
  consistency is your problem.
- **Threshold UI design** — three categories + a "no data" state,
  with hardcoded thresholds that can grow into per-tenant or
  per-customer config when there's an actual driver.
- **Single-source-of-truth derivations** — one service method
  feeds three UI surfaces; consumers never re-implement the
  logic.
- **Aggregation via two LEFT JOINs** — pulling per-customer
  totals from child tables in one SQL, with COALESCE for the
  zero-children case.
- **Whole-card links** — bigger, more obvious tap targets than
  text links inside cards.
- **Conditional alert chrome** — colour the card only when there's
  something to act on; stay neutral otherwise.
- **Cross-cutting cache eviction at every boundary** — three
  caches added, four services updated; missing one would silently
  produce stale data.

## Study materials

### Aggregation patterns

- [MySQL — Derived tables](https://dev.mysql.com/doc/refman/8.0/en/derived-tables.html)
- [Use The Index, Luke! — JOIN types](https://use-the-index-luke.com/sql/join)
- [PostgreSQL — Common Table Expressions (CTEs)](https://www.postgresql.org/docs/current/queries-with.html) — the CTE form of the same idea, nicer in databases that support it

### Java BigDecimal

- [Oracle — BigDecimal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [Baeldung — Working with BigDecimal](https://www.baeldung.com/java-bigdecimal-biginteger)
- [Joshua Bloch — Effective Java, Item 60: Avoid float and double if exact answers are required](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/)

### UX patterns for alerts

- [Nielsen Norman Group — Alert design patterns](https://www.nngroup.com/articles/indicators-validations-notifications/)
- [Inclusive Components — Cards](https://inclusive-components.design/cards/) — the whole-card-link pattern
- [Fitts's Law](https://en.wikipedia.org/wiki/Fitts%27s_law) — why tile-sized targets feel better

### Spring caching

- [Spring docs — `@Cacheable` and `@CacheEvict`](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)
- [Spring docs — Cache abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
