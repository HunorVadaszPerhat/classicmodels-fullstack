# Feature C4 — Audit columns + optimistic-lock version

## What we built

Every customer row now records who created it, who last modified it,
when, and at which "version" of the row's lifetime. Five new columns
— `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `version` —
added to the `customers` table by Flyway **V6** in a single ALTER.

The audit columns are populated automatically by the repository on
every save and update. The version column is added to the schema in
this feature but the optimistic-lock check on UPDATE doesn't fire
yet — that's C5's job. We're laying the groundwork now so C5 only
needs to change the SQL, not the schema.

The customer detail page now shows a quiet footer:

```
Created by hunor on May 6, 2026, 2:09 PM · Last updated by admin on May 7, 2026, 9:21 AM
```

The customer form carries the version through invisibly: a hidden
`FormControl` patched from the loaded customer, sent back on save.
When C5 wires the 409 check, the field is already there.

This is the customer-side application of what V4 (audit) and V5
(version) did separately for employees, but combined into one
migration. The lesson is that audit and version always travel
together — the second migration was avoidable for employees and is
avoided here.

Files touched:

- `classicmodels-backend/src/main/resources/db/migration/V6__customer_audit_columns_and_version.sql` (new)
- `classicmodels-backend/src/main/java/.../model/Customer.java`
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerResponseDTO.java`
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerRequestDTO.java`
- `classicmodels-backend/src/main/java/.../repository/CustomerRepository.java`
- `classicmodels-ui/src/app/customers/customer.model.ts`
- `classicmodels-ui/src/app/customers/customer-detail.component.ts`
- `classicmodels-ui/src/app/customers/customer-detail.component.html`
- `classicmodels-ui/src/app/customers/customer-form.component.ts`

## Why this is worth learning

Three concepts converge. **The audit pattern** itself — a discipline
every production app implements somehow. **MySQL's default-and-on-update
triggers** as a safety net for when the application can't be trusted
to set timestamps consistently. **The strategic case for combined
migrations** — when adding closely-related infrastructure, doing it
in one ALTER is cheaper than doing it in two.

This is your second pass through audit-on-an-entity (employees got
it first). The new bit specific to Customer is the **packaging
decision**: combining audit + version into a single migration
because they always evolve together, instead of repeating the V4 +
V5 split we did for employees.

## Background

### The audit pattern

Four columns. Two for time, two for identity. The naming convention is
near-universal:

| Column      | Type      | When set         | What value |
|-------------|-----------|------------------|------------|
| `createdAt` | TIMESTAMP | INSERT           | server time |
| `updatedAt` | TIMESTAMP | INSERT + UPDATE  | server time |
| `createdBy` | VARCHAR   | INSERT           | username    |
| `updatedBy` | VARCHAR   | INSERT + UPDATE  | username    |

You'll meet variants — `created_at` / `created_on`, `created_by` /
`creator_id`, sometimes a foreign key to a `users` table — but the
shape is always the same.

Why both pairs? **Time + identity together** is what makes the audit
useful. Knowing "this row was modified at 2pm yesterday" without
knowing *who* doesn't help you diagnose a problem. Knowing "Alice
modified this row" without *when* doesn't either.

### Where the values come from — two layers cooperating

**Database layer** (V6 migration):

```sql
ADD COLUMN createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
```

Two MySQL features fire here:

- **`DEFAULT CURRENT_TIMESTAMP`** — if an INSERT doesn't list this
  column, MySQL uses the current timestamp.
- **`ON UPDATE CURRENT_TIMESTAMP`** — if an UPDATE doesn't list this
  column, MySQL refreshes it to the current timestamp.

These triggers fire only when the column is OMITTED from the SQL.
If the application sets it explicitly, the application's value wins.

**Application layer** (`CustomerRepository.save()` / `.update()`):

The repository sets `createdBy` / `updatedBy` explicitly from the
current user. Timestamps it leaves to the DB defaults — simpler code
and identical effect.

Why two layers cooperating? Imagine a colleague runs an emergency
SQL fix at 3am:

```sql
UPDATE customers SET phone = '+1-555-0199' WHERE customerNumber = 103;
```

The application is bypassed entirely. With only application-layer
audit, `updatedAt` would still show last week's date — a quiet lie.
With the DB-level `ON UPDATE CURRENT_TIMESTAMP`, the timestamp gets
refreshed automatically. The audit data tells the truth even when
the application is out of the loop.

References:

- [MySQL — Automatic Initialization and Updating for TIMESTAMP and DATETIME](https://dev.mysql.com/doc/refman/8.0/en/timestamp-initialization.html)
- [Spring Data JPA — Auditing](https://docs.spring.io/spring-data/jpa/reference/auditing.html) — the Spring-native equivalent of what we're doing manually

### `CurrentUser.username()` — the bridge to Spring Security

The repository needs to know "who is making this request?" without
taking on a `SecurityContext` dependency in its constructor. The
existing `CurrentUser` utility (created in F5 for employees) does
exactly that:

```java
public final class CurrentUser {
    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }
}
```

`SecurityContextHolder` stores the authenticated principal in a
`ThreadLocal` for the duration of the request. The JWT auth filter
populates that context on every request that carries a valid JWT.

The fallback to `"system"` covers two cases:

- Unauthenticated requests (shouldn't happen in production but
  doesn't hurt to handle).
- Code running outside a request context — startup migrations,
  scheduled jobs, the `@PostConstruct` that loads seed data.

This is one of those "small utility, used everywhere" abstractions.
The `CustomerRepository` reuses it without modification.

References:

- [Spring Security — `SecurityContextHolder`](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html#servlet-authentication-securitycontextholder)
- [Spring Security — JWT authentication](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

### The optimistic-lock version column — added now, used in C5

```sql
ADD COLUMN version INT NOT NULL DEFAULT 0;
```

Every row carries a version starting at 0. Brand-new rows get 0 from
the DEFAULT. Existing seed rows also got 0 from the same default
during the migration ALTER.

The column is **inert** in C4 — the repository's `update()` doesn't
yet check or bump it. C5 will change two SQL statements:

```java
// C4 (current):
UPDATE customers SET ..., updatedBy = ? WHERE customerNumber = ?

