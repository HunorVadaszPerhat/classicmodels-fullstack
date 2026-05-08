# Feature C6 — Soft delete + delete strategies

## What we built

Two new columns on `customers` (`active`, `terminatedDate`), two new
repository methods (`softDelete`, `deleteAndDeepCascade`), and a
delete dialog that lets the user choose which strategy to apply.
The customer list now hides soft-deleted rows by default; the detail
page renders an "Inactive" chip and a termination-date row so a
soft-deleted customer accessed by URL is unmistakable.

The delete-strategy endpoint and frontend pattern mirror what
employees got in F8 (and earlier feature work that established the
NULLIFY/CASCADE/SOFT trio), but with **only two strategies actually
applicable to customers** — SOFT and DEEP_CASCADE. The reason why
NULLIFY and shallow CASCADE don't apply is itself the headline
lesson of this feature.

Files touched:

- `classicmodels-backend/src/main/resources/db/migration/V7__customer_soft_delete.sql` (new)
- `classicmodels-backend/src/main/java/.../model/Customer.java`
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerResponseDTO.java`
- `classicmodels-backend/src/main/java/.../repository/CustomerRepository.java`
- `classicmodels-backend/src/main/java/.../service/CustomerService.java`
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java`
- `classicmodels-ui/src/app/customers/customer.model.ts`
- `classicmodels-ui/src/app/customers/customer.service.ts`
- `classicmodels-ui/src/app/customers/customer-delete-dialog.component.ts` (new)
- `classicmodels-ui/src/app/customers/customer-list.component.ts`
- `classicmodels-ui/src/app/customers/customer-list.component.html`
- `classicmodels-ui/src/app/customers/customer-detail.component.ts`
- `classicmodels-ui/src/app/customers/customer-detail.component.html`

## Why this is worth learning

Three things converge. **Soft delete** as the default for any entity
that's referenced by historical/financial records — the row stays,
the FKs stay, the analytics stay valid. **The "FK direction matters"
lesson** — what strategies are even *available* depends on which way
the FKs point and whether they're nullable. **Strategy selection as
a UX problem** — making the destructive option visible enough that
users don't pick it accidentally, and impossible to use without an
explicit confirmation.

The new lesson, specific to Customer, is that **the strategy menu is
data-shaped, not framework-shaped**. The enum lists five values but
customer offers only two; the frontend reads the available list from
the backend rather than hardcoding it. Same enum, different applicable
subsets.

## Background

### Soft delete vs hard delete

Two approaches to "this row should disappear":

| Approach | What changes | Reversible | History |
|---|---|---|---|
| Soft | UPDATE: set `active = 0`, `terminatedDate = today` | Yes | Preserved |
| Hard | DELETE FROM | No | Lost (if no FK protection) |

Soft delete is the default for any entity whose row is *referenced
by* other rows that should outlive it. Customers have orders and
payments. Employees have customers and direct reports. Both cases
demand soft delete because hard-deleting destroys analytical value
on rows that don't even belong to the deleted entity.

Hard delete is appropriate only when: (a) the entity is a leaf in the
FK graph, (b) you genuinely never want to see it again, or (c) you're
implementing a "permanently delete from trash" flow that follows a
soft-delete first.

References:

- [Wikipedia — Soft delete](https://en.wikipedia.org/wiki/Tombstone_(data_store))
- [Vladimir Khorikov — Soft deletion or hard deletion?](https://enterprisecraftsmanship.com/posts/soft-deletes/)
- [Microsoft — Implementing soft delete](https://learn.microsoft.com/en-us/azure/architecture/patterns/soft-delete)

### Why customer's strategy menu is shorter than employee's

Five strategies exist in the `DeleteStrategy` enum: SOFT, NULLIFY,
CASCADE, DEEP_CASCADE, REASSIGN_DELETE. Customer supports two of
them. Why?

| Strategy | Applies to customer? | Why / why not |
|---|---|---|
| SOFT | Yes | Mark inactive — preserves orders + payments. The default. |
| NULLIFY | **No** | `orders.customerNumber` and `payments.customerNumber` are NOT NULL. Can't NULL them. |
| CASCADE | **No** | "Shallow cascade" for employees works because their child tables (customers) are themselves above orders/payments — you can stop at the customer layer and let the FK constraint reject if there's history. Customer has no equivalent stopping point; orders and payments are direct children. There's no "shallow" path. |
| DEEP_CASCADE | Yes | Walk down to orderdetails + orders + payments + customer. Destroys financial history; gated by feature flag. |
| REASSIGN_DELETE | **No** | Reassigning a customer's orders to "another customer" doesn't model anything real. Each order is a real-world transaction tied to one party. |

This isn't an enum design problem — it's a data-shape consequence.
The same enum can apply to many entities; what varies is which
strategies are *available*. The customer service's
`availableDeleteStrategies()` returns only `[SOFT, (DEEP_CASCADE)]`
and the controller rejects the others with a 400 explaining why.

### NOT NULL FKs and the cascade path

The schema:

```sql
orders     (customerNumber INT NOT NULL FK → customers)
payments   (customerNumber INT NOT NULL FK → customers)
orderdetails (orderNumber INT NOT NULL FK → orders)
```

Three NOT NULL FKs to children of customer mean any hard delete must
clear the children first. The dependency order for DEEP_CASCADE:

```
orderdetails  (grandchildren via orders)
   ↓
orders        (children)
   ↓
payments      (children, parallel to orders)
   ↓
customer      (parent)
```

Order-of-deletion matters at every layer. Get it wrong, InnoDB
rejects with a constraint violation. The repository encodes the
order in four sequential `DELETE FROM ...` statements wrapped in
one transaction — if any layer fails, the whole thing rolls back.

References:

- [MySQL — Foreign key constraints](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html)
- [InnoDB — Foreign key restrictions](https://dev.mysql.com/doc/refman/8.0/en/innodb-foreign-key-constraints.html)
- [Use The Index, Luke! — Foreign keys](https://use-the-index-luke.com/sql/where-clause/null/null-foreign-keys)

### Filtering soft-deleted rows from default lists

Once you add `active`, every list-style query needs a `WHERE active = 1`
clause to keep terminated rows from leaking into the UI. This applies
to:

- `findAll()` — used by the sales-rep dropdown, etc.
- `findAllPaged()` — the customer list page
- `countAll()` — paired with `findAllPaged` so the paginator's
  total reflects the filter

Note `findById()` does NOT filter — viewing or editing a soft-deleted
customer by direct URL should still work (the detail page shows the
"Inactive" chip; the edit form would let you reactivate, though the
form doesn't yet expose the `active` field — that's a sensible
follow-up).

This is the second WHERE clause in the dynamic-SQL pattern from C2,
combined with the search clause:

```java
StringBuilder where = new StringBuilder("WHERE active = 1");
if (search != null && !search.isBlank()) {
    where.append(" AND (customerName LIKE ? OR ...)");
}
```

The fixed `WHERE active = 1` is now the foundation; everything else
is `AND`-appended.

### Manual transaction handling vs `@MyTransactional`

The `deleteAndDeepCascade` method uses old-school
`conn.setAutoCommit(false) / commit() / rollback()` rather than the
`@MyTransactional` annotation that the employee version uses. Why?

The customer repository hasn't yet been wired into the AOP
transaction infrastructure — that's a separate piece of plumbing
that the employee work happened to need first. Until that wiring
lands for customer, manual `try / commit / rollback / finally
setAutoCommit(true)` is the correct fallback. It's verbose, but it's
also obviously correct: each step is visible.

When the AOP wiring eventually arrives for customer, the entire
manual transaction block collapses to a single `@MyTransactional`
annotation and the body becomes just the four DELETE statements.
That's a refactor for a future feature, not a blocker for C6.

References:

- [Oracle — JDBC transactions tutorial](https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html)
- [Spring docs — `@Transactional`](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html) — the production-grade equivalent

### Strategy selection as a UX problem

The dialog has two competing requirements:

- Make the safe option (SOFT) feel like the default. Users
  shouldn't have to think about which option to pick for the
  common case.
- Make the destructive option (DEEP_CASCADE) hard to pick by
  accident. A radio button alone isn't enough — typos happen,
  fast-clicking happens.

Three UX devices in combination:

1. **SOFT is selected by default** when the dialog opens. The user
   has to actively change the selection.
2. **DEEP_CASCADE has its own visual treatment** — red title,
   explicit "Permanently delete + all history" wording, no
   sugar-coating.
3. **DEEP_CASCADE requires typing `DELETE EVERYTHING`** before the
   confirm button enables. A typed phrase forces deliberate intent;
   no fast click can produce it accidentally.

Same pattern the employee dialog uses — battle-tested at this point.

Reference: [GitHub's "type the repo name to delete" pattern](https://github.com/) — the most-copied destructive-confirm idiom in modern UX.

### Server-driven feature gating

The dialog reads its strategy list from
`GET /customers/delete-strategies` on every open. Why fetch fresh
each time?

- The cost is trivial — tiny response, response time of a few ms.
- A feature-flag flip on the backend (e.g. someone enabling
  `app.delete.allow-deep-cascade=true` for an emergency
  cleanup) takes effect immediately, without restarting the
  frontend.
- The frontend never hardcodes the list of strategies — adding or
  removing strategies on the backend is a zero-frontend-change
  operation.

This is the same shape as `GET /employees/delete-strategies` — the
feature-flag-aware "what am I allowed to do?" pattern.

### `mat-radio-group` with conditional options

```html
<mat-radio-group [(ngModel)]="strategy">
  @if (data.availableStrategies.includes('SOFT')) {
    <mat-radio-button value="SOFT">...</mat-radio-button>
  }
  @if (data.availableStrategies.includes('DEEP_CASCADE')) {
    <mat-radio-button value="DEEP_CASCADE">...</mat-radio-button>
  }
</mat-radio-group>
```

Two `@if` blocks rather than a `@for` over the strategies because
each strategy has bespoke help text. A loop would force a lookup
table for the descriptions, which is more code for no expressive
gain at this size. If the strategy list grows past three or four,
refactor to data-driven rendering.

Reference: [Angular Material — MatRadioGroup](https://material.angular.io/components/radio/overview)

## The code, walked through

### Repository — softDelete is one UPDATE

```java
public void softDelete(int id) {
    String sql = """
        UPDATE customers
           SET active = 0,
               terminatedDate = CURRENT_DATE
         WHERE customerNumber = ?
        """;
    ...
}
```

Three things to notice. `CURRENT_DATE` is the MySQL function for
"today" — we don't pass it from Java because the DB's clock is the
authoritative source for "what day is it" in a deployed environment.
The query has no `WHERE active = 1` predicate — soft-deleting an
already-soft-deleted row is a no-op (idempotent), which is what we
want. And the update doesn't bump `version`; soft-delete is a
structural change, not a content edit, so the optimistic-lock
contract doesn't apply.

### Repository — deeplyCascade walks four layers

```java
try {
    // 1. orderdetails — grandchildren of customer via orders.
    "DELETE FROM orderdetails WHERE orderNumber IN (
       SELECT orderNumber FROM orders WHERE customerNumber = ?)"
    // 2. orders — direct children.
    "DELETE FROM orders WHERE customerNumber = ?"
    // 3. payments — siblings of orders, also children.
    "DELETE FROM payments WHERE customerNumber = ?"
    // 4. customer itself.
    "DELETE FROM customers WHERE customerNumber = ?"
    conn.commit();
} catch (SQLException ex) {
    conn.rollback();
    throw ex;
}
```

Set-based, not row-by-row. The `IN (subquery)` for orderdetails is
the canonical way to delete grandchildren without iterating over
parents in Java. Atomic — the whole thing succeeds or rolls back to
the original state.

### Service — strategy dispatch with a feature gate

```java
public void delete(int id, DeleteStrategy strategy) {
    repo.findById(id).orElseThrow(() -> ...);

    switch (strategy) {
        case SOFT -> repo.softDelete(id);
        case DEEP_CASCADE -> {
            if (!allowDeepCascade) {
                throw new IllegalStateException(
                    "DEEP_CASCADE is disabled in this environment. " +
                    "Set app.delete.allow-deep-cascade=true to enable it.");
            }
            repo.deleteAndDeepCascade(id);
        }
        case NULLIFY, CASCADE, REASSIGN_DELETE -> throw new IllegalArgumentException(
                strategy + " is not supported for customers — orders/payments FKs are NOT NULL. "
                + "Use SOFT (preserves history) or DEEP_CASCADE (destroys it).");
    }
}
```

Three guards at three layers:

- **Existence check** — fast 404 instead of a silent no-op.
- **Feature flag re-check** — even though the controller already
  binds the enum, a future caller (test, scheduled job) might
  bypass the controller. Re-checking here makes the rule binding
  for any caller.
- **Inapplicable-strategy rejection** — explicit error message
  telling the developer why their strategy doesn't fit, rather
  than a silent default-to-something-else.

### Frontend — dialog with strategy radio + destructive typing

```ts
canConfirm(): boolean {
  if (this.strategy === 'DEEP_CASCADE') {
    return this.confirmPhrase === 'DELETE EVERYTHING';
  }
  return this.data.availableStrategies.includes(this.strategy);
}
```

The button's disabled state is computed from the current strategy
and the typed phrase. Switching back to SOFT clears the constraint
(no phrase needed), so the user can flip between options
mid-dialog without losing track.

### List component — open dialog, dispatch, show error

```ts
remove(customer: Customer) {
  this.service.getAvailableStrategies().subscribe(availableStrategies => {
    const ref = this.dialog.open(CustomerDeleteDialogComponent, {
      data: { customerNumber: ..., customerName: ..., availableStrategies },
      width: '520px',
    });
    ref.afterClosed().subscribe(result => {
      if (!result) return;
      this.service.delete(customer.customerNumber, result.strategy).subscribe({
        next: () => this.load(),
        error: err => this.dialog.open(ErrorDialogComponent, ...),
      });
    });
  });
}
```

Two nested subscribes (strategies → dialog result), not three. The
dialog itself is sync once opened; only the strategies fetch and the
delete call are async. This shape extends naturally if we add a
"dependents preview" call later — that becomes a third forkJoin
key, not a third nesting level.

## How to test

### Soft delete (happy path)

1. Restart the backend so Flyway runs V7.
2. Sign in. Navigate to **Customers**.
3. Pick any customer (say "Atelier graphique") → click Delete.
4. Dialog opens: "Delete customer #103? Atelier graphique"
5. Default selection is "Mark as inactive (recommended)". Click
   the warn button (label: "Mark as inactive").
6. Customer disappears from the list — total count drops by one.
7. Verify in the DB:
   ```sql
   SELECT customerNumber, customerName, active, terminatedDate
   FROM customers WHERE customerNumber = 103;
   -- → active=0, terminatedDate=today
   ```
8. Visit `/customers/103` directly. The detail page still loads and
   shows an **Inactive** chip and "Terminated on" row.

### DEEP_CASCADE (destructive path)

1. Set `app.delete.allow-deep-cascade=true` in
   `application.yaml` (or via env var) and restart the backend.
2. Open the Delete dialog. The DEEP_CASCADE option is now visible
   (red title: "Permanently delete + all history").
3. Select it. A "Type DELETE EVERYTHING" input appears. Confirm
   button is disabled.
4. Type "delete everything" (lowercase). Button still disabled.
5. Type "DELETE EVERYTHING". Button enables.
6. Click. Customer + every order + every order detail + every
   payment for that customer is deleted in one transaction.
7. Verify:
   ```sql
   SELECT * FROM customers WHERE customerNumber = 103;        -- empty
   SELECT * FROM orders     WHERE customerNumber = 103;        -- empty
   SELECT * FROM payments   WHERE customerNumber = 103;        -- empty
   SELECT * FROM orderdetails WHERE orderNumber IN (
     SELECT orderNumber FROM orders WHERE customerNumber = 103
   );                                                           -- empty
   ```

### DEEP_CASCADE blocked (flag off)

1. With `app.delete.allow-deep-cascade=false`, open the Delete dialog.
   The DEEP_CASCADE option doesn't render — only "Mark as inactive"
   is shown.
2. From the API directly:
   ```bash
   curl -X DELETE 'http://localhost:9090/api/v1/customers/103?strategy=DEEP_CASCADE' \
        -H "Authorization: Bearer $JWT" -i
   # → 500 Internal Server Error (currently)
   # → "DEEP_CASCADE is disabled in this environment..."
   ```
   The error mapping for `IllegalStateException` could be tightened
   to 403 Forbidden later — current behavior is correct (the
   operation is refused) but the status code is generic.

### Strategy filter on list

1. Soft-delete a customer (via the dialog).
2. Refresh the list — the customer is gone.
3. Search for the customer's name. Still gone (the search clause
   is `AND` to the `WHERE active = 1`).
4. Open the customer's detail page directly by URL. Still loads
   (findById ignores active).

## What you just learned

- **Soft delete as the default** for any entity referenced by
  history that should outlive it.
- **The "FK direction" lesson** — what hard-delete strategies are
  even applicable depends on which FKs point at the row and
  whether they're nullable.
- **Manual JDBC transactions** as the fallback when AOP infrastructure
  isn't yet in place — verbose but obvious, and a one-annotation
  refactor away from the cleaner version.
- **Server-driven strategy gating** — the frontend reads available
  strategies from the backend on every open, so feature-flag flips
  apply immediately.
- **Destructive-typing UX** — typed phrases force deliberate intent
  for irreversible operations.
- **Filtering soft-deleted rows** at every list query, while leaving
  `findById` permissive so direct-URL access still works.
- **Status chips on the detail page** as the visual cue that
  "this row is in a non-default state."

## Study materials

### Soft delete patterns

- [Vladimir Khorikov — Soft deletion or hard deletion?](https://enterprisecraftsmanship.com/posts/soft-deletes/)
- [Microsoft — Soft delete pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/soft-delete)
- [Stack Overflow — When to use soft delete?](https://stackoverflow.com/questions/378331/) — long but thoughtful answers

### Foreign keys and deletion

- [MySQL — InnoDB FK constraints](https://dev.mysql.com/doc/refman/8.0/en/innodb-foreign-key-constraints.html)
- [PostgreSQL docs — Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html#DDL-CONSTRAINTS-FK) — same concepts
- [Use The Index, Luke! — Indexes on FKs](https://use-the-index-luke.com/sql/join/nested-loops-join-n-m)

### JDBC transactions

- [Oracle — JDBC transaction tutorial](https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html)
- [Spring docs — `@Transactional`](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)

### Angular Material dialogs

- [Angular Material — MatDialog overview](https://material.angular.io/components/dialog/overview)
- [Angular Material — MatRadioGroup](https://material.angular.io/components/radio/overview)
- [Angular Material — Dialog patterns](https://material.angular.io/components/dialog/examples)

### Destructive-action UX

- [Nielsen Norman Group — Confirming destructive actions](https://www.nngroup.com/articles/confirmation-dialog/)
- [GitHub Primer — Confirm dialogs](https://primer.style/components/dialog/) — opinionated, well-tested patterns
