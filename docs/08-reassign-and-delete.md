# Feature 08 — Reassign-and-delete workflow

## What we built

A fifth option in the employee delete dialog — "Reassign to another
employee, then delete." When chosen, a dropdown appears showing every
active employee (other than the one being deleted). Pick a target,
click Delete, and in one transaction the backend:

1. Hands all of this employee's customers to the target,
2. Reroutes every direct report's `reportsTo` to the target,
3. Deletes the source employee.

This is the natural fit for "this person is leaving, transfer their
book of business to a colleague before they walk out the door." It's
also the fix for the scenario we identified earlier — CASCADE was
useless against seeded employees because their customers all have
orders, and NULLIFY left orphaned customers with no rep. Reassigning
gives both rows a happy ending.

Files touched:

- `classicmodels-backend/src/main/java/.../repository/EmployeeRepository.java`
- `classicmodels-backend/src/main/java/.../repository/JdbcEmployeeRepository.java`
- `classicmodels-backend/src/main/java/.../service/EmployeeService.java`
- `classicmodels-backend/src/main/java/.../controller/EmployeeController.java`
- `classicmodels-ui/src/app/employees/employee.service.ts`
- `classicmodels-ui/src/app/employees/employee-dependents-dialog.component.ts`
- `classicmodels-ui/src/app/employees/employee-list.component.ts`

## Why this is worth learning

Three things converge. **Composing the existing AOP infrastructure
with new business logic** — the new repository method is `@MyTransactional`
and rides the same chain we built in earlier features. **Designing a
multi-step UX flow** — a single dialog whose options imply different
follow-up prompts and different backend endpoints. **Branching the
HTTP call at the call site** — when one user-facing action can map to
multiple endpoints, the cleanest pattern is to compose the right
observable and subscribe once.

## Background

### The reassign-and-delete pattern

A close cousin of the database-level `ON DELETE SET NULL` behaviour,
but at the application layer and with a *target* instead of NULL.
Three SQL statements, one transaction:

```sql
-- The customers move to the new rep.
UPDATE customers
   SET salesRepEmployeeNumber = ?  -- target
 WHERE salesRepEmployeeNumber = ?; -- source

-- The direct reports get a new manager.
UPDATE employees
   SET reportsTo = ?              -- target
 WHERE reportsTo = ?;             -- source

-- Now the source has no FK references; safe to delete.
DELETE FROM employees WHERE employeeNumber = ?;
```

All three run inside `@MyTransactional`. If any step fails — say the
final DELETE fails because the source was already removed by a
concurrent request — the previous UPDATEs roll back and the
customers/reports stay where they were.

### Why a dedicated endpoint instead of extending the existing delete

We could have shoehorned this into the existing
`DELETE /employees/{id}?strategy=REASSIGN_DELETE&target=1102` endpoint.
We didn't, for two reasons:

1. **Semantic clarity.** Reassigning + deleting is a substantial
   operation that affects three tables. Its own endpoint makes that
   visible in API logs, OpenAPI docs, and access-control rules.
2. **Body, not query string.** The target employee is data, and
   `POST` with a JSON body is more idiomatic for "do this thing with
   these inputs" than packing it into the URL.

```
POST /api/v1/employees/{id}/reassign-and-delete
{ "targetEmployeeId": 1102 }
```

### Branching the HTTP call

When one UI action (the user clicks Delete) can map to multiple
endpoints depending on what they picked, the cleanest pattern is to
*build the right observable*, then subscribe once:

```ts
const operation = result.strategy === 'REASSIGN_DELETE'
  ? this.service.reassignAndDelete(id, result.targetEmployeeId!)
  : this.service.delete(id, result.strategy);

operation.subscribe({
  next: () => this.load(),
  error: err => { /* show dialog */ },
});
```

The single `.subscribe()` callback handles success and error for
both endpoints uniformly — no duplicated error-handling code per
branch. This generalises: any time you find yourself writing
`if (X) { http.foo().subscribe(...) } else { http.bar().subscribe(...) }`
with the same handlers in both branches, refactor to "build then
subscribe" instead.

### `forkJoin` with three sources

The dialog now needs three pieces of data when it opens:

1. The dependents list (existing).
2. The allowed delete strategies (existing).
3. The list of potential reassign targets (new).

`forkJoin` parallelises all three:

```ts
forkJoin({
  dependents: this.service.getDependents(id),
  strategies: this.service.getAvailableStrategies(),
  everyone: this.service.list(),
}).subscribe({
  next: ({ dependents, strategies, everyone }) => { ... },
  error: ...
});
```

The dialog opens after the slowest of the three responds, but only
once. Adding a fourth source later is a one-line change.

## The code, walked through

### Repository — three statements, one annotation

```java
@MyTransactional
public void reassignAndDelete(int sourceId, int targetId) {
    if (sourceId == targetId) {
        throw new IllegalArgumentException(...);
    }
    try (Connection conn = MyDataSourceUtils.getConnection(dataSource)) {
        // 1. Customers
        // 2. Direct reports
        // 3. DELETE source
    }
}
```