// C5 (next):
UPDATE customers SET ..., updatedBy = ?, version = version + 1
 WHERE customerNumber = ? AND version = ?
```

That's the entire structural change. C4 prepares the ground:
schema, request DTO, response DTO, hidden form field. C5 does the
behavioural change in one focused commit.

### Why combine V4-style + V5-style into one migration?

The employee story did audit (V4) and version (V5) as two separate
ALTERs. Hindsight: that's two ALTER round-trips for two pieces of
infrastructure that always travel together. Both are about
"who changed what when, and is what I'm editing still current?"
Both touch the same table, the same DTOs, the same form. Splitting
them costs you a redundant deployment without buying separability.

The customer migration combines them:

```sql
ALTER TABLE customers
    ADD COLUMN createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD COLUMN createdBy VARCHAR(50) NULL,
    ADD COLUMN updatedBy VARCHAR(50) NULL,
    ADD COLUMN version   INT         NOT NULL DEFAULT 0;
```

The application-layer rollout still happens in two phases (audit in
C4, locking in C5) because *those* are genuinely independent. The DB
schema, however, is the same shape after both phases — there's no
intermediate state where it would be wrong to also have `version`
already.

This is a small example of a more general lesson: **schema migrations
are expensive (they touch every row), behaviour changes are cheap
(they touch one method).** Bundle the schema changes; split the
behavioural changes for reviewability.

### Backfilling existing rows

```sql
UPDATE customers
SET createdBy = 'seed',
    updatedBy = 'seed'
WHERE createdBy IS NULL;
```

We don't know when each seed customer was "created" in real life. The
DB defaults gave them `createdAt = updatedAt = migration time`,
which is the best approximation we have. The synthetic `'seed'`
username distinguishes "imported by the migration" from any real
user that touches the row later. The detail page shows it as
"Created by **seed** on …" which is honest about where the data
came from.

Reference: [Flyway docs — Migrations](https://documentation.red-gate.com/fd/migrations-184127470.html)

### Why audit fields belong on `CustomerResponseDTO` but not `CustomerRequestDTO`

The DTO split is about authority:

- **Response DTO** — what the server tells the client. Audit fields
  are server-authoritative, so the response carries them and the
  client shows them read-only.
- **Request DTO** — what the client tells the server. Audit fields
  are server-authoritative, so the client never sends them. If a
  malicious client *did* send `createdBy: "admin"`, the server
  ignores it because the field doesn't exist on the request type.

The version field is the exception — it lives on *both* DTOs because
the optimistic-lock pattern requires the client to echo back the
version it read. C5 will use that echo to detect concurrent edits.

References:

- [Martin Fowler — Data Transfer Object](https://martinfowler.com/eaaCatalog/dataTransferObject.html) — the canonical writeup
- [Baeldung — DTOs in Spring](https://www.baeldung.com/java-dto-pattern)

### Hidden form field for version

The customer form gets one new control:

```ts
version: [null as number | null],
```

No template binding — it's never rendered. `patchValue()` populates
it on edit because the loaded customer carries `version`. The save
handler includes it in the payload:

```ts
version: raw.version ?? undefined,
```

This is the same shape as the employee form's hidden `version`
field. The point is to have all the wiring in place before C5 lands;
when C5 changes the repository's UPDATE to check version, the
client side already does the right thing.

## The code, walked through

### The migration

Five columns, one UPDATE, twelve lines of SQL not counting comments:

```sql
ALTER TABLE customers
    ADD COLUMN createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD COLUMN createdBy VARCHAR(50) NULL,
    ADD COLUMN updatedBy VARCHAR(50) NULL,
    ADD COLUMN version   INT         NOT NULL DEFAULT 0;

