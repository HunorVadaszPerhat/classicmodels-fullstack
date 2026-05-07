# Feature 06 — Server-side pagination, sorting, filtering

## What we built

The employee list page used to fetch *every* employee in a single HTTP
call and paginate / sort / filter in the browser. That works for 23
rows but breaks at 10k. We switched to server-side mode: each request
returns just the rows for the current page plus a total-count, and a
search input filters across name and email — debounced so we don't
hammer the backend on every keystroke.

Files touched:

- `classicmodels-backend/src/main/java/.../repository/EmployeeRepository.java`
- `classicmodels-backend/src/main/java/.../repository/JdbcEmployeeRepository.java`
- `classicmodels-backend/src/main/java/.../service/EmployeeService.java`
- `classicmodels-backend/src/main/java/.../controller/EmployeeController.java`
- `classicmodels-ui/src/app/employees/employee.service.ts`
- `classicmodels-ui/src/app/employees/employee-list.component.ts`
- `classicmodels-ui/src/app/employees/employee-list.component.html`

## Why this is worth learning

Three concepts at once. **Dynamic SQL** — building a query whose shape
varies based on the parameters. **The Page envelope** — the standard
"here's a slice + the total" response shape, used by Spring Data JPA,
Spring REST Docs, basically every paged API in the Java ecosystem.
**Debounced input with RxJS** — the canonical "search as you type"
pattern, applicable to any reactive UI.

## Background

### The "fetch only the current page" mental model

```
Backend                                Frontend
─────────                              ─────────
SELECT * FROM employees                ┌─ Receive rows ─┐
WHERE active=1                          │ for page N    │
  AND lastName LIKE '%foo%'             │ + total count │
ORDER BY lastName ASC                   └────┬───────────┘
LIMIT 10 OFFSET 20                          │
                                            ▼
                                       Render in <table>
                                       Set paginator length
                                       Set sort indicators
```

Five client-side bits of state drive each request:

| State          | What it controls           | Default |
|----------------|----------------------------|---------|
| `pageIndex`    | LIMIT/OFFSET position      | 0       |
| `pageSize`     | LIMIT                      | 10      |
| `sortField`    | ORDER BY column            | lastName|
| `sortDir`      | ORDER BY direction         | asc     |
| `searchText`   | LIKE filter                | empty   |

Whenever any of them changes, fire a fresh request. Replace the
table's `dataSource` with the new content, update the paginator's
`length` with the new total.

### Dynamic SQL and JDBC's parameter-binding rules

A query whose WHERE clause changes shape per request needs care, because
JDBC's `PreparedStatement` is positional — the `?` placeholders are
indexed left-to-right. If we conditionally append a clause, we must
also conditionally append the binding in the same order.

**Pattern**: collect the bindings in a `List<Object>` as you build
the SQL:

```java
StringBuilder where = new StringBuilder("WHERE active = 1");
List<Object> params = new ArrayList<>();

if (search != null && !search.isBlank()) {
    where.append(" AND (lastName LIKE ? OR firstName LIKE ? OR email LIKE ?)");
    String like = "%" + search.trim() + "%";
    params.add(like);
    params.add(like);
    params.add(like);
}

String sql = "SELECT * FROM employees " + where + " ORDER BY ... LIMIT ? OFFSET ?";

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
- **SetObject is positional.** The parameter index counter (`idx`)
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
| `search` (free text) | `ps.setString(...)` | Bound parameter; MySQL escapes it. |
| `sortBy` (column name) | Allow-list switch | Column names CAN'T be parameterised. |
| `asc` (boolean) | `"ASC"` / `"DESC"` literal | No injection surface. |

The trap: bind parameters work for *values* (right-hand side of a
comparison), not *identifiers* (column names, table names, keywords
like ASC). For sort-by column you can't write `ORDER BY ?`; you have
to inject the column name into the SQL literal. The mitigation is
the allow-list switch:

```java
String sortColumn = switch (sortBy) {
    case "lastName", "firstName", "jobTitle", "officeCode" -> sortBy;
    default -> "lastName";
};
```

A user passing `?sort=DROP TABLE employees;--` falls through to the
default. You're safe by construction.

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
  "totalElements": 87,
  "totalPages": 9
}
```

Spring Data uses this exact shape (with extra fields like
`numberOfElements`, `first`, `last`, `pageable`). We strip it down to
the essentials. The contract is:

- `content` — actual rows for THIS page.
- `totalElements` — count across ALL pages, used by the paginator
  ("showing 1–10 of 87").
- `totalPages` — convenience field, derived from totalElements/size.

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
  (page)="onPageChange($event)">