`@MyTransactional` (the annotation we built in our AOP work) wraps
the method body in a database transaction. We don't need any explicit
`commit()` / `rollback()` here — the interceptor handles both based
on whether the method returns normally or throws.

The body uses `MyDataSourceUtils.getConnection(dataSource)` (also
from our AOP work) so the three statements run on the *same*
connection that the interceptor opened. Without that bridge, each
statement would borrow a different connection from the pool and the
transaction wouldn't actually wrap them.

### Dialog — conditional dropdown + result branching

```ts
@if (strategy === 'REASSIGN_DELETE') {
  <div class="confirm-block">
    <p>Who should inherit the customers and direct reports?</p>
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>Target employee</mat-label>
      <mat-select [(ngModel)]="targetEmployeeId">
        @for (m of data.reassignTargets; track m.employeeNumber) {
          <mat-option [value]="m.employeeNumber">
            {{ m.firstName }} {{ m.lastName }} — {{ m.jobTitle }}
          </mat-option>
        }
      </mat-select>
    </mat-form-field>
  </div>
}
```

Same pattern as the CASCADE / DEEP_CASCADE confirmations: a block
that renders only when the matching strategy is selected. The
`canConfirm()` check is extended:

```ts
case 'REASSIGN_DELETE':
  return this.targetEmployeeId != null;
```

So Delete stays disabled until the user picks a target — same UX
discipline as typing "DELETE EVERYTHING" for deep cascade.

### List component — third request joins the forkJoin

```ts
forkJoin({
  dependents: this.service.getDependents(id),
  strategies: this.service.getAvailableStrategies(),
  everyone: this.service.list(),       // new
}).subscribe(({ dependents, strategies, everyone }) => {
  const reassignTargets = everyone
    .filter(e => e.employeeNumber !== id && e.active !== false)
    .sort((a, b) => (a.lastName + a.firstName).localeCompare(b.lastName + b.firstName));
  ...
});
```

We filter and sort client-side. Could have asked the backend for the
filtered list, but `list()` is already cached, returns 23 employees
total, and the post-processing is trivial. For a 10k-employee company
you'd want a dedicated endpoint or the paged endpoint with a search
filter, but at this scale the client is the right place.

## How to test

1. Restart the backend.
2. Navigate to **Employees** and pick someone with dependents — Diane
   Murphy (1002) is the canonical example. Click Delete.
3. The dialog opens. Verify the new option **"Reassign to another
   employee — then delete"** appears in the radio list.
4. Select it. A dropdown labelled "Target employee" appears. The
   Delete button is disabled.
5. Pick a target (e.g., Mary Patterson). The Delete button enables.
6. Click Delete.
7. The list refreshes — Diane is gone. Open Mary Patterson's detail
   page → the customers that used to be Diane's are now Mary's, and
   anyone who reported to Diane now reports to Mary.

DB verification:

```sql
-- Before:
SELECT customerNumber FROM customers WHERE salesRepEmployeeNumber = 1002;
SELECT employeeNumber FROM employees WHERE reportsTo = 1002;

-- (Run the reassign in the UI)

-- After:
SELECT customerNumber FROM customers WHERE salesRepEmployeeNumber = 1056;  -- target
SELECT employeeNumber FROM employees WHERE reportsTo = 1056;
SELECT * FROM employees WHERE employeeNumber = 1002;  -- empty
```

## What you just learned

- **The reassign-then-delete pattern** as the natural alternative to
  hard-delete + cascade for "this person is leaving" workflows.
- **Composing existing infrastructure** — your `@MyTransactional`
  annotation handles the transaction; you just write the SQL.
- **Branching one UI action across multiple endpoints** — build the
  observable, subscribe once.
- **`forkJoin` scaling to N sources** — adding the fourth or fifth
  parallel request is a one-line change.

## Study materials

### Database transactions in JDBC

- [Oracle — JDBC transaction tutorial](https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html)
- [Use The Index, Luke! — Transaction throughput](https://use-the-index-luke.com/sql/where-clause/the-equals-operator) — peripheral but excellent

### Designing REST endpoints for compound actions

- [REST API Design Rulebook — Action resources](https://www.oreilly.com/library/view/rest-api-design/9781449317904/) — pages on "controller resources"
- [Microsoft REST API guidelines — Naming](https://github.com/microsoft/api-guidelines/blob/vNext/Guidelines.md#73-uri-path-design) — opinionated but pragmatic

### RxJS combinators

- [Learn RxJS — forkJoin](https://www.learnrxjs.io/learn-rxjs/operators/combination/forkjoin)
- [Learn RxJS — combineLatest](https://www.learnrxjs.io/learn-rxjs/operators/combination/combinelatest) — sibling operator for live-updating combinations
- [RxJS — All combination operators](https://rxjs.dev/api/index/function/combineLatestAll)

### Material's mat-select for dynamic dropdowns

- [Angular Material — Select](https://material.angular.io/components/select/overview)
- [Angular Material — Select API](https://material.angular.io/components/select/api)