UPDATE customers
SET createdBy = 'seed', updatedBy = 'seed'
WHERE createdBy IS NULL;
```

The migration is idempotent in the sense Flyway cares about — it's
versioned (V6) and won't re-run after it's been applied once. If you
need to revert during local development, drop the schema and let
Flyway re-run from V1.

### Repository — `save()` sets createdBy/updatedBy

```java
final String currentUser = CurrentUser.username();
String sql = """
    INSERT INTO customers (
        customerName, contactLastName, contactFirstName,
        phone, addressLine1, addressLine2, city, state, postalCode,
        country, salesRepEmployeeNumber, creditLimit,
        createdBy, updatedBy
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;
...
pstmt.setString(13, currentUser);  // createdBy
pstmt.setString(14, currentUser);  // updatedBy (same on first insert)
```

`createdAt` / `updatedAt` are deliberately *not* in the column list.
The DB's `DEFAULT CURRENT_TIMESTAMP` fills them in. `version` is
also omitted — its `DEFAULT 0` does the right thing.

### Repository — `update()` sets updatedBy, leaves version alone (for now)

```java
final String currentUser = CurrentUser.username();
String sql = """
    UPDATE customers SET
        customerName = ?, contactLastName = ?, contactFirstName = ?,
        phone = ?, addressLine1 = ?, addressLine2 = ?, city = ?, state = ?,
        postalCode = ?, country = ?, salesRepEmployeeNumber = ?, creditLimit = ?,
        updatedBy = ?
    WHERE customerNumber = ?
    """;
```

`updatedBy` joins the SET list. `updatedAt` is not in the SET list —
the DB's `ON UPDATE CURRENT_TIMESTAMP` refreshes it automatically.
`version` is also not in the SET list yet — that comes in C5.

### Repository — `mapRow()` reads the new columns

```java
Timestamp createdTs = rs.getTimestamp("createdAt");
Timestamp updatedTs = rs.getTimestamp("updatedAt");
c.setCreatedAt(createdTs == null ? null : createdTs.toInstant());
c.setUpdatedAt(updatedTs == null ? null : updatedTs.toInstant());
c.setCreatedBy(rs.getString("createdBy"));
c.setUpdatedBy(rs.getString("updatedBy"));
c.setVersion(rs.getInt("version"));
```

`Timestamp.toInstant()` is the standard JDBC conversion for
`java.sql.Timestamp` to `java.time.Instant`. The null-guard exists
because `getTimestamp` returns `null` for SQL NULL values, even
though our columns are `NOT NULL` — the guard is paranoia against a
future column relaxation.

### Detail page — audit footer

```html
@if (c.createdAt || c.updatedAt) {
  <div class="audit-footer">
    @if (c.createdAt) {
      <span>Created by <strong>{{ c.createdBy ?? 'unknown' }}</strong>
      on {{ c.createdAt | date:'medium' }}</span>
    }
    @if (c.updatedAt && c.updatedAt !== c.createdAt) {
      <span> · Last updated by <strong>{{ c.updatedBy ?? 'unknown' }}</strong>
      on {{ c.updatedAt | date:'medium' }}</span>
    }
  </div>
}
```

Two small UX choices worth flagging. The outer `@if` makes the whole
block disappear if the API doesn't return audit fields (e.g. older
client / older server combinations). The inner `c.updatedAt !== c.createdAt`
suppresses the "Last updated" line on rows that have only ever been
created — showing both lines on a brand-new record reads as "updated
at the same instant it was created," which is technically true but
visually noisy.

The `date:'medium'` pipe gives "May 6, 2026, 2:09 PM"-style output —
not so terse as to be cryptic, not so verbose as to dominate the
page.

### Form — hidden version control

```ts
form = this.fb.group({
  ...
  /*
   * Hidden form field carrying the optimistic-lock version (V6).
   * Loaded from the server when the form opens; sent back on save.
   * The user never sees or types it ...
   */
  version: [null as number | null],
});
```

```ts
const value: Customer = {
  ...
  version: raw.version ?? undefined,
};
```

Nothing rendered for `version` in the template. `patchValue()` sets
it on edit; the save payload sends it back. C5 picks it up and
compares it against the DB's current version.

## How to test

### Migration

1. Restart the backend so Flyway runs V6.
2. Verify in the DB:
   ```bash
   docker compose exec mysql mysql classicmodels -e "DESCRIBE customers;"
   ```
   You should see `createdAt`, `updatedAt`, `createdBy`, `updatedBy`,
   and `version` at the bottom.
3. Confirm seed rows backfilled:
   ```bash
   docker compose exec mysql mysql classicmodels -e \
     "SELECT customerNumber, createdBy, updatedBy, version FROM customers LIMIT 5;"
   ```
   Every row should show `createdBy='seed'`, `updatedBy='seed'`,
   `version=0`.

### Audit on create

1. Sign in as `admin`.
2. Navigate to **Customers** → **New Customer**, fill the form, save.
3. Open the new customer's detail page. Footer should read:
   "Created by **admin** on …" with no second line yet (created and
   updated are equal on a fresh row).

### Audit on update

1. Click **Edit**, change a field, save.
2. Reload the detail page. Footer should now read:
   "Created by **admin** on May 8 · Last updated by **admin** on May 8".
3. The "Last updated" timestamp should be later than "Created."

### Audit by another user

1. Sign out, sign in as a different account (or, in dev, edit the JWT
   payload manually if you have a tool for it).
2. Edit any customer.
3. Detail page footer's "Last updated by" reflects the new user.

### Version field round-trip (preparation for C5)

1. Open the browser dev tools → Network tab.
2. Edit a customer and save.
3. The PUT request body should include `"version": 0` (or whatever
   the current version is). Right now the backend doesn't check it,
   but C5 will.

## What you just learned

- **The audit pattern** — four columns (createdAt/updatedAt/createdBy/
  updatedBy), populated by a combination of DB defaults and
  application-layer logic.
- **MySQL's `ON UPDATE CURRENT_TIMESTAMP`** as a safety net for
  out-of-band SQL changes.
- **Reusing `CurrentUser.username()`** — a small static utility that
  bridges Spring Security to non-Spring code paths.
- **Combined migrations** for closely-related infrastructure — saving
  a deployment when the schema shape is stable across phases.
- **DTO authority split** — response DTOs carry server-authoritative
  fields, request DTOs only carry client-supplied ones (with
  `version` as the deliberate exception).
- **Hidden form fields** for round-tripping server state through the
  client without exposing it in the UI.
- **Suppressing redundant audit lines** when created and updated
  timestamps are identical.

## Study materials

### Flyway

- [Flyway docs — Migrations](https://documentation.red-gate.com/fd/migrations-184127470.html)
- [Flyway docs — Versioning conventions](https://documentation.red-gate.com/fd/migrations-184127470.html#versioned-migrations)
- [Flyway docs — Best practices](https://documentation.red-gate.com/fd/best-practices-184127506.html)

### MySQL TIMESTAMP behaviour

- [MySQL — Automatic Initialization and Updating for TIMESTAMP and DATETIME](https://dev.mysql.com/doc/refman/8.0/en/timestamp-initialization.html)
- [MySQL — TIMESTAMP vs DATETIME](https://dev.mysql.com/doc/refman/8.0/en/datetime.html)

### Spring Security context

- [Spring Security — `SecurityContextHolder`](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html#servlet-authentication-securitycontextholder)
- [Spring Data JPA — Auditing](https://docs.spring.io/spring-data/jpa/reference/auditing.html) — the Spring-native pattern (`@CreatedDate`, `@LastModifiedBy`, etc.) we're approximating manually

### DTO design

- [Martin Fowler — Data Transfer Object](https://martinfowler.com/eaaCatalog/dataTransferObject.html)
- [Baeldung — DTOs in Spring](https://www.baeldung.com/java-dto-pattern)

### Optimistic locking primer (preparation for C5)

- [Baeldung — Optimistic locking with JPA](https://www.baeldung.com/jpa-optimistic-locking)
- [Vladimir Mihalcea — Optimistic vs pessimistic locking](https://vladmihalcea.com/optimistic-vs-pessimistic-locking/)
- [PostgreSQL docs — Concurrency control](https://www.postgresql.org/docs/current/mvcc.html) — same concepts, slightly different lens
