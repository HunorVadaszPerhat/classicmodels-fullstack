# Feature 02 — Office detail page with team + map

## What we built

The "View office details →" link in the employee map popup used to lead
to a stub page that just dumped the office as JSON. We rebuilt it as a
proper detail page with three sections: the office's address and phone,
an interactive map pinned at the office, and a list of every active
employee assigned to that office (each clickable to their own detail
page).

To make the team list possible, the backend's `GET /employees` endpoint
gained an optional `?officeCode=` filter — same list endpoint, narrowed
when a specific office is named.

Files touched:

- `classicmodels-backend/src/main/java/.../repository/EmployeeRepository.java`
- `classicmodels-backend/src/main/java/.../repository/JdbcEmployeeRepository.java`
- `classicmodels-backend/src/main/java/.../service/EmployeeService.java`
- `classicmodels-backend/src/main/java/.../controller/EmployeeController.java`
- `classicmodels-ui/src/app/offices/office-detail.component.ts`
- `classicmodels-ui/src/app/offices/office-detail.component.html`

## Why this is worth learning

Three concepts come together. **Optional query parameters** are the
REST-idiomatic way to narrow a collection endpoint without adding a new
route. **Parallel HTTP calls with `forkJoin`** is the right way to
coordinate "I need both A and B before rendering" — it's twice as fast
as awaiting them in sequence. **Repository → service → controller
layering** is the classic three-tier pattern Spring applications follow,
and adding one feature touches all three layers in a coordinated way.

## Background

### Optional query parameters in Spring

`@RequestParam` binds a URL query parameter to a controller-method
argument. Setting `required = false` makes Spring tolerate the
parameter being absent — your method receives `null` instead of an
HTTP 400. You then branch on it:

```java
@GetMapping
public ResponseEntity<List<EmployeeResponseDTO>> findAll(
        @RequestParam(name = "officeCode", required = false) String officeCode) {
    if (officeCode != null && !officeCode.isBlank()) {
        return ResponseEntity.ok(service.findByOffice(officeCode));
    }
    return ResponseEntity.ok(service.findAll());
}
```

The `?` syntax in URLs (`?officeCode=4`) is the standard way to pass
filter / pagination parameters. As more filters get added later you
can just append them: `?officeCode=4&active=true&sort=lastName`.

References:

- [Spring docs — `@RequestParam`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html)
- [MDN — URL query strings](https://developer.mozilla.org/en-US/docs/Learn/Common_questions/What_is_a_URL#parameters)

### `HttpParams` in Angular

The TypeScript counterpart. `HttpParams` is an immutable builder for
URL-encoded query strings; you `.set(...)` keys onto it and pass the
result as the `params` option to `HttpClient`.

```ts
this.http.get<Employee[]>(this.baseUrl, {
  params: new HttpParams().set('officeCode', code),
});
```

The output is `?officeCode=4` appended to the URL automatically, with
proper percent-encoding for any special characters in the value.

Reference: [Angular HttpParams](https://angular.dev/api/common/http/HttpParams)

### `forkJoin` — parallel observables

When you need multiple HTTP calls and don't care about ordering between
them, `forkJoin` is RxJS's "wait for everything" combinator. It takes
an object whose values are observables and emits one combined object
when every input has completed.

```ts
forkJoin({
  office: this.officeService.get(code),
  team:   this.employeeService.list(filterParams),
}).subscribe(({ office, team }) => {
  this.office.set(office);
  this.team.set(team);
});
```

Both requests fire immediately. The subscriber runs once, with both
results, when the slower of the two finishes. Twice as fast as
`subscribe-then-subscribe-inside`.

Important caveat: `forkJoin` only emits if *every* observable completes.
If one of them errors, the whole thing errors. We work around this
selectively with `catchError(() => of(...))` to provide a default value
for non-critical requests — see the team fetch in our component.

References:

- [RxJS forkJoin](https://rxjs.dev/api/index/function/forkJoin)
- [Learn RxJS — forkJoin](https://www.learnrxjs.io/learn-rxjs/operators/combination/forkjoin) — narrative explanation with examples

### The repository → service → controller pattern

A staple of Spring applications. Each layer has a single responsibility:

```
HTTP request
    │
    ▼
Controller        ← parses URL/body, returns HTTP response, no business logic
    │
    ▼
Service           ← business logic, transactions, caching, calls repositories
    │
    ▼
Repository        ← raw data access (JDBC / JPA / etc.), no business logic
    │
    ▼
Database
```

Adding a feature like "filter by office" touches all three layers in
the same coordinated way:

1. Repository gains a query method: `findByOfficeCode(String)`.
2. Service exposes it with whatever business rules apply (caching,
   filtering inactive rows, etc.): `findByOffice(String)`.
3. Controller maps an HTTP route to it: `?officeCode=X` → service call.

Each layer remains independently testable, and changes at one layer
(e.g. switching from JDBC to JPA) only affect that layer. This is
sometimes called "thin controllers, fat services" — controllers should
be small and dumb; services should be where the work happens.

Reference: [Baeldung — Spring layered architecture](https://www.baeldung.com/spring-component-repository-service)

## The code, walked through

### Backend — three small additions in three files

The interface gets a new method declaration:

```java
List<Employee> findByOfficeCode(String officeCode);
```

The implementation runs a parameterised query — same shape as
`findAll`, plus one extra WHERE predicate:

```java
String sql = "SELECT * FROM employees WHERE active = 1 AND officeCode = ?";
ps.setString(1, officeCode);
```

The service exposes the new method to the rest of the app:

```java
public List<EmployeeResponseDTO> findByOffice(String officeCode) {
    return repo.findByOfficeCode(officeCode).stream()
            .map(mapper::toResponseDTO)
            .toList();
}
```

The controller's `findAll` gains an optional parameter:

```java
public ResponseEntity<List<EmployeeResponseDTO>> findAll(
        @RequestParam(name = "officeCode", required = false) String officeCode) {
    if (officeCode != null && !officeCode.isBlank()) {
        return ResponseEntity.ok(service.findByOffice(officeCode));
    }
    return ResponseEntity.ok(service.findAll());
}
```

That's the entire backend story for this feature.

### Frontend — parallel fetch, then render

The detail component fires both HTTP calls in parallel:

```ts
const officeRequest = this.officeService.get(code);
const teamRequest = this.employeeService
  .list(new HttpParams().set('officeCode', code))
  .pipe(catchError(() => of([] as Employee[])));

forkJoin({ office: officeRequest, team: teamRequest }).subscribe({
  next: ({ office, team }) => {
    this.office.set(office);
    this.team.set(team);
    this.loading.set(false);
  },
  ...
});
```

`catchError(() => of([]))` makes the team fetch tolerant — if the
employees endpoint fails for any reason, the office card still renders
with an empty team. This is a deliberate "degrade gracefully" choice;
we'd rather show partial data than a blank page.

The template renders the team as a simple list using Angular's
control-flow syntax:

```html
@if (team(); as members) {
  @if (members.length === 0) {
    <p class="team-empty">No active employees assigned to this office.</p>
  } @else {
    @for (m of members; track m.employeeNumber) {
      <div class="team-row">
        <a [routerLink]="['/employees', m.employeeNumber]">
          {{ m.firstName }} {{ m.lastName }}
        </a>
        <span class="team-title">— {{ m.jobTitle }}</span>
      </div>
    }
  }
}
```

The `@if (team(); as members) { ... }` syntax both reads the signal
and aliases it as `members` inside the block — saves you typing
`team()` six times.

## How to test

1. Restart the backend so the controller picks up the new query parameter.
2. Save the frontend files; the dev server hot-reloads.
3. Sign in (admin / admin123 if Stage 2 of Spring Security is active).
4. Navigate to **Employees**, open any employee, click the office map marker.
5. Click "View office details →" in the popup.
6. The office detail page should now show:
   - Address card
   - Map with a marker (popup says "N employees here")
   - "Team" section listing every active employee at this office,
     each linking to their own detail page

Direct API check from the terminal:

```bash
curl 'http://localhost:9090/api/v1/employees?officeCode=6'
```

Should return only the Sydney employees. Unfiltered:

```bash
curl 'http://localhost:9090/api/v1/employees'
```

Returns all active employees.

## What you just learned

- **Optional query parameters** in Spring with `@RequestParam(required = false)`,
  and the matching client-side `HttpParams` builder.
- **Parallel HTTP calls** with `forkJoin`, plus the `catchError(() => of(default))`
  pattern for tolerating non-critical request failures.
- **The three-tier pattern** of repository → service → controller, and how a
  single feature touches all three in a coordinated way.
- **Angular's control-flow blocks** (`@if`, `@else`, `@for`) — modern
  replacements for `*ngIf`/`*ngFor` directives.

## Study materials

### Spring controllers & layering

- [Spring docs — `@RequestParam`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html)
- [Baeldung — Spring component, repository, service](https://www.baeldung.com/spring-component-repository-service)
- [Baeldung — Spring `@RequestParam`](https://www.baeldung.com/spring-request-param)

### Angular HttpClient + RxJS

- [Angular — HttpClient overview](https://angular.dev/guide/http)
- [Angular — HttpParams API](https://angular.dev/api/common/http/HttpParams)
- [RxJS forkJoin](https://rxjs.dev/api/index/function/forkJoin)
- [Learn RxJS — forkJoin](https://www.learnrxjs.io/learn-rxjs/operators/combination/forkjoin) — examples, gotchas
- [Learn RxJS — catchError](https://www.learnrxjs.io/learn-rxjs/operators/error_handling/catch) — graceful failure handling

### Angular control-flow blocks

- [Angular — Control flow](https://angular.dev/guide/templates/control-flow) — the new `@if`, `@for`, `@switch` syntax
- [Angular blog — Built-in control flow announcement](https://blog.angular.io/meet-angulars-new-control-flow-a02c6eee7843) — the rationale for replacing structural directives

### URL design

- [REST API Design Rulebook (Mark Massé)](https://www.oreilly.com/library/view/rest-api-design/9781449317904/) — short book on REST URL conventions
- [Microsoft — REST API design guidelines](https://github.com/microsoft/api-guidelines/blob/vNext/Guidelines.md) — pragmatic, opinionated, freely available
