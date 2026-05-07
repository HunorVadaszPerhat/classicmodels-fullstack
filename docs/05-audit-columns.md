# Feature 05 — Audit columns on employees

## What we built

Every employee row now records who created it, who last modified it,
and when. Four new columns — `createdAt`, `updatedAt`, `createdBy`,
`updatedBy` — added to the `employees` table by Flyway V4.

The columns are populated automatically by the repository on every
save and update. The user identity comes from the Spring Security
authentication context that the JWT auth filter (Stage 3) puts in
place on every request. The timestamps come partly from MySQL's
built-in `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`
and partly from the application — both layers cooperating means the
audit data stays correct even if data is changed outside the app
(e.g. by a manual SQL fix).

The detail page now shows a quiet footer:

```
Created by hunor on May 6, 2026, 2:09 PM · Last updated by admin on May 7, 2026, 9:21 AM
```

Files touched:

- `classicmodels-backend/src/main/resources/db/migration/V4__employee_audit_columns.sql` (new)
- `classicmodels-backend/src/main/java/.../audit/CurrentUser.java` (new)
- `classicmodels-backend/src/main/java/.../model/Employee.java`
- `classicmodels-backend/src/main/java/.../dto/employee/EmployeeResponseDTO.java`
- `classicmodels-backend/src/main/java/.../repository/JdbcEmployeeRepository.java`
- `classicmodels-ui/src/app/employees/employee.service.ts`
- `classicmodels-ui/src/app/employees/employee-detail.component.html`
- `classicmodels-ui/src/app/employees/employee-detail.component.ts`

## Why this is worth learning

Three concepts converge. **The audit pattern** itself — a discipline
every production app implements somehow. **MySQL's default-and-on-update
triggers** as a safety net for when the application can't be trusted
to set timestamps consistently. **`SecurityContextHolder`** as the
canonical way for non-controller code to ask "who made this request?"

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

### Where the values come from

Two layers, each contributing:

**Database layer** (V4 migration):

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

**Application layer** (`JdbcEmployeeRepository.save()` and `.update()`):

The repository sets `createdBy`/`updatedBy` explicitly. Timestamps it
leaves to the DB defaults — simpler code and identical effect.

### Why two layers cooperating?

Suppose a colleague runs an emergency SQL fix at 3am:

```sql
UPDATE employees SET email = 'fixed@example.com' WHERE employeeNumber = 1370;
```

The application is bypassed entirely. With only application-layer
audit logic, `updatedAt` would still show whatever value it had
before this fix — silently wrong. With MySQL's `ON UPDATE` trigger,
`updatedAt` is refreshed automatically. The `updatedBy` column would
keep its old value (the DB has no idea who just connected), so you'd
see something like `updatedBy = 'admin'` and `updatedAt = "3am
yesterday"` which is enough to trigger the question "wait, was admin
really at the keyboard at 3am?" — and you'd find the bypass.

The combination of "DB tracks what it can" + "app fills in user
identity" is the standard defence-in-depth play.

References:

- [MySQL — `DEFAULT` clause](https://dev.mysql.com/doc/refman/8.0/en/data-type-defaults.html)
- [MySQL — `ON UPDATE CURRENT_TIMESTAMP`](https://dev.mysql.com/doc/refman/8.0/en/timestamp-initialization.html)

### Spring Security's `SecurityContextHolder`

The bridge between "this request is authenticated as X" and "any code
in the call stack can find out who X is."

`SecurityContextHolder` is a static facade with one main method:

```java
SecurityContext ctx = SecurityContextHolder.getContext();
Authentication auth = ctx.getAuthentication();
String username = auth.getName();
```

The context is stored in a `ThreadLocal`, so it follows the request
through whatever method calls happen during processing — controllers,
services, repositories, our AOP interceptors. The JWT filter we wrote
in Stage 3 populates the context on every authenticated request; once
the request finishes, Spring Security clears the ThreadLocal so the
next request starts fresh.

For our use case (auditing), we want a tiny helper:

```java
public static String username() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
            || !auth.isAuthenticated()
            || "anonymousUser".equals(auth.getPrincipal())) {
        return "system";
    }
    return auth.getName();
}
```

The "system" fallback handles edge cases: the request isn't
authenticated, or this code is running outside any request (e.g.
during a startup migration). Either way, we get a valid string to
write to the column.

References:

- [Spring Security — Authentication architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
- [Spring Security — SecurityContextHolder](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html#servlet-authentication-securitycontextholder)
- [Baeldung — SecurityContextHolder](https://www.baeldung.com/get-user-in-spring-security)

### What about Spring Data JPA's `@CreatedDate` / `@LastModifiedDate`?

JPA users get this for free with annotations:

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Employee {
    @CreatedDate     private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
    @CreatedBy       private String createdBy;
    @LastModifiedBy  private String updatedBy;
}
```

Plus a config bean exposing an `AuditorAware<String>` that returns
the current username, and a `@EnableJpaAuditing` switch.

Same pattern as ours — JPA just hides the population logic in a
listener. If/when this project ever migrates to JPA, you'd delete the
explicit SQL we just wrote and replace it with these annotations.

Reference: [Spring Data JPA — Auditing](https://docs.spring.io/spring-data/jpa/reference/auditing.html)

### Could we have used AOP instead?

Yes. A `@MyAuditable` annotation on save/update methods, with an
interceptor that reads the entity arg, sets the audit fields via
reflection, and then proceeds. The downsides are: more magic (the
SQL doesn't show what's being set), and a tighter coupling between
the AOP framework and entity field names.

For learning purposes, the explicit population is more honest — you
can see exactly which columns get touched. AOP-driven auditing is a
fine production pattern (and it's what JPA's listener does
internally), but it's an optimisation for "many entities all need
auditing"; we have one entity here.

## The code, walked through

### V4 migration — DB defaults + backfill

```sql
ALTER TABLE employees
    ADD COLUMN createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD COLUMN createdBy VARCHAR(50) NULL,
    ADD COLUMN updatedBy VARCHAR(50) NULL;

UPDATE employees SET createdBy = 'seed', updatedBy = 'seed' WHERE createdBy IS NULL;
```

The `ALTER` runs once. Existing rows get their timestamps from the
default (current time at migration); new rows get them at INSERT
time. The follow-up `UPDATE` backfills `createdBy`/`updatedBy` for
the seed data — without this they'd stay NULL.

### CurrentUser — small helper, big leverage

```java
public final class CurrentUser {
    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() ||
                "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }
}
```

Static method, returns a string, never throws. That makes it safe to
call from any layer — repositories, services, batch jobs — without
worrying about exception propagation or null pointers.

### Repository — populate on write

```java
public Employee save(Employee e) {
    final String currentUser = CurrentUser.username();
    final String insertAuto = """
        INSERT INTO employees (
            lastName, firstName, ..., jobTitle,
            createdBy, updatedBy
        ) VALUES (?, ?, ..., ?, ?, ?)
        """;
    ...
    ps.setString(8, currentUser);  // createdBy
    ps.setString(9, currentUser);  // updatedBy
    ...
}

public void update(Employee e) {
    final String currentUser = CurrentUser.username();
    String sql = """
        UPDATE employees SET
            lastName = ?, ..., jobTitle = ?,
            updatedBy = ?
        WHERE employeeNumber = ?
        """;
    ...
    ps.setString(8, currentUser);   // updatedBy
    ...
}
```

`save` populates both `createdBy` and `updatedBy` (a row's first
modification is its creation). `update` populates only `updatedBy` —
`createdBy` is immutable per the audit pattern.

Notice we don't write `createdAt`/`updatedAt` from Java at all. The
DB defaults handle it cleanly.

### mapRow — read it back

```java
java.sql.Timestamp createdTs = rs.getTimestamp("createdAt");
java.sql.Timestamp updatedTs = rs.getTimestamp("updatedAt");
e.setCreatedAt(createdTs == null ? null : createdTs.toInstant());
e.setUpdatedAt(updatedTs == null ? null : updatedTs.toInstant());
e.setCreatedBy(rs.getString("createdBy"));
e.setUpdatedBy(rs.getString("updatedBy"));
```

`Timestamp.toInstant()` converts the JDBC `Timestamp` (which is
JVM-time-zone-anchored) into a `java.time.Instant` (UTC). For
audit timestamps you almost always want UTC-anchored `Instant`s
because the alternative — local time — moves around when the server
crosses time zones.

### Frontend — quiet footer

```html
@if (e.createdAt || e.updatedAt) {
  <div class="audit-footer">
    @if (e.createdAt) {
      <span>Created by <strong>{{ e.createdBy ?? 'unknown' }}</strong>
      on {{ e.createdAt | date:'medium' }}</span>
    }
    @if (e.updatedAt && e.updatedAt !== e.createdAt) {
      <span> · Last updated by <strong>{{ e.updatedBy ?? 'unknown' }}</strong>
      on {{ e.updatedAt | date:'medium' }}</span>
    }
  </div>
}
```

Two pipes worth knowing: `| date:'medium'` is Angular's locale-aware
date formatting. `'medium'` is one of several preset formats; the
others are `'short'`, `'long'`, `'full'`, plus full custom formats.
The `??` is JavaScript's nullish-coalescing operator.

The check `e.updatedAt !== e.createdAt` hides the "Last updated"
line when the row has never been modified since creation —
otherwise the same timestamp would be repeated twice.

## How to test

1. Restart the backend so Flyway applies V4. Watch logs for
   `Successfully applied 1 migration to schema "classicmodels"`.
2. Sign in (e.g. as `admin / admin123`).
3. Navigate to **Employees** → click any seed employee (e.g. Diane
   Murphy). Scroll to the bottom of the card. You should see:
   ```
   Created by seed on May 6, 2026, 1:42 PM
   ```
   No "Last updated" line because seeds were never modified.
4. Click **Edit**, change something trivial (e.g. extension), Save.
5. Open the detail page again. Now you should see:
   ```
   Created by seed on May 6, 2026, 1:42 PM ·
   Last updated by admin on May 6, 2026, 1:55 PM
   ```
6. Sign out, sign in as `user / user123`, edit again, save. The
   `updatedBy` should now read `user`.

Direct DB check:

```sql
SELECT employeeNumber, firstName, lastName,
       createdBy, updatedBy, createdAt, updatedAt
FROM employees
ORDER BY updatedAt DESC
LIMIT 5;
```

Most-recently edited rows come first; the audit columns tell you
who and when.

## What you just learned

- **The audit-columns pattern**: four columns (createdAt/updatedAt/
  createdBy/updatedBy), populated automatically, never by hand.
- **MySQL's `DEFAULT CURRENT_TIMESTAMP` and `ON UPDATE CURRENT_TIMESTAMP`**
  as a defence-in-depth backstop for when the app can't be relied on.
- **`SecurityContextHolder`** as the standard way for non-controller
  code to find out who's making a request.
- **`java.sql.Timestamp.toInstant()`** for converting JDBC timestamps
  into UTC-anchored `java.time.Instant`s.
- **Angular's `date` pipe** for locale-aware date formatting.

## Study materials

### MySQL timestamps & defaults

- [MySQL — Automatic initialization and updating for TIMESTAMP](https://dev.mysql.com/doc/refman/8.0/en/timestamp-initialization.html)
- [MySQL — Data type default values](https://dev.mysql.com/doc/refman/8.0/en/data-type-defaults.html)
- [Use The Index, Luke! — TIMESTAMP best practices](https://use-the-index-luke.com/sql/where-clause/searching-for-ranges/index-time-key)

### Spring Security context

- [Spring Security — Authentication architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
- [Baeldung — Get the current logged-in user](https://www.baeldung.com/get-user-in-spring-security)

### Spring Data JPA auditing (the easier-but-magic version)

- [Spring Data JPA — Auditing](https://docs.spring.io/spring-data/jpa/reference/auditing.html)
- [Baeldung — Spring Data JPA Auditing](https://www.baeldung.com/database-auditing-jpa)

### Java time API

- [Oracle — `java.time` package overview](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html)
- [Baeldung — Instant vs LocalDateTime](https://www.baeldung.com/java-instant-vs-localdatetime)
- [Stuart Marks — "JSR-310 — A Java Time API for the 21st Century"](https://www.youtube.com/watch?v=nEQHB7TGNHk) — the API designer's own talk

### Angular date pipes

- [Angular — `DatePipe`](https://angular.dev/api/common/DatePipe)
- [Angular — Built-in pipes](https://angular.dev/guide/templates/pipes) — the full list
