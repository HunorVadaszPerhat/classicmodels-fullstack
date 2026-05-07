# Feature 12 — Sales dashboard with Chart.js

## What we built

A new `/dashboard` page that opens onto the company's sales picture:
four KPI tiles across the top (total revenue, total orders, average
order value, active customers) and four charts beneath (revenue by
month as a line/area chart, revenue by product line as a doughnut,
top 10 customers as a horizontal bar chart with click-to-drill-in,
orders by status as a doughnut). Everything comes from one HTTP call
to a new aggregation endpoint.

The frontend uses **Chart.js** for the charts, in deliberate contrast
to D3 from Feature 11. Two different approaches to "graphics in the
browser," used side-by-side so the trade-offs become concrete.

Files touched:

- `classicmodels-backend/src/main/java/.../dto/dashboard/SalesDashboardDTO.java` (new)
- `classicmodels-backend/src/main/java/.../repository/DashboardRepository.java` (new)
- `classicmodels-backend/src/main/java/.../service/DashboardService.java` (new)
- `classicmodels-backend/src/main/java/.../controller/DashboardController.java` (new)
- `classicmodels-ui/package.json` — added `chart.js`
- `classicmodels-ui/src/app/dashboard/dashboard.service.ts` (new)
- `classicmodels-ui/src/app/dashboard/dashboard.component.ts` (new)
- `classicmodels-ui/src/app/dashboard/dashboard.component.html` (new)
- `classicmodels-ui/src/app/dashboard/dashboard.routes.ts` (new)
- `classicmodels-ui/src/app/app.routes.ts` — `/dashboard` route
- `classicmodels-ui/src/app/home/home.component.ts` — nav link

## Why this is worth learning

Three threads converge in this feature.

First, **SQL aggregations as a data-engineering primitive.** Four of
the six backend queries are GROUP BY — the workhorse of OLAP, BI, and
every dashboard you've ever seen. Push aggregation to the database;
ship summary rows to the frontend.

Second, **Chart.js as a "good defaults" library.** Where D3 hands you
the LEGO bricks, Chart.js hands you assembled toys. You write a few
lines of config, and you get a tooltip-enabled, animated, responsive
chart. The cost is less control: doing something Chart.js wasn't
designed for is harder than just opening a D3 editor. We use
Chart.js here precisely because the task ("X by Y over time") is
exactly what it's good at.

Third, **the canvas/DOM lifecycle in Angular.** Charts live on
`<canvas>` elements that don't exist until Angular has rendered the
view, so you have to interact with them in `ngAfterViewInit` or
later. Charts also hold a 2D context and event listeners that
Angular doesn't know to clean up — so `ngOnDestroy` has to call
`chart.destroy()` explicitly.

## Background

### SQL aggregation refresher

The four aggregations on the dashboard each follow the same pattern:

```sql
SELECT bucket_column, AGGREGATE(measure_column)
  FROM table_or_join
 GROUP BY bucket_column
 ORDER BY bucket_column;
```

`GROUP BY` collapses many rows into one row per distinct value of
the listed column(s). `SUM`, `COUNT`, `AVG`, `MIN`, `MAX` are the
core SQL aggregate functions. `DATE_FORMAT(orderDate, '%Y-%m')`
turns a timestamp into a year-month string so we can group by
calendar month without a separate column.

Two more advanced patterns worth knowing once you're comfortable
with the basics:

- **`COUNT(DISTINCT x)`** — counts unique values of `x` rather than
  every non-null row. Used here to count "customers who ordered
  anything" rather than "rows in the orders table."
- **Window functions** (e.g. `SUM(...) OVER (PARTITION BY ...)`) —
  do GROUP-BY-style aggregation while keeping the per-row detail
  rows. Not used in this dashboard but invaluable once you need
  things like "running total" or "rank within group."

References:

- [PostgreSQL — Aggregate Functions](https://www.postgresql.org/docs/current/functions-aggregate.html) — even on MySQL, the conceptual reference is excellent
- [MySQL — GROUP BY clause](https://dev.mysql.com/doc/refman/8.0/en/group-by-modifiers.html)
- [Mode — A Beginner's Guide to SQL window functions](https://mode.com/sql-tutorial/sql-window-functions/)
- [Modern SQL — A complete guide to GROUP BY](https://modern-sql.com/feature/group-by)

### Chart.js fundamentals

Chart.js is an MIT-licensed canvas-based charting library. Its core
abstraction is the `Chart` constructor:

```ts
new Chart(canvas, {
  type: 'line' | 'bar' | 'doughnut' | ...,
  data: {
    labels: ['Jan', 'Feb', 'Mar'],
    datasets: [{ label: 'Revenue', data: [10, 20, 15] }],
  },
  options: { /* axes, legend, tooltips, callbacks */ },
});
```

Three things to understand:

1. **Datasets.** A chart can hold multiple datasets — line charts
   with two lines, bar charts with side-by-side bars, mixed charts.
   Each dataset has its own data array, color, and styling.
2. **Tree-shaking.** Chart.js v3+ is modular: you import + register
   only the controllers, scales, elements, and plugins you actually
   use. We register `LineController` + `BarController` +
   `DoughnutController` etc. once at module load. Forgetting to
   register one yields a cryptic "controller is not registered"
   error.
3. **Update vs. replace.** Once you have a `Chart` instance, you can
   mutate its `data` and call `chart.update()` to animate the change.
   This is preferable to destroying and recreating the canvas — same
   reason the org chart in Feature 11 uses incremental D3 joins.

References:

- [Chart.js — Getting Started](https://www.chartjs.org/docs/latest/getting-started/)
- [Chart.js — Integration with Angular](https://www.chartjs.org/docs/latest/getting-started/integration.html#vue-react-angular)
- [Chart.js — Tree-shaking](https://www.chartjs.org/docs/latest/getting-started/integration.html#bundlers-webpack-rollup-etc)

### Canvas vs. SVG (revisited)

Feature 11 used SVG for the org chart; this feature uses Canvas via
Chart.js. Why?

| | **SVG** (org chart) | **Canvas** (dashboard) |
|---|---|---|
| Hit detection | Free — DOM events on each shape | Library has to compute it |
| Accessibility | DOM is screen-readable | Charts are images to a screen reader |
| Performance | Slows past ~10k nodes | Smooth into the 100k range |
| Animation | CSS transitions / D3 transitions | Library-managed RAF loop |
| Styling | CSS rules apply | Pixel-by-pixel inside the canvas |

For the org chart, we wanted clickable nodes and accessible labels
(28 employees, easy to inspect). For a sales dashboard, we want
fast redraws, smooth animations, and built-in tooltip behavior over
many data points. Different tools for different jobs.

References:

- [Chart.js — Performance](https://www.chartjs.org/docs/latest/general/performance.html)
- [MDN — Canvas API](https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API)

### The Angular canvas lifecycle gotcha

A `<canvas>` element doesn't exist when `ngOnInit` fires — Angular
hasn't projected the template yet. So we use `@ViewChild` plus
`ngAfterViewInit`:

```ts
@ViewChild('myCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;

ngAfterViewInit() {
  if (this.data) this.makeChart();
}
```

If the data fetch finishes *before* `ngAfterViewInit`, store the
data and create the chart in `ngAfterViewInit`. If the fetch
finishes *after*, create the chart in the HTTP callback — but we
also need to make sure the canvas is no longer hidden under
`@if (loading()) { ... } @else { ... }`. Same `requestAnimationFrame`
trick we used for the D3 chart works here.

And: `Chart.js` creates a 2D context and binds resize listeners. If
the canvas is removed from the DOM (e.g. on route change) without
`chart.destroy()`, those resources leak. The `ngOnDestroy` hook
cleans them up.

References:

- [Angular — Component lifecycle](https://angular.dev/guide/components/lifecycle)
- [Angular — `@ViewChild`](https://angular.dev/api/core/ViewChild)
- [Chart.js — Canvas size + responsiveness](https://www.chartjs.org/docs/latest/configuration/responsive.html)

### Why a single endpoint for the whole dashboard

A common mistake: one HTTP endpoint per chart. Six fetches in
parallel, six independent loading states, and a small race-condition
window where the totals KPI and the chart breakdowns are computed
microseconds apart and don't sum to the same number.

The single-endpoint pattern (sometimes called "BFF aggregation" —
Backend-For-Frontend) folds everything into one logical snapshot:

- One request, one spinner, simpler error handling.
- All numbers come from the same logical point in time, so the
  totals and the breakdowns are guaranteed to agree.
- The backend can optimize: run the aggregations in parallel, share
  a single transaction, cache the whole snapshot together.

This is a real architectural pattern, not just a corner-cutting move.
Most production dashboards work this way.

References:

- [Sam Newman — Backend-for-Frontend](https://samnewman.io/patterns/architectural/bff/)
- [Microsoft — BFF pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends)

## The code, walked through

### Backend — aggregation queries

```java
public List<MonthlyRevenue> revenueByMonth() {
    final String sql = """
        SELECT DATE_FORMAT(o.orderDate, '%Y-%m') AS month,
               SUM(od.quantityOrdered * od.priceEach) AS revenue
          FROM orders o
          JOIN orderdetails od ON o.orderNumber = od.orderNumber
         GROUP BY month
         ORDER BY month
        """;
    // ... raw JDBC ResultSet loop ...
}
```

Six similar queries. Each returns a small list. The service stitches
them together into one `SalesDashboardDTO`.

The `JOIN` is necessary because revenue lives on `orderdetails`
(line items) but the date lives on `orders` (the header). One header
to many details — a textbook one-to-many relationship.

### Backend — service layer derives the avg

```java
BigDecimal avg = totalOrders == 0
    ? BigDecimal.ZERO
    : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
```

We could compute average order value in SQL too (`SUM(...)/COUNT(...)`),
but doing it in Java keeps the two underlying numbers (revenue,
order count) reusable elsewhere and makes the divide-by-zero guard
explicit. Tradeoff: one more network roundtrip if we wanted these
totals separately.

### Backend — single envelope DTO

```java
public record SalesDashboardDTO(
    BigDecimal totalRevenue,
    long totalOrders,
    BigDecimal averageOrderValue,
    long activeCustomers,
    List<MonthlyRevenue>     revenueByMonth,
    List<ProductLineRevenue> revenueByProductLine,
    List<CustomerRevenue>    topCustomers,
    Map<String, Long>        ordersByStatus
) { /* nested records */ }
```

Java records (since 14) are the perfect fit for DTOs: equality, a
constructor, accessors, all generated from the field list. Jackson
serializes them out of the box.

### Frontend — registering Chart.js components once

```ts
Chart.register(
  LineController, BarController, DoughnutController,
  LinearScale, CategoryScale,
  LineElement, PointElement, BarElement, ArcElement,
  Legend, Tooltip, Filler,
);
```

This is the tree-shaking dance. If you forget `LineElement`, line
charts render as a row of disconnected dots and Chart.js logs a
warning to the console. If you forget `LinearScale`, the y-axis is
empty. The error messages aren't always crystal clear — register
generously when you're learning, and tighten the imports later
when you know which ones matter.

### Frontend — creating one chart

```ts
this.revenueByMonthChart = new Chart(canvasEl, {
  type: 'line',
  data: { labels, datasets: [{ data: values, fill: true, tension: 0.3 }] },
  options: {
    responsive: true,
    maintainAspectRatio: false,    // critical: lets the canvas fill its parent
    plugins: {
      tooltip: { callbacks: { label: ctx => '  ' + this.formatMoney(ctx.parsed.y) } },
    },
    scales: {
      y: { ticks: { callback: v => this.formatMoney(v as number) } },
    },
  },
});
```

`responsive: true` + `maintainAspectRatio: false` is the only way to
get Chart.js to fill a CSS-sized parent container. Without the
second flag, Chart.js picks an arbitrary aspect ratio and ignores
the parent's height.

The `callbacks` on `tooltip` and `scales.y.ticks` let us format
numbers as `$2.4M` instead of `2400000`. They're plain JS callbacks
called during drawing — full access to the data, the context, the
formatted label.

### Frontend — update vs. recreate

```ts
if (this.revenueByMonthChart) {
  this.revenueByMonthChart.data.labels = labels;
  this.revenueByMonthChart.data.datasets[0].data = values;
  this.revenueByMonthChart.update();
} else {
  this.revenueByMonthChart = new Chart(canvasEl, config);
}
```

On the first call, we instantiate; on subsequent calls (e.g. the
Refresh button), we mutate and `update()`. `update()` animates the
diff — points slide to new positions, new bars grow up from zero.
This is the same pattern as D3's data join (Feature 11), just
expressed differently.

### Frontend — click-to-drill-in on the top customers chart

```ts
options: {
  onClick: (_event, elements) => {
    if (!elements.length) return;
    const idx = elements[0].index;
    this.router.navigate(['/customers', ids[idx]]);
  },
}
```

Chart.js exposes `onClick` with a list of "active elements" at the
click point. We grab the first one, use its index to look up the
customer id, and route. Same idea as the org chart's
`g.on('click', ...)` — chart-library-specific, but the pattern is
the same.

## How to test

1. Make sure the backend is running and you're logged in.
2. Click **Dashboard** in the toolbar (now first in the nav row).
3. The page should load in well under a second; you'll see four
   KPI tiles followed by four charts.
4. Hover any line/bar/doughnut slice — Chart.js renders a tooltip
   automatically with the formatted value.
5. Click a bar in **Top 10 customers** — you're routed to that
   customer's detail page.
6. Click **Refresh** — the charts re-fetch and animate to the new
   values. (To see numbers actually change, place an order in
   another window first.)
7. Resize the window — the charts re-render to fit the new size.
   No JavaScript on our end; Chart.js installs a `ResizeObserver`.

To verify the backend endpoint directly:

```sh
curl -H "Authorization: Bearer <jwt>" http://localhost:9090/api/v1/dashboard/sales | jq
```

You'll get the full `SalesDashboardDTO` JSON.

## What you just learned

- **SQL aggregations** (`SUM`, `COUNT`, `GROUP BY`, `JOIN`) as the
  workhorse of dashboards.
- **The single-snapshot endpoint pattern** (BFF aggregation) and
  why it beats one-endpoint-per-chart.
- **Chart.js component registration** and tree-shaking.
- **The canvas + Angular lifecycle**: `@ViewChild`,
  `ngAfterViewInit`, `chart.destroy()` in `ngOnDestroy`.
- **Update-via-mutation** as Chart.js's idiomatic way to keep the
  same chart instance through data changes.
- **`onClick` and tooltip callbacks** for chart interactivity.
- **`responsive: true` + `maintainAspectRatio: false`** as the
  recipe for filling a CSS-sized container.
- **Java records** as the right shape for transport DTOs, with
  nested record types modelling the chart datasets.

## Study materials

### SQL aggregation deep dive

- [Mode — SQL Tutorial: Aggregations](https://mode.com/sql-tutorial/sql-aggregate-functions/) — beginner-friendly
- [Use The Index, Luke! — Pagination & Aggregation](https://use-the-index-luke.com/sql/partial-results) — when aggregations need indexes
- [Modern SQL — Window functions](https://modern-sql.com/feature/window-functions) — the next step after GROUP BY
- [Mike Hadlow — Why I avoid ORMs](https://mikehadlow.blogspot.com/2012/06/avoid-orm-tax.html) — argues for plain SQL access for reporting

### Chart.js

- [Chart.js — Getting started](https://www.chartjs.org/docs/latest/getting-started/) — the official reference
- [Chart.js — Configuration](https://www.chartjs.org/docs/latest/configuration/) — every option explained
- [Chart.js — Samples](https://www.chartjs.org/docs/latest/samples/) — runnable examples per chart type
- [GitHub — chartjs-plugin-zoom](https://github.com/chartjs/chartjs-plugin-zoom) — adds pan/zoom (handy for time series)
- [GitHub — chartjs-plugin-datalabels](https://github.com/chartjs/chartjs-plugin-datalabels) — text labels on points/bars

### When to reach for a different tool

- [Observable Plot](https://observablehq.com/plot/) — D3 authors' "Chart.js-flavored" wrapper for D3
- [Vega-Lite](https://vega.github.io/vega-lite/) — declarative grammar of graphics
- [ECharts](https://echarts.apache.org/) — Apache project; richer than Chart.js, free
- [AG Charts](https://www.ag-grid.com/charts/) — commercial; "best in class" if you can pay
- [Plotly.js](https://plotly.com/javascript/) — scientific charts; 3D, statistical primitives

### Angular + Chart.js patterns

- [ng2-charts](https://valor-software.com/ng2-charts/) — opinionated Angular wrapper if you'd rather not write the `@ViewChild` boilerplate yourself
- [Angular signals + canvas tutorial](https://blog.angular-university.io/angular-canvas/) — comparable lifecycle considerations

### REST API design for dashboards

- [Sam Newman — Backend for Frontend](https://samnewman.io/patterns/architectural/bff/)
- [Microsoft — BFF pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends)
- [GraphQL alternative](https://graphql.org/learn/) — if your dashboards need to evolve faster than your endpoints
- [Martin Fowler — Reporting Database](https://martinfowler.com/bliki/ReportingDatabase.html) — at scale, dashboards live on a separate read replica

### Chart accessibility (out of scope here, but read it eventually)

- [WAI — Complex images](https://www.w3.org/WAI/tutorials/images/complex/) — how to expose chart data to screen readers
- [Chart.js a11y plugin discussion](https://github.com/chartjs/Chart.js/issues/9806) — current state of the art
