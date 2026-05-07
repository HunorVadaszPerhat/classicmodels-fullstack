# Feature 13 — Customer lifetime value

## What we built

A new page at `/customers/:id/lifetime-value` (linked from the
customer detail screen) that shows a single customer's analytical
profile: headline KPIs (total revenue, total orders, average order
value, predicted CLV), three RFM scores with explanations, a
marketing-flavored segment classification (Champions / Loyal /
At Risk / etc.), and a bar chart of every order on a timeline.

The interesting work happens in SQL: a Common Table Expression that
rolls up orders per customer, then a single window-function pass
(`NTILE(4) OVER (ORDER BY …)`) that buckets every customer into
quartile-based RFM scores in one go. The Java service derives a few
more numbers (AOV, predicted CLV, segment) and stitches everything
into one DTO. The frontend just renders.

Files touched:

- `classicmodels-backend/src/main/java/.../dto/customer/CustomerLifetimeValueDTO.java` (new)
- `classicmodels-backend/src/main/java/.../repository/CustomerLifetimeValueRepository.java` (new)
- `classicmodels-backend/src/main/java/.../service/CustomerLifetimeValueService.java` (new)
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java` — new endpoint
- `classicmodels-backend/src/main/java/.../controller/GlobalExceptionHandler.java` — `NoSuchElementException → 404`
- `classicmodels-ui/src/app/customers/customer.service.ts` — adds `getLifetimeValue`
- `classicmodels-ui/src/app/customers/customer-lifetime-value.component.ts` (new)
- `classicmodels-ui/src/app/customers/customer-lifetime-value.component.html` (new)
- `classicmodels-ui/src/app/customers/customer.routes.ts` — `:id/lifetime-value` route
- `classicmodels-ui/src/app/customers/customer-detail.component.html` — link button

## Why this is worth learning

Three threads converge.

**SQL window functions.** `NTILE`, `RANK`, `LAG`, `LEAD`,
`SUM(...) OVER (PARTITION BY ...)` — the family of operations you
can only do with windows. They let you rank, percentile, running-
sum, and "this row vs. the previous row" entirely in SQL. Window
functions are arguably the most underused feature in modern SQL,
because they don't show up in beginner tutorials. Once you've used
them, you'll keep finding new applications.

**RFM segmentation.** A 50-year-old technique from direct-mail
marketing that's still everywhere. Score every customer on three
axes — Recency, Frequency, Monetary — and use the combination to
group them into segments. Half of the value is the algorithm; the
other half is internalizing why these three axes are the right
ones (they capture the bulk of "is this customer valuable?" with
just three numbers).

**Customer Lifetime Value (CLV).** The dollar version of "how much
will this relationship be worth." Comes in historic flavors (sum of
past payments) and predictive flavors (a forecast). Real CLV models
get sophisticated quickly — Pareto/NBD distributions, gamma-gamma
spend models, churn probabilities. We use the textbook simple
formula here so the concept lands without the math taking over.

## Background

### SQL window functions

A window function looks like an aggregate (`SUM`, `COUNT`, `RANK`,
`NTILE`) followed by an `OVER (...)` clause. The `OVER` clause
defines a "window" of rows the function operates on, *without
collapsing the result rows the way `GROUP BY` does*.

```sql
SELECT customerNumber,
       monetary,
       NTILE(4) OVER (ORDER BY monetary DESC) AS monetaryQuartile
  FROM per_customer;
