# Feature C2 — Server-side pagination, sorting, search

## What we built

The customer list page used to fetch *every* customer in a single HTTP
call and paginate / sort / filter in the browser. That works for 122
rows but breaks at 10k. We switched to server-side mode: each request
returns just the rows for the current page plus a total-count, and a
search input filters across customer name and contact name — debounced
so we don't hammer the backend on every keystroke.

This is the same shape as the employee-list rewrite from Feature 6,
applied to a different entity. The customer-specific bits are which
columns are sortable (customerName, contactLastName, city, country,
creditLimit) and which are searched (customerName, contactLastName,
contactFirstName).

The backend already had a `GET /customers/find-all-paged` endpoint
with page/size/sort/dir, but no `search` parameter and no
`@CacheEvict` of `customersPaged` on writes (a latent bug — mutations
left stale pages in the cache). Both are fixed here.

Files touched:

- `classicmodels-backend/src/main/java/.../repository/CustomerRepository.java`
- `classicmodels-backend/src/main/java/.../service/CustomerService.java`
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java`
- `classicmodels-ui/src/app/customers/customer.service.ts`
- `classicmodels-ui/src/app/customers/customer-list.component.ts`
- `classicmodels-ui/src/app/customers/customer-list.component.html`

## Why this is worth learning

Three concepts at once. **Dynamic SQL** — building a query whose shape
varies based on the parameters. **The Page envelope** — the standard
"here's a slice + the total" response shape, used by Spring Data, JPA,
basically every paged API in the Java ecosystem. **Debounced input
with RxJS** — the canonical "search as you type" pattern, applicable
to any reactive UI.

This is your second pass through these patterns (employees got them
first). The point is to feel them sink in: the second time round you
should be reaching for the same building blocks (`HttpParams`,
`Subject` + `debounceTime`, allow-list switch on `sortBy`) without
having to think about them.

## Background

### The "fetch only the current page" mental model

```
Backend                                Frontend
─────────                              ─────────
SELECT * FROM customers                ┌─ Receive rows ─┐
WHERE customerName LIKE '%foo%'        │ for page N    │
   OR contactLastName LIKE '%foo%'     │ + total count │
ORDER BY customerName ASC              └────┬───────────┘
LIMIT 10 OFFSET 20                          │
                                            ▼
                                       Render in <table>
                                       Set paginator length
                                       Set sort indicators
```

Five client-side bits of state drive each request:

| State          | What it controls           | Default       |
|----------------|----------------------------|---------------|
| `pageIndex`    | LIMIT/OFFSET position      | 0             |
| `pageSize`     | LIMIT                      | 10            |
| `sortField`    | ORDER BY column            | customerName  |
| `sortDir`      | ORDER BY direction         | asc           |
| `searchText`   | LIKE filter                | empty         |

Whenever any of them changes, fire a fresh request. Replace the
table's `dataSource` with the new content, update the paginator's
`length` with the new total.

### Customers don't (yet) have a soft-delete column

Worth flagging because the employee version's WHERE clause starts
with `WHERE active = 1` and conditionally appends an AND. Customers
don't have an `active` column yet — soft delete is C6. So our WHERE
clause is *empty* by default and only appears when a search term is
present:

```java
StringBuilder where = new StringBuilder();
if (search != null && !search.isBlank()) {
    where.append("WHERE (customerName LIKE ? OR contactLastName LIKE ? OR contactFirstName LIKE ?)");
    ...
}
```

The shape adapts when C6 lands and we add `WHERE active = 1` as the
base predicate. For now, there's nothing to filter against by default.

### Dynamic SQL and JDBC's parameter-binding rules

A query whose WHERE clause changes shape per request needs care, because
JDBC's `PreparedStatement` is positional — the `?` placeholders are
indexed left-to-right. If we conditionally append a clause, we must
also conditionally append the binding in the same order.

**Pattern**: collect the bindings in a `List<Object>` as you build
the SQL:

```java
StringBuilder where = new StringBuilder();
List<Object> params = new ArrayList<>();

if (search != null && !search.isBlank()) {
    where.append("WHERE (customerName LIKE ? OR contactLastName LIKE ? OR contactFirstName LIKE ?)");
    String like = "%" + search.trim() + "%";
    params.add(like);
    params.add(like);
    params.add(like);
}

String sql = ("SELECT * FROM customers %s ORDER BY %s %s LIMIT ? OFFSET ?")
        .formatted(where, sortColumn, order);