</mat-paginator>
```

The `[length]` is what lets the paginator render "1–10 of 87". When
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
- [Angular Material — MatTable with server-side pagination](https://material.angular.io/components/table/examples) — search the page for "Pagination"

### Debounced search with RxJS Subjects

Without debouncing, every keystroke fires a request. A user typing
"hernandez" triggers nine HTTP calls in fast succession; the backend
processes most of them only to throw the result away when the next
one arrives.

**Pattern**: pipe input changes through a `Subject` with `debounceTime`:

```ts
private readonly search$ = new Subject<string>();

ngOnInit() {
  this.search$.pipe(
    debounceTime(300),
    distinctUntilChanged(),
  ).subscribe(value => {
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
and you get one request, not nine.

`distinctUntilChanged()` skips emissions where the value matches the
last one — protects against, e.g., the user pressing Shift (which
fires an input event without changing the text).

300ms is a good middle ground:

- **<200ms** feels snappy but starts firing while users are still
  typing.
- **>500ms** feels laggy.
- **300ms** is what most search-as-you-type implementations use.

References:

- [Learn RxJS — debounceTime](https://www.learnrxjs.io/learn-rxjs/operators/filtering/debouncetime)
- [Learn RxJS — distinctUntilChanged](https://www.learnrxjs.io/learn-rxjs/operators/filtering/distinctuntilchanged)
- [Angular blog — Building search with RxJS](https://blog.angular.io/) — countless examples

## The code, walked through

### Repository — dynamic WHERE + parameter list

```java
StringBuilder where = new StringBuilder("WHERE active = 1");
List<Object> params = new ArrayList<>();
if (search != null && !search.isBlank()) {
    where.append(" AND (lastName LIKE ? OR firstName LIKE ? OR email LIKE ?)");
    String like = "%" + search.trim() + "%";
    params.add(like); params.add(like); params.add(like);
}

String sql = ("SELECT * FROM employees %s ORDER BY %s %s LIMIT ? OFFSET ?")
        .formatted(where, sortColumn, order);
```

The WHERE clause grows by zero or three placeholders depending on the
search term. The bindings in `params` are added in lockstep so they
match positionally. The ORDER BY column is injected as a literal
(allow-list filtered), and LIMIT/OFFSET come last.

### Service — caching the page response

```java
@Cacheable(cacheNames = "employeesPaged", key = "{#page, #size, #sortBy, #asc, #search}")
public PageResponse<EmployeeResponseDTO> findAllPaged(
        int page, int size, String sortBy, boolean asc, String search) {
    ...
}
```

The cache key includes all five inputs, so different (page, search)
combinations are cached separately. Same query within the cache window
hits the cache; first call hits the DB. Cache evicts on any write to
employees (the `@CacheEvict` on save/update/delete already covers this).

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

The reset to `pageIndex = 0` matters: searching for "hernandez" while
on page 5 should land you on page 1 of the new result set, not page 5
(which might not exist). Same logic for sort changes.

## How to test

1. Restart the backend.
2. Make sure you're logged in.
3. Navigate to **Employees**. You should see a paginator at the bottom
   showing "1 – 10 of 23" (or however many active employees you have).
4. Click **Next** → page 2 loads. Backend logs confirm it's a fresh
   query with `OFFSET 10`.
5. Click the **Last Name** header → table re-fetches sorted by
   lastName ASC. Click again → DESC. Once more → resets.
6. Type "her" in the search box. After ~300ms the table refreshes
   showing only Hernandez (and any other "her" matches).
7. Clear the search → all rows return.

You can confirm this is genuinely server-side via the network tab:
every keystroke (after debounce) is a fresh request to
`/api/v1/employees/find-all-paged?...` with the appropriate query
parameters.

## What you just learned

- **The Page-envelope contract** (`content`, `totalElements`, …) and
  how it lets the paginator render correctly.
- **Dynamic SQL** with parameter binding — building a WHERE clause
  whose shape varies and keeping bindings in sync.
- **Three different SQL-injection mitigation patterns** for values,
  identifiers, and booleans.
- **`MatPaginator` / `MatSort` in server-side mode** — drive your own
  data fetch from their events.
- **Debounced search with RxJS Subjects + `debounceTime` +
  `distinctUntilChanged`** — the canonical reactive pattern for
  "search as you type."

## Study materials

### Backend pagination

- [Spring Data — Paging and sorting](https://docs.spring.io/spring-data/commons/docs/current/reference/html/#repositories.special-parameters) — even though we don't use Spring Data, the contract is the reference
- [PostgreSQL — LIMIT/OFFSET pitfalls](https://use-the-index-luke.com/no-offset) — why deep pagination is slow
  and what cursor-based pagination is
- [Markus Winand — SQL Indexing and Tuning](https://use-the-index-luke.com/) — the indispensable guide

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
- [Learn RxJS — switchMap](https://www.learnrxjs.io/learn-rxjs/operators/transformation/switchmap) — useful when chaining the search Subject directly into the HTTP call (alternative to imperative load())

### SQL injection prevention

- [OWASP — SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
- [Bobby Tables](https://bobby-tables.com/) — the joke that the lesson never gets old