```

This returns one row per customer (no collapse), with an extra column
that ranks each customer into quartiles by monetary value. Compare
with `GROUP BY`, which would have produced 4 rows.

A few canonical window functions worth knowing:

| Function | What it does |
|---|---|
| `ROW_NUMBER()` | Sequential row numbers within the window. |
| `RANK()` | Like ROW_NUMBER but ties get the same rank, with gaps. |
| `DENSE_RANK()` | Same as RANK but no gaps. |
| `NTILE(n)` | Bucket rows into `n` equal-sized groups (quartile, decile, etc). |
| `LAG(col, n)` | Value from `n` rows back in the window. |
| `LEAD(col, n)` | Value from `n` rows ahead. |
| `SUM(col) OVER (...)` | Running totals, sliding sums, partition-scoped sums. |

The `OVER (...)` clause has three parts:
- `PARTITION BY` — like `GROUP BY` but for the window only.
- `ORDER BY` — defines the row ordering inside the window (mandatory
  for ranking functions).
- `ROWS BETWEEN ...` — frame: how many rows around the current row
  count toward the function. Default: from the start of the partition
  to the current row.

For RFM we use the simplest form: no partition (rank globally) and
just an `ORDER BY` that defines what "best" means.

References:

- [PostgreSQL — Window Functions](https://www.postgresql.org/docs/current/tutorial-window.html) — the canonical reference, even if you're on MySQL
- [MySQL — Window Function Concepts and Syntax](https://dev.mysql.com/doc/refman/8.0/en/window-functions-usage.html)
- [Modern SQL — Window functions](https://modern-sql.com/feature/window-functions) — language-agnostic, excellent explainer
- [Mode — Window Functions](https://mode.com/sql-tutorial/sql-window-functions/) — beginner-friendly tutorial

### Common Table Expressions (CTEs)

A CTE — the `WITH ... AS (...)` syntax — is a named subquery you
reference like a table for the duration of one statement:

```sql
WITH per_customer AS (
    SELECT customerNumber, SUM(amount) AS monetary
      FROM orders
     GROUP BY customerNumber
)
SELECT *,
       NTILE(4) OVER (ORDER BY monetary DESC) AS quartile
  FROM per_customer;