try (PreparedStatement ps = c.prepareStatement(sql)) {
    int idx = 1;
    for (Object p : params) ps.setObject(idx++, p);
    ps.setInt(idx++, size);
    ps.setInt(idx, page * size);
    ...
}
```

Three things to notice:

- **Each `?` corresponds to exactly one binding.** Three `?` for the
  search → three `params.add(...)` calls.
- **`setObject` is positional.** The parameter index counter (`idx`)
  must match the order the placeholders appear in the SQL.
- **LIMIT/OFFSET parameters come AFTER the WHERE parameters** — they
  appear later in the SQL.

For a very dynamic query you might reach for a query builder library
(JOOQ, MyBatis, QueryDSL). For "one optional WHERE clause" the manual
build-up is verbose but obviously correct.

References:

- [Oracle — JDBC PreparedStatement](https://docs.oracle.com/javase/tutorial/jdbc/basics/prepared.html)
- [Baeldung — Dynamic queries with JDBC](https://www.baeldung.com/java-jdbc) — see the WHERE-clause section

### SQL injection — three different patterns

Three pieces of "external data" can flow into the SQL, each with its
own handling:

| Source | Pattern | Why |
|--------|---------|-----|
| `search` (free text) | `ps.setObject(...)` | Bound parameter; MySQL escapes it. |
| `sortBy` (column name) | Allow-list switch | Column names CAN'T be parameterised. |
| `asc` (boolean) | `"ASC"` / `"DESC"` literal | No injection surface. |

The trap: bind parameters work for *values* (right-hand side of a
comparison), not *identifiers* (column names, table names, keywords
like ASC). For sort-by column you can't write `ORDER BY ?`; you have
to inject the column name into the SQL literal. The mitigation is
the allow-list switch:

```java
String sortColumn = switch (sortBy) {
    case "customerName", "contactLastName", "city", "country", "creditLimit" -> sortBy;
    default -> "customerName";
};
```

A user passing `?sort=DROP TABLE customers;--` falls through to the
default. You're safe by construction. Whenever you add a sortable
column to the UI, you must also add it to this switch — otherwise
clicking the new column header silently sorts by the default.

References:

- [OWASP — SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
- [Bobby Tables](https://bobby-tables.com/) — the canonical "use parameterised queries" reminder

### The Page envelope

A standard wrapper for paged responses:

```json
{
  "content": [...],
  "page": 0,
  "size": 10,
  "totalElements": 122,
  "totalPages": 13
}
```

Spring Data uses this exact shape (with extra fields like
`numberOfElements`, `first`, `last`, `pageable`). We strip it down to
the essentials. The contract is:

- `content` — actual rows for THIS page.
- `totalElements` — count across ALL pages, used by the paginator
  ("showing 1–10 of 122").
- `totalPages` — convenience field, derived from `totalElements / size`.

The frontend's `MatPaginator` binds its `[length]` input to
`totalElements` and computes the rest itself.

### `MatPaginator` and `MatSort` in server-side mode

Both Material widgets work fine without a `MatTableDataSource` — you
just listen to their events and drive your own data fetch:

```html
<mat-paginator
  [length]="total()"
  [pageIndex]="pageIndex()"
  [pageSize]="pageSize()"
  [pageSizeOptions]="[5, 10, 25, 50]"
  showFirstLastButtons
  (page)="onPageChange($event)">
</mat-paginator>
```

The `[length]` is what lets the paginator render "1–10 of 122". When
the user clicks Next, it emits a `PageEvent` with the new
`pageIndex` and `pageSize`; we update local state and re-fetch.

Same idea for `MatSort`:

```html
<table mat-table
       matSort
       [matSortActive]="sortField()"
       [matSortDirection]="sortDir()"
       (matSortChange)="onSortChange($event)">
```

References:

- [Angular Material — MatPaginator](https://material.angular.io/components/paginator/overview)
- [Angular Material — MatSort](https://material.angular.io/components/sort/overview)

### Debounced search with RxJS Subjects

Without debouncing, every keystroke fires a request. A user typing
"signal" triggers six HTTP calls in fast succession; the backend
processes most of them only to throw the result away when the next
one arrives.

**Pattern**: pipe input changes through a `Subject` with `debounceTime`:

```ts
private readonly search$ = new Subject<string>();

ngOnInit() {
  this.search$.pipe(
    debounceTime(300),
    distinctUntilChanged(),
  ).subscribe(() => {
    this.pageIndex.set(0);
    this.load();
  });
}

