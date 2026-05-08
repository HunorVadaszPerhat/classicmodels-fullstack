# Feature C5 — Optimistic locking on customer update

## What we built

C4 added a `version` column to the customers table but left it
inert. C5 wires it up. The repository's UPDATE now checks the
client's read version against the row's current version; if they
differ, the UPDATE matches no rows, the repository throws
`OptimisticLockingFailureException`, the global exception advice
translates that to HTTP 409 Conflict, and the customer form opens
a "Stale data" dialog telling the user to reload.

This is two SQL bits and one frontend conditional — the smallest
possible C-feature, because C4 did all the prep work.

Files touched:

- `classicmodels-backend/src/main/java/.../repository/CustomerRepository.java`
- `classicmodels-ui/src/app/customers/customer-form.component.ts`

The `GlobalExceptionHandler` already maps
`OptimisticLockingFailureException` → 409 generically, so no advice
changes were needed. C5 inherits the same wiring employees got in F7.

## Why this is worth learning

The **lost-update problem** is one of the oldest hazards in
multi-user data systems, and you'll meet it on every collaborative
app you ever build. **Optimistic locking** is the standard
mitigation — small footprint, simple semantics, defends well
against the common case (concurrent edits are rare) without paying
the synchronisation cost of pessimistic locking.

This is your second pass through the pattern. The point is to feel
the *minimal* shape of it — once C4 has added the column, DTOs, and
hidden form field, the actual concurrency-control behaviour is
literally one SQL change and one branch in an HTTP error handler.
That's the payoff of the C4 / C5 split.

## Background

### The lost-update problem

```
Time   Alice                         Bob
─────  ───────────────────────────   ───────────────────────────
t1     Reads customer 103 v=0
t2                                   Reads customer 103 v=0
t3     Saves new phone number,
       customer 103 now v=1
t4                                   Saves new credit limit
                                      WHERE version=0 → 0 rows
t5                                   ── 409, "stale data"
```

Without locking, Bob's later save would overwrite Alice's phone-
number change as if it never happened. There's no warning anywhere
— the data quietly diverges from what either user expected.