```

This reads top-to-bottom: "First compute per_customer; then run the
window function across it." Without a CTE you'd have to inline the
subquery (uglier, less readable) or create a temp table (more
machinery, less ad-hoc). For analytic queries that compose multiple
GROUP BYs, CTEs are the canonical structure.

Worth noting: CTEs are conceptually cleaner than subqueries but
historically MySQL (pre-8.0) didn't support them. Modern MySQL 8+
and every other major SQL database do.

References:

- [PostgreSQL — Common Table Expressions](https://www.postgresql.org/docs/current/queries-with.html)
- [MySQL — WITH clause](https://dev.mysql.com/doc/refman/8.0/en/with.html)
- [Modern SQL — Recursive CTEs](https://modern-sql.com/feature/with-recursive) — for the next-level usage

### RFM segmentation

RFM analysis was developed in the 1980s for direct mail catalogs:
who do we ship the printed catalog to next quarter? It survives
because three numbers are enough to capture most of "is this
customer valuable?":

- **Recency** — when did they last buy? Recent buyers are more likely
  to buy again.
- **Frequency** — how often do they buy? Frequent buyers are sticky.
- **Monetary** — how much do they spend per order or in total?
  High-spend customers contribute disproportionately.

For each axis, we score every customer 1–4 (or 1–5; 4 is more common
because it gives 64 cells; 5 gives 125, more granularity than most
teams use). 4 is best, 1 is worst. The score is a *quartile* — it
ranks each customer relative to the others.

The combination of three scores → segment names is judgement, not
math. The names you see in any RFM tutorial (Champions, Loyal,
At Risk, Lost, etc.) come from the marketing-analytics tradition and
are roughly standardized.

References:

- [Wikipedia — RFM (market research)](https://en.wikipedia.org/wiki/RFM_(market_research))
- [Putler — Complete guide to RFM analysis](https://www.putler.com/rfm-analysis/) — has the standard segment matrix
- [Bruce Hardie's RFM page](http://brucehardie.com/notes/039/) — academic perspective, links to original papers

### Customer Lifetime Value (CLV) — historic vs. predictive

Two different things share the name "CLV":

- **Historic CLV** — sum of every dollar this customer has paid us.
  Backwards-looking. Easy to compute, useful for ranking.
- **Predictive CLV** — forecast of future value. Forwards-looking.
  Harder to compute well, but the one the business actually wants.

Our `predictedClv` uses the simplest predictive formula:

```
Predicted CLV = AOV × purchase-frequency-per-year × estimated-lifespan
```

`AOV` is computed from history. `purchase frequency` is extrapolated
linearly from the customer's tenure. `lifespan` we hard-code at 3
years.

Real models do better. A few you'll encounter:

- **Cohort retention curves** — track how a cohort's purchase rate
  decays over time, multiply.
- **Pareto/NBD model** — probabilistic; predicts when a customer
  goes "dormant" given their past purchase pattern.
- **Gamma-gamma spend model** — forecasts AOV variance per customer.
- **ML approaches** — gradient-boosted trees, neural nets on
  per-customer feature vectors.

For learning the concept, the simple formula is fine; for production,
budget a serious project to do it right.

References:

- [Bruce Hardie — RTM Models for CLV](http://brucehardie.com/notes/) — the academic gold standard
- [Lifetimes — Python library for the Pareto/NBD + Gamma-Gamma stack](https://lifetimes.readthedocs.io/) — what you'd use in practice
- [Shopify — Calculating CLV](https://www.shopify.com/blog/customer-lifetime-value) — practical primer
- [HBR — How Valuable Is Word of Mouth?](https://hbr.org/2007/10/how-valuable-is-word-of-mouth) — the strategic case for caring about CLV

### Frontend pieces revisited

This page reuses everything Feature 12 set up: Chart.js components
already registered, the same canvas-lifecycle pattern with
`@ViewChild` + `ngAfterViewInit`, the same money-formatting helper.
The only new UI primitives are Material's `mat-chip` (for the
segment badge — though we use a custom span+CSS for finer color
control) and `MatTooltipModule` for the explanatory hover text.

The route follows a deliberate convention. Where customer detail is
`/customers/:id` (the system of record for that customer), the
analytical view is `/customers/:id/lifetime-value` — a sibling
route, not a tab inside detail. The doc on the dashboard explains
why analytical views deserve their own surface; the same reasoning
applies here.

## The code, walked through

### The RFM SQL — the centerpiece

```sql
WITH per_customer AS (
    SELECT
        o.customerNumber,
        c.customerName,
        MIN(o.orderDate) AS firstOrderDate,
        MAX(o.orderDate) AS lastOrderDate,
        COUNT(DISTINCT o.orderNumber) AS frequency,
        COALESCE(SUM(od.quantityOrdered * od.priceEach), 0) AS monetary,
        DATEDIFF(CURDATE(), MAX(o.orderDate)) AS recencyDays
      FROM orders o
      JOIN customers c    ON o.customerNumber = c.customerNumber
      JOIN orderdetails od ON o.orderNumber  = od.orderNumber
     GROUP BY o.customerNumber, c.customerName
)
SELECT customerNumber, customerName, firstOrderDate, lastOrderDate,
       frequency, monetary, recencyDays,
       NTILE(4) OVER (ORDER BY recencyDays ASC)  AS recencyNtile,
       NTILE(4) OVER (ORDER BY frequency  DESC) AS frequencyNtile,
       NTILE(4) OVER (ORDER BY monetary   DESC) AS monetaryNtile
  FROM per_customer;
```

The CTE is doing one job: collapse order detail rows into one row
per customer, with the four "raw" measures (first/last order date,
frequency, monetary). The outer SELECT then runs three independent
NTILE calls, each ranking customers along a different axis.

NTILE returns 1 for the *first* group when sorted ASC. So:
- For recency, sorted ASC, 1 = "smallest recencyDays" = "most recent"
  = best.
- For frequency / monetary, sorted DESC, 1 = "largest count / spend"
  = best.

The Java side then flips Recency back: `5 - rawNtile`. After this
flip, the convention is uniform: **4 = best, 1 = worst** across all
three axes.

```java
int recencyScore   = 5 - recencyNtile;
int frequencyScore = 5 - frequencyNtile;
int monetaryScore  = 5 - monetaryNtile;
```

### Why we filter in Java rather than SQL

```java
while (rs.next()) {
    if (rs.getInt("customerNumber") != customerNumber) continue;
    // ...
}
```

We pull every customer's row through the driver and pick the one we
want in Java. That's deliberate. NTILE has to see the *whole
distribution* to compute quartiles correctly. Filtering inside the
CTE would make NTILE rank one row against itself, producing
nonsense.

If we wanted to keep the filter in SQL, we'd write:

```sql
SELECT *
  FROM (the whole NTILE query)
 WHERE customerNumber = ?
