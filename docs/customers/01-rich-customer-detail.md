# Feature C1 — Rich customer detail page

## What we built

The customer detail page used to be a flat field-list — a `mat-card`
with thirteen `<div><strong>Label:</strong> value</div>` rows in a
single column, including raw display of `salesRepEmployeeNumber: 1370`
with no clue who employee 1370 actually is. We rebuilt it as a
card-based page grouped by topic, with three sections — Contact,
Address, Account — and a header that carries the customer name, a
country chip, and quick-action buttons (Edit, Lifetime value).

The biggest functional change is that the sales-rep foreign key is now
*resolved*: we make a second HTTP call to `GET /employees/{id}`,
render the rep's name and job title, and link the row to that
employee's detail page. If the lookup fails (rep deleted, network
error) we degrade to "Employee #1370 (couldn't resolve)" rather than
blanking the page.

This is the same shape as the employee-detail rewrite from Feature 24,
applied to a different entity. The patterns are familiar; the
customer-specific bits are address formatting and currency display.

Files touched:

- `classicmodels-ui/src/app/customers/customer-detail.component.ts`
- `classicmodels-ui/src/app/customers/customer-detail.component.html`

## Why this is worth learning

This feature stitches together five small ideas, three of which are
recaps and two of which are new. **Card-based layout with grouped
sections** is the recurring pattern for any "show one record" page —
once you've built it for Employee and Customer, you can stamp it out
for Office, Order, Product without thinking. **Foreign-key resolution
with `forkJoin` + `catchError`** is the standard Angular play for
"the API gave me an id, I want to render a name." That's the recap.

The new ideas are **`computed()` signals** for derived view state
(here, the multi-line address), and the **three-state render pattern
for FK resolution** — unassigned, lookup-failed, lookup-succeeded —
each visually distinct so the user can tell at a glance which
situation they're in. Plus the small but useful habit of using
`Intl.NumberFormat` for currency rather than string concatenation.

## Background

### `computed()` — derived signals

Angular signals come in three flavours: `signal()` for writable state,
`computed()` for derived state, and `effect()` for side effects. A
`computed` signal recomputes only when one of the signals it reads
from changes, and only when something actually reads its value (lazy
evaluation). The result is memoised — read the computed signal ten
times in one render pass and the function body runs once.

```ts
addressLines = computed(() => {
  const c = this.customer();
  if (!c) return [];
  // ... build the array of address lines
});
```

This is the right home for view-derived data. The alternative —
formatting in the template, or recomputing in `ngOnInit` and storing
in a second `signal` — either spreads logic across `.html` and `.ts`
or duplicates the source-of-truth. `computed` keeps the formatting
beside the source signal and updates automatically when the source
changes.

References:

- [Angular — `computed()`](https://angular.dev/guide/signals#computed-signals)
- [Angular — Signals overview](https://angular.dev/guide/signals)

### `forkJoin` with one observable, and why we still use it

`forkJoin` shines when you have two or more observables to coordinate.
Here we technically only have one — the sales-rep lookup — because
the customer fetch happens first and we *need* its `salesRepEmployeeNumber`
before we know what to fetch. So why wrap a single observable in
`forkJoin`?

Two reasons. First, **shape consistency** — the rest of the app
already uses `forkJoin({ ... }).subscribe(({ a, b }) => ...)` for
multi-fetch coordination. Keeping the same shape here means tomorrow
when we add a third call (say, recent orders) we just add another
key to the object and another destructure on the subscriber. Second,
**explicit batching** — `forkJoin` only emits once *all* its inputs
have completed, which guarantees the page paints in one go rather
than in two flashes ("customer appears, half a second later sales rep
fills in").

That said, with a single observable, a plain `.subscribe()` would
also work. The choice here is stylistic, optimised for the next
change rather than this one.

Reference: [RxJS forkJoin](https://rxjs.dev/api/index/function/forkJoin)

### The three-state render pattern for FK resolution

Whenever you resolve a foreign key, you're committing to handling
three distinct outcomes in the UI. Forgetting any of them produces
either a confusing render or a blank page:

| State | What happened | What to render |
|---|---|---|
| 1. Not assigned | The FK column is null on the parent record | "Unassigned" or similar empty-state label |
| 2. Lookup failed | FK is set, but the target record is gone or the call errored | The raw id with a "couldn't resolve" hint |
| 3. Resolved | FK is set and the target record came back | The target's display name + a link |

In code, those map cleanly to checking the FK on the parent
(`!c.salesRepEmployeeNumber` — state 1), then checking the lookup
result (`salesRep() === null` — state 2, `salesRep() === Employee`
— state 3). If you only handle state 3, your page silently breaks the
moment a sales rep is deleted.

The visual treatment for each state should be different — a muted
"Unassigned" tag, a yellow-ish "couldn't resolve" warning, a real
clickable link — so an admin scanning a list can spot bad data
without reading every row.

### `catchError(() => of(null))` for graceful degradation

The RxJS pattern for "if this errors, substitute a default and don't
blow up the outer stream." Use it whenever a non-critical request can
fail without making the whole page unusable. The `null` we substitute
becomes the trigger for the lookup-failed render branch above.

```ts
const rep$ = customer.salesRepEmployeeNumber
  ? this.employees.get(customer.salesRepEmployeeNumber).pipe(catchError(() => of(null)))
  : of(null);
```

Without `catchError`, a 404 from `GET /employees/1370` would error the
entire `forkJoin` and the whole page would render in the error state
— even though the customer record itself loaded fine.

Reference: [RxJS catchError](https://rxjs.dev/api/operators/catchError)

### `Intl.NumberFormat` for currency

JavaScript ships a built-in formatter for numbers, currencies, dates
and units. It's locale-aware, handles edge cases (negative zero,
infinity, very large numbers), and produces output that matches the
operating system's expectations.

```ts
new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
}).format(150000);
// → "$150,000"
```

The alternative — `'$' + n.toLocaleString()` — works for the happy
path but breaks for non-US locales, doesn't handle the cents
correctly, and gives you no way to suppress fractional digits cleanly.
`Intl.NumberFormat` is the modern correct answer.

Reference: [MDN — Intl.NumberFormat](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/NumberFormat)

### `<dl>`, `<dt>`, `<dd>` for label/value pairs

The semantic HTML for "list of terms and their definitions." A
detail page is fundamentally a list of (label, value) pairs, which
makes `<dl>` exactly the right element rather than a pile of `<div>`s.
Screen readers announce it as a description list; the layout is a CSS
grid concern, not an HTML concern.

```html
<dl class="fields">
  <dt><mat-icon>person</mat-icon> Contact</dt>
  <dd>{{ c.contactFirstName }} {{ c.contactLastName }}</dd>

  <dt><mat-icon>call</mat-icon> Phone</dt>
  <dd>{{ c.phone || '—' }}</dd>
</dl>
```

The accompanying CSS turns it into a two-column grid — labels in the
first column, values in the second — with the label column sized to
the widest term so everything aligns:

```css
.fields {
  display: grid;
  grid-template-columns: minmax(140px, max-content) 1fr;
  column-gap: 1.25rem;
  row-gap: 0.5rem;
}
```

This is the part that makes the page feel "designed" rather than
"thrown together with `<div><strong>X:</strong> Y</div>`."

References:

- [MDN — `<dl>` element](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/dl)
- [CSS-Tricks — Grid `minmax()` and `max-content`](https://css-tricks.com/snippets/css/complete-guide-grid/)

## The code, walked through

### Loading: customer first, sales-rep second

The `ngOnInit` flow is two stages. We can't fetch the sales rep until
we know which employee number to fetch — that's on the customer
record. So we load the customer first, then trigger the rep lookup
once we have it:

```ts
ngOnInit() {
  const raw = this.route.snapshot.paramMap.get('id');
  const id = Number(raw);
  if (raw === null || !Number.isInteger(id)) {
    this.error.set(`Invalid customer id in URL: "${raw}"`);
    return;
  }

  this.loading.set(true);
  this.customers.get(id).subscribe({
    next: customer => {
      this.customer.set(customer);

      const rep$ = customer.salesRepEmployeeNumber
        ? this.employees.get(customer.salesRepEmployeeNumber)
            .pipe(catchError(() => of(null)))
        : of(null);

      forkJoin({ rep: rep$ }).subscribe(({ rep }) => {
        this.salesRep.set(rep);
        this.loading.set(false);
      });
    },
    error: err => {
      this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load customer');
      this.loading.set(false);
    },
  });
}
```

A few things worth noticing:

- The route param is validated up-front. `Number("foo")` is `NaN`,
  which would silently propagate to `?id=NaN` against the backend.
  Catching it here gives a useful error message instead.
- `customer.salesRepEmployeeNumber ? ... : of(null)` short-circuits
  when the FK is null, so we don't bother making a request that would
  immediately 404.
- The `forkJoin` wrapper looks redundant for one observable, but it
  matches the shape we'll want when the next feature adds another
  lookup (e.g. credit-rating service).

### Multi-line address as a `computed()`

The address is formatted as a list of lines, which the template then
renders one per row. Because the formatting depends only on
`this.customer()`, it's a perfect fit for `computed`:

```ts
addressLines = computed(() => {
  const c = this.customer();
  if (!c) return [];
  const lines: string[] = [c.addressLine1];
  if (c.addressLine2) lines.push(c.addressLine2);
  // City, State Postal — comma-separated where parts exist
  const cityLine = [c.city, c.state, c.postalCode]
    .filter(Boolean)
    .join(c.state || c.postalCode ? ', ' : '');
  if (cityLine) lines.push(cityLine);
  if (c.country) lines.push(c.country);
  return lines;
});
```

Three things to note:

- We start with an array containing only the required `addressLine1`,
  then conditionally push the optional bits. This avoids the typical
  `if (x) result += ", " + x` accumulator pattern, which is hard to
  read once you have four optional pieces.
- `filter(Boolean)` drops empty / null parts before joining — that's
  the idiomatic JavaScript way to drop falsy values from an array.
- The template just iterates over `addressLines()` and emits each
  one in its own row. No template-level conditionals.

### The three-state sales-rep render

The most pedagogically interesting piece. All three render branches
live in the template, each producing a distinct visual:

```html
@if (!c.salesRepEmployeeNumber) {
  <span class="muted">Unassigned</span>
} @else if (salesRep(); as rep) {
  <a class="rep-link" [routerLink]="['/employees', rep.employeeNumber]">
    {{ rep.firstName }} {{ rep.lastName }}
  </a>
  <span class="rep-title"> — {{ rep.jobTitle }}</span>
} @else if (salesRep() === null) {
  <span>Employee #{{ c.salesRepEmployeeNumber }}</span>
  <span class="muted">(couldn't resolve)</span>
} @else {
  <span class="muted">Loading…</span>
}
```

Reading top to bottom: no FK → "Unassigned"; FK set and resolved →
clickable rep with title; FK set and lookup returned `null` → raw id
with hint; otherwise (still loading) → spinner-y placeholder.

Note the order matters. The `@else if (salesRep(); as rep)` branch
both checks for "is this an Employee" *and* aliases it as `rep` for
the body. A truthy Employee instance falls through here. The
`null` branch comes after, because `null` is falsy and would skip the
truthy-aliasing block above it.

### Currency formatting through a tiny helper

`formatMoney` exists so the template doesn't have to handle the
null/undefined case alongside the `Intl.NumberFormat` call:

```ts
formatMoney(n: number | null | undefined): string {
  if (n == null) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(n);
}
```

Two design choices: we use a single em-dash (`—`) for "no value"
because it reads more cleanly than "N/A" or "—" or empty, and we
suppress fractional digits because customer credit limits in this
dataset are always whole-dollar amounts. The `n == null` check
catches both `null` and `undefined` thanks to `==` coercion — the
one place loose equality earns its keep.

## How to test

1. Save the modified files; the dev server hot-reloads.
2. Sign in (admin / admin123).
3. Navigate to **Customers** and click any customer row.
4. Verify the new layout:
   - Header shows customer name, customer number, country chip, and
     two quick-action buttons (Edit, Lifetime value).
   - Three sections in order: Contact, Address, Account.
   - Address renders multi-line, with city/state/postal on one line
     and country on its own.
   - Sales rep shows the rep's full name + job title and links to
     their employee detail page.
   - Credit limit displays as `$150,000` (US-formatted, no cents).
5. Test the three FK states:
   - **Unassigned** — find a customer with `salesRepEmployeeNumber`
     null. Should display "Unassigned" in muted text.
   - **Resolved** — most customers. Should display the linked rep.
   - **Lookup failed** — temporarily edit the SQL to set a customer's
     `salesRepEmployeeNumber` to a non-existent id like `9999`, refresh.
     Should display "Employee #9999 (couldn't resolve)". Revert the
     change after testing.
6. Visit `/customers/abc` (invalid id). Should show "Invalid customer
   id in URL: 'abc'" rather than crashing or making a bad backend
   call.

Direct API checks from the terminal:

```bash
curl 'http://localhost:9090/api/v1/customers/103'
# → customer JSON, including salesRepEmployeeNumber
curl 'http://localhost:9090/api/v1/employees/1370'
# → the resolved sales rep
```

## What you just learned

- **`computed()` signals** for derived view state — keeping the
  formatting next to the source data and letting Angular handle
  invalidation.
- **The three-state FK-resolution render pattern** — unassigned,
  resolved, lookup-failed — each visually distinct, with the order
  of `@if` / `@else if` branches mattering because of truthy-alias
  pattern matching.
- **`forkJoin` with a single observable** as a deliberate
  consistency choice for code that will grow more lookups later.
- **`catchError(() => of(null))`** as the standard "this is not
  critical, don't break the page" tolerance pattern.
- **`Intl.NumberFormat`** as the correct way to render currency,
  rather than string concatenation.
- **Semantic `<dl>` / `<dt>` / `<dd>`** for label/value pairs,
  laid out with CSS grid.

## Study materials

### Angular signals

- [Angular — Signals overview](https://angular.dev/guide/signals)
- [Angular — `computed()` signals](https://angular.dev/guide/signals#computed-signals)
- [Angular blog — Signals deep dive](https://blog.angular.dev/angular-signals-now-stable-but-zone-js-still-required-29c84d2f76c7)

### RxJS

- [RxJS forkJoin](https://rxjs.dev/api/index/function/forkJoin)
- [RxJS catchError](https://rxjs.dev/api/operators/catchError)
- [Learn RxJS — catchError patterns](https://www.learnrxjs.io/learn-rxjs/operators/error_handling/catch)

### JavaScript Intl

- [MDN — Intl.NumberFormat](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/NumberFormat)
- [MDN — Intl namespace](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl) — dates, lists, plural rules
- [Smashing Magazine — Internationalization in JavaScript](https://www.smashingmagazine.com/2022/03/internationalization-localization-static-sites/)

### Semantic HTML

- [MDN — `<dl>`, `<dt>`, `<dd>`](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/dl)
- [CSS-Tricks — A complete guide to CSS grid](https://css-tricks.com/snippets/css/complete-guide-grid/)
- [WebAIM — Definition lists for accessibility](https://webaim.org/techniques/semanticstructure/)

### UX patterns for detail pages

- [Nielsen Norman Group — Cards: UI Component Pattern](https://www.nngroup.com/articles/cards-component/)
- [Refactoring UI — Designing for hierarchy](https://www.refactoringui.com/) — short book, the chapter on hierarchy is the one
