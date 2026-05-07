# Feature 07 — Optimistic locking

## What we built

A `version` column on the `employees` table that prevents two
concurrent edits from silently overwriting each other. The repository's
UPDATE checks the version the client read against the version
currently in the database; if they differ, the UPDATE matches no
rows, the repository throws `OptimisticLockingFailureException`, the
controller advice translates that to HTTP 409 Conflict, and the
frontend pops a "Stale data" dialog telling the user to reload.

Files touched:

- `classicmodels-backend/src/main/resources/db/migration/V5__employee_version.sql` (new)
- `classicmodels-backend/src/main/java/.../model/Employee.java`
- `classicmodels-backend/src/main/java/.../dto/employee/EmployeeRequestDTO.java`
- `classicmodels-backend/src/main/java/.../dto/employee/EmployeeResponseDTO.java`
- `classicmodels-backend/src/main/java/.../repository/JdbcEmployeeRepository.java`
- `classicmodels-backend/src/main/java/.../controller/GlobalExceptionHandler.java` (new)
- `classicmodels-ui/src/app/employees/employee.service.ts`
- `classicmodels-ui/src/app/employees/employee-form.component.ts`

## Why this is worth learning

The **lost-update problem** is one of the oldest hazards in
multi-user data systems, and you'll meet it on every collaborative
app you ever build. **Optimistic locking** is the standard mitigation
— small footprint, simple semantics, defends well against the common
case (concurrent edits are rare) without paying the synchronisation
cost of pessimistic locking. **`@RestControllerAdvice` /
`@ExceptionHandler`** is Spring's mechanism for translating thrown
exceptions into HTTP responses without scattering try/catch through
controllers.

## Background

### The lost-update problem

```
Time   Alice                         Bob
─────  ───────────────────────────   ───────────────────────────
t1     Reads row X { email: A }
t2                                   Reads row X { email: A }
t3     Saves row X { email: A' }     ── still has A in memory
t4                                   Saves row X { email: A'' }
t5                                   ── A' is silently lost
```

Without locking, Bob's later save overwrites Alice's email change as
if it never happened. There's no warning anywhere — the data quietly
diverges from what either user expected.

This is bad. The two standard solutions:

**Pessimistic locking.** Alice locks the row when she opens it
(`SELECT ... FOR UPDATE`). Bob's read blocks until Alice saves or
times out. Strong correctness, but every long-running edit form
holds a lock that blocks other users. Doesn't scale to web UIs
where users have edit forms open for minutes.

**Optimistic locking.** Alice and Bob both freely read the row.
Each save checks "is the row still in the state I read?" If yes,
proceed. If no, fail and let the user reload. No locks held
between requests; the "optimism" is the bet that conflicts are
rare enough that occasional retries are cheaper than continuous
blocking.

Web apps almost always use optimistic. Reservation systems and
banking sometimes use pessimistic. JPA, Hibernate, and Spring Data
all default to optimistic and call it `@Version`.

References:

- [Wikipedia — Optimistic concurrency control](https://en.wikipedia.org/wiki/Optimistic_concurrency_control)
- [Wikipedia — Lost update problem](https://en.wikipedia.org/wiki/Lost_update_problem)
- [Martin Fowler — Patterns of Enterprise Application Architecture: "Optimistic Offline Lock"](https://martinfowler.com/eaaCatalog/optimisticOfflineLock.html)

### How a `version` column does it

Add an integer `version` to every row. Convention: starts at 0,
increments on every UPDATE.

The UPDATE statement pattern:

```sql
UPDATE employees
   SET firstName = ?, lastName = ?, ...,
       version = version + 1
 WHERE employeeNumber = ?
   AND version = ?              ← the version the client read
```

The trick is the second predicate. If the row's version is still
what the client expected, the UPDATE matches one row and succeeds.
If someone else updated in the meantime — bumping the version —
the predicate fails to match and the UPDATE affects zero rows.

`executeUpdate()` returns the count. We branch on it:

```java
int affected = ps.executeUpdate();
if (affected == 0) {
    throw new OptimisticLockingFailureException(...);
}
```

A small subtlety: `affected == 0` could also mean "row doesn't exist"
(the user is editing a deleted employee). To give a precise message
we check existence first and pick the right exception type. That's
what Spring Data JPA does internally too.

References:

- [Vlad Mihalcea — Optimistic locking with Hibernate](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/) — narrative explanation
- [Spring docs — `@Version` (JPA)](https://docs.spring.io/spring-data/jpa/reference/jpa/auditing.html#jpa.entity-persistence.locking)

### `@RestControllerAdvice` and `@ExceptionHandler`

A class annotated `@RestControllerAdvice` is essentially a "exceptions
that escape any controller flow through here first" interceptor. Each
method inside annotated `@ExceptionHandler(SomeException.class)`
catches that type and turns it into a `ResponseEntity` (or returns the
exception's body directly).

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(...);
    }
}
```

Without this, the exception would propagate up Spring's filter chain
and get handled by Spring's default error machinery — usually
producing a generic 500 response with a stack trace in the body. The
advice gives us a controlled, shaped 409 response that the frontend
can recognise and react to.

The body shape mimics RFC 9457 (Problem Details for HTTP APIs)
loosely: `type`, `title`, `status`, `detail`. Not a hard requirement
— anything well-defined works — but RFC 9457 is the closest thing to
a standard for "shaped error responses."

References:

- [Spring docs — `@RestControllerAdvice`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)
- [Baeldung — Spring REST exception handling](https://www.baeldung.com/exception-handling-for-rest-with-spring)
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)

### HTTP 409 Conflict

The standard status code for "your request couldn't be applied
because the resource is in a state that conflicts with what you
expected." Used for:

- Optimistic-lock conflicts (this feature)
- Trying to create a resource that already exists
- Trying to delete a resource that has dependencies (we use it in
  the deletion-planner UX)

Distinct from:

- **400 Bad Request** — the request itself is malformed.
- **422 Unprocessable Entity** — the request is well-formed but
  semantically invalid (validation failure).
- **412 Precondition Failed** — used with conditional headers
  (If-Match, If-Unmodified-Since) for resource-versioning at the
  HTTP layer rather than the application layer.

Reference: [MDN — HTTP 409 Conflict](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/409)

## The code, walked through

### Repository — version-aware UPDATE

```java
String sql = """
    UPDATE employees SET
        lastName = ?, ..., updatedBy = ?,
        version = version + 1
    WHERE employeeNumber = ?
      AND version = ?
    """;

int affected = ps.executeUpdate();
if (affected == 0) {
    boolean exists = findById(e.getEmployeeNumber()).isPresent();
    if (!exists) {
        throw new RuntimeException("Employee " + ... + " not found");
    }
    throw new OptimisticLockingFailureException(
            "Employee " + ... + " was modified by someone else "
            + "(stale version " + e.getVersion() + "). Reload and try again.");
}
```

The version check is in the WHERE; the bump is in the SET. Both run
atomically — same UPDATE statement, same transaction. There's no
race window where two concurrent transactions could both see "v=3"
and both increment to "v=4." MySQL's row-level locking serialises
the two writes; one wins, the other fails the WHERE.

### GlobalExceptionHandler — translate to 409

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(...) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "type", "optimistic-lock-failure",
                "title", "Stale data",
                "status", 409,
                "detail", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
```

One annotation on the class, one method per exception type, no
controller code touched. As the app grows, more handlers get added
here — not-found, validation, etc.

### Form — version flows through invisibly

```ts
form = this.fb.group({
  ...,
  version: [null as number | null],   // hidden field
});
```

The form has a `version` control, but no `<input>` for it in the
template — the user never sees it. When the API response patches
the form via `patchValue(employee)`, the version control is set to
whatever the server returned. On save, `form.value` includes that
version, so the request DTO carries it back.

### Save error handler — pop a dialog on 409

```ts
error: err => {
  this.saving.set(false);
  if (err?.status === 409) {
    const detail = err?.error?.detail ?? 'This employee was modified by someone else...';
    this.dialog.open(ErrorDialogComponent, {
      data: {
        title: 'Stale data',
        message: detail + '\n\nReload the page to see the latest version.',
      },
      ...
    });
    return;
  }
  this.error.set(err?.error?.message ?? err?.message ?? 'Save failed');
},
```

Two-tier UX: most errors stay as the inline banner (fine for "you
forgot a field"); the 409 lock failure gets a modal because the user
must consciously react to it.

## How to test

Open the same employee in two browser windows side-by-side
(easiest: regular window + an incognito window so they have separate
sessions). In window A, change the email; click Save. The save
succeeds, A redirects back to the list.

In window B (still showing the older version), change the phone
extension; click Save. The Save spinner appears briefly, then a
**Stale data** dialog pops up:

```
Stale data
Employee 1370 was modified by someone else
(stale version 0). Reload and try again.

Reload the page to see the latest version.

[OK]
```

Click OK, navigate back to the employee, and you'll see the email
change from window A. The phone change from window B was correctly
rejected — no silent overwrite.

You can also reproduce with curl alone. First, fetch:

```bash
curl http://localhost:9090/api/v1/employees/1370 -H "Authorization: Bearer $TOKEN"
# Note the version field, say it's 0.
```

Then PUT twice in quick succession:

```bash
# First call — succeeds, version becomes 1
curl -X PUT http://localhost:9090/api/v1/employees/1370 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"lastName":"Hernandez","firstName":"Gerard",...,"version":0}'

# Second call with the SAME old version — fails with 409
curl -X PUT http://localhost:9090/api/v1/employees/1370 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"lastName":"Hernandez","firstName":"Gerard",...,"version":0}'
# {"detail":"Employee 1370 was modified by someone else (stale version 0)..."}
```

## What you just learned

- **The lost-update problem** and why optimistic locking is the
  standard web-app solution.
- **The version-column pattern**: integer column, atomic
  check-and-bump in the same UPDATE.
- **Distinguishing "row not found" from "version mismatch"** — same
  symptom (zero rows affected), different exception types so the
  user gets a useful error message.
- **`@RestControllerAdvice`** as the canonical Spring way to centralise
  exception → HTTP-response translation.
- **HTTP 409 Conflict** as the right status code for state-conflict
  failures.

## Study materials

### Concurrency theory

- [Wikipedia — Optimistic concurrency control](https://en.wikipedia.org/wiki/Optimistic_concurrency_control)
- [Wikipedia — Lost update problem](https://en.wikipedia.org/wiki/Lost_update_problem)
- [Martin Fowler — Optimistic Offline Lock pattern](https://martinfowler.com/eaaCatalog/optimisticOfflineLock.html)

### Spring concurrency primitives

- [Spring docs — `@Version` in JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/auditing.html#jpa.entity-persistence.locking)
- [Vlad Mihalcea — Optimistic locking](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/) — Hibernate-flavoured but conceptually identical
- [Baeldung — Spring Data optimistic locking](https://www.baeldung.com/jpa-optimistic-locking)

### Spring exception handling

- [Spring docs — `@ControllerAdvice`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)
- [Baeldung — REST exception handling](https://www.baeldung.com/exception-handling-for-rest-with-spring)
- [Baeldung — `@ResponseStatus` annotation](https://www.baeldung.com/spring-response-status) — alternative pattern for shaping responses
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html) — the standard for error response bodies