```

That works too — slight tradeoff: more SQL, but only one row crosses
the JDBC boundary. For this dataset (≤ 122 customers) the
Java-side filter is fine.

### Segment classification

```java
private Segment classifySegment(RfmScore rfm) {
    int r = rfm.recency(), f = rfm.frequency(), m = rfm.monetary();

    if (r >= 4 && f >= 4 && m >= 4) return Segment.CHAMPIONS;
    if (r >= 3 && (f + m) >= 7)     return Segment.LOYAL;
    if (r <= 2 && f >= 4 && m >= 4) return Segment.CANT_LOSE;
    if (r <= 2 && f >= 3 && m >= 3) return Segment.AT_RISK;
    // ...
}
```

Order matters: most-specific first, fall-throughs last. The rules
are a discrete decision tree over the 64 RFM cells (4 × 4 × 4) — you
could equivalently encode this as a 64-row lookup table; chained
ifs are easier to read once you have a sense of the shape.

### Predicted CLV

```java
BigDecimal ordersPerYear = tenureDays > 0
    ? BigDecimal.valueOf(row.frequency())
        .multiply(BigDecimal.valueOf(365))
        .divide(BigDecimal.valueOf(tenureDays), 2, RoundingMode.HALF_UP)
    : BigDecimal.valueOf(row.frequency());

BigDecimal predictedClv = aov
    .multiply(ordersPerYear)
    .multiply(BigDecimal.valueOf(ASSUMED_LIFESPAN_YEARS))
    .setScale(2, RoundingMode.HALF_UP);
```

`aov × ordersPerYear × lifespan`. The divide-by-zero guard handles
"this customer has only one order, and tenureDays is therefore 0."
We fall back to "they bought N orders in the same day, project that
forward as N orders/year" — not particularly defensible, but better
than blowing up.

### Frontend — the segment badge

```html
<span class="segment-badge {{ d.segment }}"
      [matTooltip]="segmentTooltip(d.segment)">
  {{ formatSegment(d.segment) }}
</span>
```

Eight CSS classes — one per segment — pick the badge color. The
`{{ d.segment }}` interpolation puts the enum value (e.g. `LOST`)
into the class list, which the stylesheet keys on
(`.segment-badge.LOST`). `formatSegment` converts `LOST` →
`"Lost"`, `NEW_CUSTOMERS` → `"New Customers"`.

### Frontend — RFM dial cards

Three `mat-card` blocks, each showing the score and a textual
interpretation generated by `rfmMeaning(axis, score)`:

```ts
rfmMeaning(axis: 'r' | 'f' | 'm', score: number): string {
  const labels = ['', 'Low', 'Below avg.', 'Above avg.', 'High'];
  const axisLabel = axis === 'r' ? 'recency' :
                    axis === 'f' ? 'frequency' :
                                   'monetary value';
  return `${labels[score]} ${axisLabel}`;
}
```

Score 1 → "Low recency", 4 → "High recency". Quick to read, no
math required by the user.

### Frontend — order timeline chart

```ts
this.historyChart = new Chart(canvasEl, {
  type: 'bar',
  data: { labels, datasets: [{ data: values, backgroundColor: '#1565c0' }] },
  options: {
    plugins: {
      tooltip: {
        callbacks: {
          title: items => `Order #${tooltips[items[0].dataIndex]}`,
          label: ctx => '  ' + this.formatMoney(ctx.parsed.y),
        },
      },
    },
    // ...
  },
});
```

Same Chart.js patterns as Feature 12: registered components, money
formatting via `tooltip.callbacks`, `responsive: true` +
`maintainAspectRatio: false`. One bar per order, sized by amount,
ordered chronologically.

## How to test

1. Make sure backend + frontend are running.
2. Go to **Customers**, open any customer's detail page (e.g. id 124,
   "Mini Gifts Distributors Ltd." — known to be a top spender in the
   classic models seed).
3. Click **View lifetime value**.
4. The page should show:
   - The customer name, segment badge in a color-coded chip
     (hover for explanation).
   - Four KPI tiles: total revenue, total orders, AOV, predicted CLV.
   - Three RFM cards each with score, axis name, and meaning.
   - A bar chart of every order on a date axis.
5. Try a customer with limited history (e.g. id 144, "Volvo Model
   Replicas, Co" — a smaller account). Their segment should be
   different.
6. Try a customer with no orders (e.g. id 125 if it exists). You
   should see "This customer has no order history yet" instead of a
   crash — that's the 404 path being handled.
7. Verify the backend response directly:

```sh
curl -H "Authorization: Bearer <jwt>" \
     http://localhost:9090/api/v1/customers/124/lifetime-value | jq