The two standard solutions:

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
- [Martin Fowler — Optimistic Offline Lock](https://martinfowler.com/eaaCatalog/optimisticOfflineLock.html)

### How a `version` column does it

C4 added the column. C5 uses it. The pattern is two SQL changes:

```sql
UPDATE customers
   SET ...,
       version = version + 1            -- (1) bump on success
 WHERE customerNumber = ?
   AND version = ?                       -- (2) match the read version
```

The trick is the second predicate. If the row's version is still
what the client expected, the UPDATE matches one row and succeeds
— and the version increment fires atomically with the rest of the
SET. If someone else updated in the meantime, bumping the version,
the predicate fails to match and the UPDATE affects zero rows.

`executeUpdate()` returns the affected count. We branch on it:

```java
int affected = ps.executeUpdate();
if (affected == 0) {
    throw new OptimisticLockingFailureException(...);
}
```

Atomicity matters: the version bump and the data change are part of
the same UPDATE statement. There's no "between" state where a
concurrent reader could see the data change but not the version
bump (or vice versa). The DB engine guarantees row-level statement
atomicity.

References:

- [Vlad Mihalcea — Optimistic locking with Hibernate](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/)
- [Spring docs — `@Version` (JPA)](https://docs.spring.io/spring-data/jpa/reference/jpa/auditing.html#jpa.entity-persistence.locking)

### Disambiguating "stale" from "missing"

`affected == 0` could mean two things:

1. **Stale version** — the row exists but its version moved on.
2. **Row missing** — the user is editing a customer that has been
   deleted between read and save.

Both are 4xx situations, but they call for different messages. We
disambiguate by checking existence before throwing:

```java
boolean exists = findById(c.getCustomerNumber()).isPresent();
if (!exists) {
    throw new RuntimeException(
            "Customer " + c.getCustomerNumber() + " not found");
}
throw new OptimisticLockingFailureException(
        "Customer " + c.getCustomerNumber() + " was modified by someone else "
        + "(stale version " + c.getVersion() + "). Reload and try again.");
```

This is what Spring Data JPA does internally — the cost of the extra
SELECT only fires on the conflict path, which is supposed to be
rare under the optimism assumption. So it's free 99.9% of the time
and gives a precise error message the 0.1% of the time it isn't.

There's a small race here: between the failed UPDATE and the
existence check, the row could be deleted (giving the wrong error
message) or re-created (also giving the wrong error message). Both
are vanishingly unlikely under realistic load and we accept the
minor imprecision rather than wrap both in a SERIALIZABLE
transaction.

Reference: [Spring docs — `OptimisticLockingFailureException`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/OptimisticLockingFailureException.html)

### Why the `GlobalExceptionHandler` doesn't need changes

The `@RestControllerAdvice` we wrote in F7 already maps
`OptimisticLockingFailureException` → 409 Conflict, and it's
entity-agnostic — it catches the exception type, not "the exception
type when thrown from EmployeeRepository." The customer repository
throwing the same type gets the same HTTP response automatically.

This is the value of `@RestControllerAdvice` over per-controller
try/catch. One handler, all controllers, all entities. Adding a
new entity that needs the same translation costs zero handler code.

Reference: [Spring docs — `@RestControllerAdvice`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)

### HTTP 409 Conflict — when the request is fine but the state isn't

The standard status code for "your request couldn't be applied
because the resource is in a state that conflicts with what you
expected." Used for:

- Optimistic-lock conflicts (this feature)
- Trying to create a resource that already exists
- Trying to delete a resource that has dependencies

Distinct from:

- **400 Bad Request** — the request itself is malformed.
- **422 Unprocessable Entity** — the request is well-formed but
  semantically invalid (validation failure).
- **412 Precondition Failed** — used with conditional headers
  (If-Match, If-Unmodified-Since) for resource-versioning at the
  HTTP layer rather than the application layer.

Reference: [MDN — HTTP 409 Conflict](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/409)

### Why a dialog and not an inline banner

The C3 form has an inline error banner used for plain "save failed"
messages (validation problems, network blips). For 409 we go bigger
— a modal dialog — for two reasons:

1. **The user has to take action.** A stale-data error means the
   page they're looking at is wrong. Making them click OK on a
   modal forces acknowledgement that just-reload is the next step.
2. **Inline banners are easy to miss.** If the user clicked Save
   and the page didn't visibly change, they might not notice a
   small red strip at the top of the form and assume the save
   worked. A dialog can't be missed.

The same `ErrorDialogComponent` from `shared/` is reused — same
visual treatment as the employee-form's 409 handler.

### Why the version stays inert on create

The form sends `version` on create too (because the field is in
the FormGroup), but the create path doesn't use it. The repository's
INSERT doesn't include the version column, so MySQL's `DEFAULT 0`
handles it. The client-sent value is discarded. This is correct —
on create there's no "version the client read" because the row
didn't exist yet. The UPDATE path is the only place version
matters.

## The code, walked through

### Repository — version-aware UPDATE

```java
String sql = """
    UPDATE customers SET
        customerName = ?, ..., updatedBy = ?,
        version = version + 1
    WHERE customerNumber = ?
      AND version = ?
    """;
...
pstmt.setInt(14, c.getCustomerNumber());
pstmt.setInt(15, c.getVersion());

int affected = pstmt.executeUpdate();
if (affected == 0) {
    boolean exists = findById(c.getCustomerNumber()).isPresent();
    if (!exists) {
        throw new RuntimeException(
                "Customer " + c.getCustomerNumber() + " not found");
    }
    throw new OptimisticLockingFailureException(
            "Customer " + c.getCustomerNumber() + " was modified by someone else "
            + "(stale version " + c.getVersion() + "). Reload and try again.");
}
```

The two SQL pieces (`AND version = ?`, `version = version + 1`) are
the only schema-level changes from the C4 update. Everything else is
the existing parameter-binding pattern.

### Frontend — 409 → dialog

```ts
obs.subscribe({
  next: () => { ... navigate back ... },
  error: err => {
    this.saving.set(false);
    if (err?.status === 409) {
      const detail = err?.error?.detail
          ?? 'This customer was modified by someone else while you were editing.';
      this.dialog.open(ErrorDialogComponent, {
        data: {
          title: 'Stale data',
          message: detail + '\n\nReload the page to see the latest version.',
        },
        width: '480px',
      });
      return;
    }
    this.error.set(err?.error?.message ?? err?.message ?? 'Save failed');
  },
});
```

The 409 branch is checked first, returns early. Anything else falls
through to the generic inline banner.

`err.error.detail` reads the `detail` field of the RFC 9457 problem
shape the `GlobalExceptionHandler` returns. The fallback string is
there in case some future advice change drops the detail field.

## How to test

### The conflict flow (two browser windows)

This is the cleanest way to actually exercise the lock:

1. Sign in as `admin`. Open Customer 103's edit page in **window A**.
2. Open the same Customer 103's edit page in **window B**.
3. In **window B**: change the phone number, click Save changes.
   Window B navigates back to /customers — success.
4. In **window A**: change the customer name (without reloading),
   click Save changes.
5. Window A pops a "Stale data" dialog: "Customer 103 was modified
   by someone else (stale version 0). Reload and try again."
6. Click OK. Reload window A. The form now has the phone change
   from window B and version=1. Window A's name change can be
   re-applied and saved.

### Direct API test

```bash
# Read current version
curl -H "Authorization: Bearer $JWT" \
     'http://localhost:9090/api/v1/customers/103' | jq '.version'
# → 0

# Submit with stale version
curl -X PUT \
     -H "Authorization: Bearer $JWT" \
     -H "Content-Type: application/json" \
     -d '{"customerName":"...","contactLastName":"...","contactFirstName":"...","phone":"...","addressLine1":"...","city":"...","country":"...","version": 99}' \
     'http://localhost:9090/api/v1/customers/103' \
     -i
# → HTTP/1.1 409 Conflict
# → { "type": "optimistic-lock-failure", "title": "Stale data", "status": 409, ... }
```

### Version-bump verification

```bash
# Successful update bumps version
curl -X PUT ... 'http://localhost:9090/api/v1/customers/103'
curl 'http://localhost:9090/api/v1/customers/103' | jq '.version'
# → 1
```

### Missing-row handling

```bash
# Delete customer 103 first (or pick a non-existent id)
curl -X PUT -d '{"...","version": 0}' \
     'http://localhost:9090/api/v1/customers/99999' -i
# → 500 (currently — RuntimeException), with message "Customer 99999 not found"
```

The "not found" path currently throws a plain `RuntimeException`,
which is correctly reported but not as a 404. That's a pre-existing
gap in the customer error mapping that's worth tightening up later
(the Employee path has the same issue). Worth flagging here: the
disambiguation is *for the message*, not yet *for the HTTP status*.

## What you just learned

- **Optimistic vs pessimistic locking** — when each is appropriate
  and why web apps default to optimistic.
- **The version-column pattern** — two SQL changes (WHERE check
  and SET bump) that compose atomically into a single UPDATE.
- **Disambiguating stale vs missing** by re-checking existence on
  the conflict path — Spring Data JPA's internal pattern,
  reproduced manually.
- **Inheriting the `@RestControllerAdvice` mapping** — the
  exception-to-HTTP wiring written for one entity covers all
  entities for free.
- **Dialog vs inline banner** — when the user has to take action,
  the modal forces acknowledgement.
- **The minimal C-feature shape** — once the prep work (C4) is in,
  C5 was two SQL bits + one error branch. The cost of the C4/C5
  split is paid back in reviewability.

## Study materials

### Concurrency control

- [Wikipedia — Optimistic concurrency control](https://en.wikipedia.org/wiki/Optimistic_concurrency_control)
- [Wikipedia — Lost update problem](https://en.wikipedia.org/wiki/Lost_update_problem)
- [Martin Fowler — Optimistic Offline Lock](https://martinfowler.com/eaaCatalog/optimisticOfflineLock.html)
- [Vlad Mihalcea — Optimistic vs pessimistic locking](https://vladmihalcea.com/optimistic-vs-pessimistic-locking/)

### Spring exception handling

- [Spring docs — `@RestControllerAdvice`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)
- [Spring docs — `OptimisticLockingFailureException`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/OptimisticLockingFailureException.html)
- [Baeldung — Spring REST exception handling](https://www.baeldung.com/exception-handling-for-rest-with-spring)

### HTTP status codes

- [MDN — HTTP 409 Conflict](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/409)
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)

### Angular Material dialogs

- [Angular Material — MatDialog](https://material.angular.io/components/dialog/overview)
- [Angular Material — Dialog API](https://material.angular.io/components/dialog/api)