onSearchInput(value: string) {
  this.searchText = value;
  this.search$.next(value);
}
```

`debounceTime(300)` means "wait until 300ms have passed without a new
emission, then emit the most recent value." Pause briefly while typing
and you get one request, not six.

`distinctUntilChanged()` skips emissions where the value matches the
last one — protects against, e.g., the user pressing Shift (which
fires an input event without changing the text).

300ms is a good middle ground:

- **<200ms** feels snappy but starts firing while users are still typing.
- **>500ms** feels laggy.
- **300ms** is what most search-as-you-type implementations use.

References:

- [Learn RxJS — debounceTime](https://www.learnrxjs.io/learn-rxjs/operators/filtering/debouncetime)
- [Learn RxJS — distinctUntilChanged](https://www.learnrxjs.io/learn-rxjs/operators/filtering/distinctuntilchanged)

### Why the cache key has to include `search`

Spring's `@Cacheable` keys cache entries by the inputs you tell it to.
Our customer-paged cache used to key on `{page, size, sortBy, asc}`.
Add `search` to the inputs without adding it to the key, and a request
for `?search=signal` would return a *cached page from a different
search* — or worse, the unfiltered page.

```java
@Cacheable(cacheNames = "customersPaged", key = "{#page, #size, #sortBy, #asc, #search}")
```

The cache key is now the full input tuple. Different
(page, search) combinations cache independently. Same query within
the cache window hits the cache; first call hits the DB.

The flip side: writes have to evict the paged cache too. We added
`customersPaged` to the `@CacheEvict(allEntries = true)` list on
`create`, `update`, `delete`, and `createBulk`. Without that, creating
a new customer would leave stale list pages in the cache.

References:

- [Spring docs — Cache abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Baeldung — `@Cacheable` and SpEL keys](https://www.baeldung.com/spring-cache-tutorial)

## The code, walked through

### Repository — dynamic WHERE + parameter list

```java
String order = asc ? "ASC" : "DESC";
String sortColumn = switch (sortBy) {
    case "customerName", "contactLastName", "city", "country", "creditLimit" -> sortBy;
    default -> "customerName";
};

StringBuilder where = new StringBuilder();
List<Object> params = new ArrayList<>();
if (search != null && !search.isBlank()) {
    where.append("WHERE (customerName LIKE ? OR contactLastName LIKE ? OR contactFirstName LIKE ?)");
    String like = "%" + search.trim() + "%";
    params.add(like);
    params.add(like);
    params.add(like);
}

String sql = ("SELECT * FROM customers %s ORDER BY %s %s LIMIT ? OFFSET ?")
        .formatted(where, sortColumn, order);