```

## What you just learned

- **Window functions** as the SQL primitive for "compute rankings /
  running totals without collapsing rows."
- **`NTILE(n)`** specifically, for quartile / decile / percentile
  bucketing.
- **Common Table Expressions** as the readable structure for
  multi-step analytical queries.
- **RFM segmentation** as the canonical 3-axis customer scoring.
- **Historic vs. predictive CLV** and the gulf between the simple
  formula and a real model.
- **Mapping enum values to CSS classes** for declarative color
  coding without a color lookup table in TypeScript.
- **Custom tooltip callbacks** on Chart.js for richer hover text.
- **`@RestControllerAdvice` + `NoSuchElementException → 404`** as
  the clean way to translate "missing data" exceptions into HTTP
  status codes globally.

## Study materials

### SQL — windows + CTEs

- [PostgreSQL — Window Functions](https://www.postgresql.org/docs/current/tutorial-window.html)
- [MySQL — Window Functions](https://dev.mysql.com/doc/refman/8.0/en/window-functions.html)
- [Mode — Window Functions tutorial](https://mode.com/sql-tutorial/sql-window-functions/)
- [Modern SQL](https://modern-sql.com/) — exhaustive reference; window functions, CTEs, JSON, recursive queries
- [Markus Winand — SQL Performance Explained](https://sql-performance-explained.com/) — book; the chapter on indexed window functions is essential

### RFM and customer segmentation

- [Wikipedia — RFM](https://en.wikipedia.org/wiki/RFM_(market_research))
- [Putler — Complete RFM Analysis Guide](https://www.putler.com/rfm-analysis/) — segment matrix you'll see referenced everywhere
- [Bruce Hardie — Marketing & CLV research notes](http://brucehardie.com/notes/) — academic primary sources
- [Hubspot — Customer Segmentation 101](https://blog.hubspot.com/service/customer-segmentation) — broader segmentation context

### CLV — practitioner

- [Shopify — Customer Lifetime Value](https://www.shopify.com/blog/customer-lifetime-value)
- [Survey Sparrow — CLV Formulas Explained](https://surveysparrow.com/blog/customer-lifetime-value-formula/)
- [Lifetimes — Python library](https://lifetimes.readthedocs.io/) — actual library marketers use for predictive CLV
- [Squark — Predictive CLV explainer](https://squark.ai/predictive-clv/)

### CLV — academic

- [Fader, Hardie, Lee — RFM and CLV: Using Iso-value Curves for Customer Base Analysis](https://www.brucehardie.com/papers/rfm_clv_2005-02-16.pdf) — seminal paper
- [Pareto/NBD — original BG/NBD paper](https://brucehardie.com/papers/018/) — the workhorse model

### SQL design for analytics

- [Joe Celko — SQL for Smarties](https://www.elsevier.com/books/joe-celkos-sql-for-smarties/celko/978-0-12-800761-7) — book; the chapter on window functions and analytical queries is canonical
- [Itzik Ben-Gan — T-SQL Querying](https://www.itzikben-gan.com/Books/) — book; T-SQL specific but the patterns generalize

### Spring + analytics endpoints

- [Spring docs — `JdbcTemplate`](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html) — what we'd use if we hadn't built our own thread-local repository pattern
- [Spring docs — `@RestControllerAdvice`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)
- [Baeldung — Spring exception handling](https://www.baeldung.com/exception-handling-for-rest-with-spring)

### Chart.js — going further

- [Chart.js — Custom tooltips](https://www.chartjs.org/docs/latest/configuration/tooltip.html#external-custom-tooltips) — for fully custom HTML tooltips
- [Chart.js — Time scale](https://www.chartjs.org/docs/latest/axes/cartesian/time.html) — for proper date-aware x-axes (we'd want this if the timeline gets dense)
