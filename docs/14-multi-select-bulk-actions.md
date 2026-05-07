# Feature 14 — Multi-select + bulk actions

## What we built

The employee list now has a checkbox column. Tick rows individually,
or use the header checkbox to select every row on the current page.
Selections persist across pagination (move to page 2 and your page-1
selections are still there). When you have anything selected, a blue
"bulk action" bar appears above the table showing the count and a
**Delete selected** button.

Clicking it opens a confirmation dialog: pick the strategy
(SOFT / NULLIFY / CASCADE / DEEP_CASCADE), type-to-confirm for
destructive ones, hit Delete. The frontend sends one request to a
new `POST /employees/bulk-delete` endpoint; the backend processes
each id independently and returns a result envelope. A summary
dialog shows "N succeeded, M failed" with per-id failure reasons,
so the user can fix and retry whatever didn't work.

Files touched:

- `classicmodels-backend/src/main/java/.../dto/employee/BulkDeleteRequestDTO.java` (new)
- `classicmodels-backend/src/main/java/.../dto/employee/BulkOperationResultDTO.java` (new)
- `classicmodels-backend/src/main/java/.../service/EmployeeService.java` — adds `bulkDelete()`
- `classicmodels-backend/src/main/java/.../controller/EmployeeController.java` — new endpoint
- `classicmodels-ui/src/app/employees/employee.service.ts` — adds `bulkDelete()` + `BulkOperationResult`
- `classicmodels-ui/src/app/employees/employee-bulk-delete-dialog.component.ts` (new)
- `classicmodels-ui/src/app/employees/employee-bulk-result-dialog.component.ts` (new)
- `classicmodels-ui/src/app/employees/employee-list.component.ts` — selection state, bulk-delete handler
- `classicmodels-ui/src/app/employees/employee-list.component.html` — checkbox column, bulk-bar

## Why this is worth learning

Bulk operations are the unglamorous workhorse of admin UIs. A
"delete one row" workflow is straightforward; a "delete fifty rows"
workflow has to grapple with three things at once.

**Selection as state.** Tracking which rows are selected through
pagination, sorting, and live data refreshes is a small but real
state-management problem. The "right" abstraction (a `Set<id>`
backed by a signal) and the "wrong" abstraction (a `selected`
boolean on each Employee object) have very different consequences.

**Atomicity vs. partial success.** A single transaction means "all
or nothing" — clean semantics, but one bad row blocks the batch.
Per-item processing means partial success — messier semantics, but
admins can make progress. Real bulk endpoints almost always go with
the second model, and the API has to reflect that in its response
shape (`successCount`, `failureCount`, `failures: [...]`).

**The "indeterminate" header checkbox.** A spreadsheet-style header
checkbox needs three states: checked (everything selected),
unchecked (nothing selected), and *indeterminate* (some selected).
Material's `MatCheckbox` exposes this via `[indeterminate]`, but you
have to compute it correctly — and decide what "everything" means
(the visible page, or the whole filtered dataset?).

## Background

### Selection state — id-set vs. row-flag

Two ways to track "which rows are selected":

**Per-row `selected: boolean` flag** — extend the `Employee` shape.
Simple at first; reads great in the template (`{{ row.selected }}`).
But: doesn't survive a refetch (the new objects don't have the
flag), doesn't survive pagination (the row only exists in memory
for the visible page), and conflates "model state from the server"
with "transient UI state."

**Set of selected ids** (what we use):

```ts
selection = signal<Set<number>>(new Set());
```

The Set is a transient client-side concept. It outlives any
particular row object. Pagination is a no-op for selection — the
ids that aren't on the visible page are still in the Set. Refetching
the data doesn't blow away selection. The cost is a slightly less
direct template binding (`isSelected(row)` instead of `row.selected`).

Beyond a few hundred items, the Set approach is what every grid
library you've heard of (AG Grid, ag-Grid, MUI DataGrid, TanStack
Table) does, for the same reasons.

References:

- [MDN — `Set`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set)
- [TanStack Table — Row Selection](https://tanstack.com/table/v8/docs/api/features/row-selection) — same id-set model, well-documented

### Tri-state checkboxes

A tri-state checkbox has three visual states: checked, unchecked,
and **indeterminate** (a horizontal line, signalling "neither but
something").

```html
<mat-checkbox
  [checked]="state === 'all'"
  [indeterminate]="state === 'some'"
  (change)="togglePage()">
</mat-checkbox>
```

The `state` is computed from the data:

```ts
pageSelectionState = computed<'none' | 'some' | 'all'>(() => {
  // count visible rows that are in the selection
});
```

Two design choices buried in this:

- **What's the "all" set?** We chose "every row on the current
  page," not "every row in the dataset." The header acts on what's
  visible. This matches every email client and spreadsheet you've
  used.
- **What does clicking `indeterminate` do?** Two reasonable answers
  — "select all" or "deselect all." We picked select-all because
  the user can already deselect by clicking individual rows, but
  there's no other affordance for selecting more rows.

References:

- [Material Web — Checkbox states](https://material.angular.io/components/checkbox/overview#indeterminate-state)
- [HTML spec — `indeterminate`](https://html.spec.whatwg.org/multipage/input.html#the-input-element:dom-input-indeterminate) — for raw `<input type="checkbox">`

### Atomic vs. per-item bulk semantics

The backend has to choose: process the batch as **one transaction**
(atomic) or **per-item** (some succeed, some fail).

**Atomic.** One database transaction wraps the entire batch. Any
failure → ROLLBACK → nothing changes. The HTTP response is binary:
200 (everything worked) or 4xx/5xx (nothing worked).

```sql
BEGIN;
DELETE FROM employees WHERE employeeNumber = 1;
DELETE FROM employees WHERE employeeNumber = 2;  -- if this fails,
DELETE FROM employees WHERE employeeNumber = 3;  -- so do these,
COMMIT;
```

**Per-item.** Each id is its own logical operation. Failures are
collected into a result envelope:

```java
for (int id : ids) {
    try { delete(id); successes++; }
    catch (Exception e) { failures.add(new Failure(id, e.getMessage())); }
}
return new BulkResult(requested, successes, failures.size(), failures);
```

The HTTP response is always 200; the body tells you what happened.

Choose **atomic** when:
- The operations are coupled ("transfer money": debit + credit must
  both succeed or neither).
- A failed item's effect on the others is meaningful (concurrent
  inventory updates).
- The user cares about all-or-nothing more than partial progress.

Choose **per-item** (almost everything else) when:
- Each row stands alone (delete users, soft-delete records).
- Partial success is recoverable (the user can fix bad rows and
  retry).
- "One bad row blocks 49 good ones" would be infuriating.

Our `bulkDelete` is per-item. The doc-comment on the service method
explains the choice in detail.

References:

- [Stripe API — Batch operations](https://docs.stripe.com/api/batch) — concrete real-world per-item example
- [GraphQL — Mutation responses](https://graphql.org/learn/mutations/) — per-field success/error, similar shape
- [Microsoft Patterns & Practices — Bulk operations](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/) — broader architectural context

### Result envelopes — the canonical shape

```json
{
  "requested": 10,
  "successCount": 7,
  "failureCount": 3,
  "failures": [
    { "id": 1188, "reason": "Cannot delete: still has dependent customers" },
    { "id": 1370, "reason": "Employee 1370 not found" },
    { "id": 1612, "reason": "DEEP_CASCADE is disabled in this environment" }
  ]
}
```

Three fields you almost always want:
- **`requested`** — how many ids the client asked about.
- **`successCount` / `failureCount`** — for headline display.
- **`failures: [{ id, reason }]`** — per-item explanations.

You'll also see fields like `success: true` (overall flag),
`successes: [...ids]` (echo back the ones that worked, useful for
the client to update its view without guessing), `partial: true`
(redundant but explicit). Pick the subset that matches your
frontend.

References:

- [JSON:API — Batch endpoint extension](https://jsonapi.org/extensions/atomic/) — atomic alternative; useful contrast
- [Apollo Federation — Errors with paths](https://www.apollographql.com/docs/federation/) — for per-item errors in GraphQL

### Updating selection after the response

When the bulk delete returns:

```ts
const failedIds = new Set(outcome.failures.map(f => f.id));
const next = new Set<number>();
for (const id of this.selection()) {
  if (failedIds.has(id)) next.add(id);    // keep the ones that failed
}
this.selection.set(next);
this.load();                              // refetch the page
```

The succeeded ids are dropped from the selection (they don't exist
anymore — keeping them selected would be misleading). The failed
ids remain selected, so the user can fix the underlying problem and
hit "Delete selected" again. If the backend echoed back the
succeeded ids in the response (an alternative API design), we'd use
that directly instead of computing "everything not in failures."

## The code, walked through

### Backend — the bulk delete service method

```java
@CacheEvict(cacheNames = {"employees", "employeesAll"}, allEntries = true)
public BulkOperationResultDTO bulkDelete(List<Integer> ids, DeleteStrategy strategy) {
    List<Integer> distinct = ids.stream().distinct().toList();

    int successCount = 0;
    var failures = new ArrayList<BulkOperationResultDTO.Failure>();
    for (Integer id : distinct) {
        try {
            delete(id, strategy);   // existing per-row delete, with broadcast
            successCount++;
        } catch (Exception e) {
            failures.add(new BulkOperationResultDTO.Failure(
                id, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }
    return new BulkOperationResultDTO(distinct.size(), successCount, failures.size(), failures);
}
```

Each iteration calls the existing single-row `delete(id, strategy)`
method, which already broadcasts a DELETED event. So the frontend's
event listeners receive N individual events (not one summary event)
— and the 250ms debounce we added in Feature 11 collapses them into
one redraw on the consumer side. No new event types needed.

### Backend — request DTO uses the existing strategy enum

```java
public record BulkDeleteRequestDTO(
        List<Integer> ids,
        DeleteStrategy strategy
) {}
```

Java records are perfect for request bodies. Jackson deserializes
them by parameter name; the strategy enum is bound from a JSON
string ("NULLIFY", "CASCADE", etc.) automatically. Total config: zero.

### Frontend — selection as a `Set` in a signal

```ts
selection = signal<Set<number>>(new Set<number>());

toggleRow(e: Employee): void {
  if (e.employeeNumber == null) return;
  const next = new Set(this.selection());     // copy the Set
  if (next.has(e.employeeNumber)) next.delete(e.employeeNumber);
  else                            next.add(e.employeeNumber);
  this.selection.set(next);                   // assign new reference
}
```

Why copy-on-write rather than mutating in place? Signals fire on
reference change. Mutating the existing Set wouldn't trigger
template updates. The same rule applies in React state and most
state libraries — immutable updates are how reactivity finds out
something changed.

### Frontend — computed tri-state for the header checkbox

```ts
pageSelectionState = computed<'none' | 'some' | 'all'>(() => {
  const visible = this.employees();
  if (visible.length === 0) return 'none';
  const sel = this.selection();
  let count = 0;
  for (const e of visible) {
    if (e.employeeNumber != null && sel.has(e.employeeNumber)) count++;
  }
  if (count === 0) return 'none';
  if (count === visible.length) return 'all';
  return 'some';
});
```

`computed` is Angular's signal-derived signal: re-evaluates only
when its inputs (`employees()`, `selection()`) change. The template
binds:

```html
<mat-checkbox
  [checked]="pageSelectionState() === 'all'"
  [indeterminate]="pageSelectionState() === 'some'"
  (change)="togglePage()">
</mat-checkbox>
```

Three tri-state behaviors covered by two boolean attributes. Note
that `indeterminate` is purely visual — the underlying input is
either checked or not.

### Frontend — type-to-confirm guard

```ts
canSubmit(): boolean {
  if (!this.strategy) return false;
  if (this.isDestructive()) return this.confirmation.trim() === this.confirmPhrase();
  return true;
}
```

For non-SOFT strategies, the user must type the exact phrase
(`DELETE 7` for 7 selected) before the button enables. Same idea
as GitHub asking for the repo name before deletion: it forces a
moment of conscious confirmation before an irreversible action.

### Frontend — surface the per-item failures

```ts
this.dialog.open(EmployeeBulkResultDialogComponent, {
  data: outcome,
  width: '520px',
});
```

The result dialog reads the envelope and shows:
- An icon based on shape (✓ all / ⚠ partial / ✗ all failed).
- A summary line ("3 of 5 deleted successfully (2 failures)").
- A scrollable list of `#1188: Cannot delete: still has dependent customers`.

The user can copy reasons, close the dialog, fix them in another
tab, come back, and bulk-delete again. The selection still has the
failed ids — see "Updating selection after the response" above.

## How to test

1. Reload the UI; restart the backend (new endpoint).
2. Go to **/employees**.
3. Tick a few rows individually. The blue bulk-bar appears at the
   top with the count.
4. Tick the **header checkbox** — every row on the current page
   joins the selection.
5. Move to **page 2** and tick a row there. Move back to page 1.
   Your earlier selections are still there. (The Set survives the
   refetch.)
6. Click **Delete selected**. The dialog shows the count + strategy
   picker. Try **CASCADE** — for non-SOFT strategies the type-to-
   confirm box appears. Type `DELETE N` (where N is your count) and
   the button enables.
7. Click Delete.
8. The result dialog shows successes and failures. Failures stay
   selected so you can retry.
9. Open another browser tab on `/employees` while the bulk runs —
   you'll see N individual rows disappear via the live-update
   plumbing from Feature 10.

To verify the backend:

```sh
curl -X POST -H "Authorization: Bearer <jwt>" \
     -H "Content-Type: application/json" \
     -d '{"ids":[1188,1216,1286],"strategy":"NULLIFY"}' \
     http://localhost:9090/api/v1/employees/bulk-delete
```

You'll get a JSON envelope back, even if some ids were already deleted.

## What you just learned

- **Sets vs. row-flags** as the right abstraction for selection
  state, especially with pagination.
- **`signal<Set<...>>` with copy-on-write** as the idiomatic way to
  do reactive Set state in Angular signals.
- **`computed()` for derived UI state** like the tri-state checkbox.
- **Tri-state Material checkbox** via `[checked]` + `[indeterminate]`.
- **Atomic vs. per-item bulk semantics** and when each is right.
- **Result-envelope shape** (`requested / successCount / failureCount
  / failures`) as a portable pattern for any bulk endpoint.
- **Type-to-confirm** as a guardrail for destructive bulk actions.
- **Reusing per-row logic in bulk operations** — the bulk service
  loops over the existing `delete(id, strategy)` and benefits from
  every per-row safety net (cache eviction, live event broadcast,
  audit logging).

## Study materials

### Selection patterns and grid components

- [TanStack Table — Row Selection](https://tanstack.com/table/v8/docs/api/features/row-selection) — id-set model, framework-agnostic
- [Angular Material — Selection model docs](https://material.angular.io/cdk/collections/overview#selectionmodel) — `SelectionModel<T>` is the alternative if you don't want to roll your own Set
- [AG Grid — Selection](https://www.ag-grid.com/javascript-data-grid/row-selection/) — the heavyweight option; checkboxes, ranges, copy/paste

### Tri-state checkboxes

- [Angular Material — `MatCheckbox` indeterminate](https://material.angular.io/components/checkbox/overview#indeterminate-state)
- [WAI-ARIA — `aria-checked="mixed"`](https://www.w3.org/WAI/ARIA/apg/patterns/checkbox/examples/checkbox-mixed/) — accessibility for tri-state

### Bulk APIs and partial-success patterns

- [Stripe — Batch endpoints](https://docs.stripe.com/api/batch)
- [Microsoft Graph — Batching](https://learn.microsoft.com/en-us/graph/json-batching) — Microsoft Graph's batch model
- [GitHub — REST API best practices](https://docs.github.com/en/rest/overview/api-versions) — bulk + idempotency notes
- [JSON:API — Atomic Operations](https://jsonapi.org/extensions/atomic/) — the alternative atomic model
- [REST API Cookbook — Idempotency keys](https://stripe.com/docs/api/idempotent_requests) — for safe retries on bulk operations

### Transactional patterns

- [PostgreSQL — Transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html)
- [Martin Fowler — Patterns of Enterprise Application Architecture](https://martinfowler.com/eaaCatalog/) — Unit of Work, Identity Map; foundational
- [Spring docs — `@Transactional` semantics](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)

### Angular signals

- [Angular — Signals overview](https://angular.dev/guide/signals)
- [Angular — `computed` signals](https://angular.dev/guide/signals#computed-signals)
- [Angular — Signal effects](https://angular.dev/guide/signals#effect) — when reactivity needs side effects

### Confirmation UX

- [GitHub — Settings → "Type the repo name to confirm"](https://docs.github.com/en/repositories/creating-and-managing-repositories/deleting-a-repository) — the original
- [Material Design — Confirmation patterns](https://m2.material.io/components/dialogs#confirmation-dialog)
- [Nielsen Norman — Destructive actions UX](https://www.nngroup.com/articles/confirmation-dialog/)