```

The WHERE clause grows by zero or three placeholders depending on the
search term. The bindings in `params` are added in lockstep so they
match positionally. The ORDER BY column is injected as a literal
(allow-list filtered), and LIMIT/OFFSET come last.

`countAll(search)` uses the same dynamic-WHERE construction so the
total reflects the filter. If they got out of sync — e.g.
`countAll()` returned the unfiltered count while `findAllPaged`
returned filtered rows — the paginator would render "showing 1–10
of 122" while only 8 rows were filtered, then break when the user
tried to go to page 13.

### Service — search as cache key, evictions on writes

```java
@Cacheable(cacheNames = "customersPaged", key = "{#page, #size, #sortBy, #asc, #search}")
public PageResponse<CustomerResponseDTO> findAllPaged(
        int page, int size, String sortBy, boolean asc, String search) {
    var items = repo.findAllPaged(page, size, sortBy, asc, search).stream()
            .map(mapper::toResponseDTO)
            .toList();
    long total = repo.countAll(search);
    int totalPages = (int) Math.ceil((double) total / size);
    return new PageResponse<>(items, page, size, total, totalPages);
}
```

And on every mutation:

```java
@CacheEvict(cacheNames = {"customersAll", "customersPaged"}, allEntries = true)
public CustomerResponseDTO create(CustomerRequestDTO dto) { ... }
```

`allEntries = true` is the simple-but-correct approach — wipe the
whole paged cache rather than try to figure out which keys this
specific create might have invalidated. The paged cache repopulates
on the next read.

### Controller — one new optional `@RequestParam`

```java
public ResponseEntity<PageResponse<CustomerResponseDTO>> findAllPaged(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "customerName") String sort,
        @RequestParam(defaultValue = "asc") String dir,
        @RequestParam(name = "search", required = false) String search) {
    boolean asc = !"desc".equalsIgnoreCase(dir);
    return ResponseEntity.ok(service.findAllPaged(page, size, sort, asc, search));
}
```

`required = false` makes Spring tolerate the parameter being absent —
your method receives `null` instead of an HTTP 400. No client-side
sentinel needed.

### Frontend — Subject-driven debounced search

```ts
this.search$.pipe(
  debounceTime(300),
  distinctUntilChanged(),
).subscribe(() => {
  this.pageIndex.set(0);
  this.load();
});
```

The Subject is the entry point; the operators shape the stream; the
subscribe is the side effect. It's a tiny self-contained reactive
pipeline that runs for the lifetime of the component.

The reset to `pageIndex = 0` matters: searching for "signal" while
on page 5 should land you on page 1 of the new result set, not page 5
(which might not exist). Same logic for sort changes.

### Frontend — service method with `HttpParams`

```ts
listPaged(opts: {
  page: number;
  size: number;
  sort?: string;
  dir?: 'asc' | 'desc';
  search?: string;
}) {
  let params = new HttpParams()
    .set('page', String(opts.page))
    .set('size', String(opts.size));
  if (opts.sort) params = params.set('sort', opts.sort);
  if (opts.dir) params = params.set('dir', opts.dir);
  // Skip empty/whitespace-only search rather than sending ?search=
  if (opts.search && opts.search.trim()) {
    params = params.set('search', opts.search.trim());
  }
  return this.http.get<PageResponse<Customer>>(`${this.baseUrl}/find-all-paged`, { params });
}
```

`HttpParams` is immutable — every `.set()` returns a new instance, so
the reassignment to `params` is required. The conditional `.set()` for
search keeps the URL clean: we send `?search=signal` only when there's
actually a search, never `?search=`.

## How to test

1. Restart the backend (the controller picked up a new query param).
2. Sign in (admin / admin123).
3. Navigate to **Customers**. The paginator at the bottom should show
   "1 – 10 of 122" (or however many customers your seed has).
4. Click **Next** → page 2 loads. Backend logs should show a fresh
   query with `OFFSET 10`.
5. Click the **Name** column header → table re-fetches sorted by
   customerName ASC. Click again → DESC. Once more → resets.
6. Click the **Contact** column → re-fetches sorted by
   contactLastName.
7. Type "signal" in the search box. After ~300ms the table refreshes
   showing only customers whose name or contact contains "signal"
   (probably "Signal Gift Stores", "Signal Collectibles Ltd.").
8. Clear the search → all rows return.

You can confirm this is genuinely server-side via the network tab:
every keystroke (after debounce) is a fresh request to
`/api/v1/customers/find-all-paged?...` with the appropriate query
parameters.

Direct API check from the terminal:

```bash
curl 'http://localhost:9090/api/v1/customers/find-all-paged?page=0&size=5&sort=customerName&dir=asc&search=signal'
```

Should return a `PageResponse<Customer>` with up to 5 matches and the
total count.

## What you just learned

- **The Page-envelope contract** (`content`, `totalElements`, …) and
  how it lets the paginator render correctly.
- **Dynamic SQL** with parameter binding — building a WHERE clause
  whose shape varies and keeping bindings in sync.
- **Three different SQL-injection mitigation patterns** for values,
  identifiers, and booleans.
- **Cache key composition** — keys must include every input that
  changes the result, and writes must evict the relevant caches.
- **`MatPaginator` / `MatSort` in server-side mode** — drive your own
  data fetch from their events.
- **Debounced search with RxJS Subjects + `debounceTime` +
  `distinctUntilChanged`** — the canonical reactive pattern for
  "search as you type."

## Study materials

### Backend pagination

- [Spring Data — Paging and sorting](https://docs.spring.io/spring-data/commons/docs/current/reference/html/#repositories.special-parameters) — even though we don't use Spring Data, the contract is the reference
- [PostgreSQL — LIMIT/OFFSET pitfalls](https://use-the-index-luke.com/no-offset) — why deep pagination is slow and what cursor-based pagination is
- [Markus Winand — SQL Indexing and Tuning](https://use-the-index-luke.com/) — the indispensable guide

### Spring caching

- [Spring docs — Cache abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Baeldung — Spring `@Cacheable` and SpEL](https://www.baeldung.com/spring-cache-tutorial)
- [Spring docs — `@CacheEvict`](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-evict)

### JDBC

- [Oracle — JDBC tutorials](https://docs.oracle.com/javase/tutorial/jdbc/index.html)
- [Baeldung — JDBC tutorials](https://www.baeldung.com/java-jdbc)

### Angular Material tables

- [Angular Material — Table overview](https://material.angular.io/components/table/overview)
- [Angular Material — Table with server-side pagination example](https://material.angular.io/components/table/examples)
- [Angular Material — MatPaginator API](https://material.angular.io/components/paginator/api)
- [Angular Material — MatSort API](https://material.angular.io/components/sort/api)

### RxJS for "search as you type"

- [Learn RxJS — debounceTime](https://www.learnrxjs.io/learn-rxjs/operators/filtering/debouncetime)
- [Learn RxJS — distinctUntilChanged](https://www.learnrxjs.io/learn-rxjs/operators/filtering/distinctuntilchanged)
- [Learn RxJS — switchMap](https://www.learnrxjs.io/learn-rxjs/operators/transformation/switchmap) — useful when chaining the search Subject directly into the HTTP call (alternative to imperative `load()`)

### SQL injection prevention

- [OWASP — SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
- [Bobby Tables](https://bobby-tables.com/) — the joke that the lesson never gets old
