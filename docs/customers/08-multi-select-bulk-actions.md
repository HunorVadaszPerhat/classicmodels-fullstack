# Feature C8 — Multi-select + bulk actions

## What we built

The customer list now has a checkbox column. Tick rows individually,
or use the header checkbox to select every row on the current page.
Selections persist across pagination (move to page 2 and your page-1
selections are still there). When you have anything selected, a blue
"bulk action" bar appears above the table showing the count and a
**Delete selected** button.

Clicking it opens a confirmation dialog: pick the strategy
(SOFT or, if the feature flag is on, DEEP_CASCADE), type-to-confirm
for the destructive option, hit Delete. The frontend sends one
request to a new `POST /customers/bulk-delete` endpoint; the backend
processes each id independently and returns a result envelope. A
summary dialog shows "N succeeded, M failed" with per-id failure
reasons, so the user can fix and retry whatever didn't work.

This is the customer-side application of F14 (employees got it
first). The customer-specific bits are:

- The strategy menu has only **two** options (SOFT, DEEP_CASCADE)
  because NULLIFY/CASCADE/REASSIGN_DELETE don't apply to customers
  — same constraint that drove C6's design.
- The default strategy is **SOFT** (vs. NULLIFY for employees),
  because for customers the safe choice is preserving order +
  payment history.

Files touched:

- `classicmodels-backend/src/main/java/.../dto/customer/CustomerBulkDeleteRequestDTO.java` (new)
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerBulkOperationResultDTO.java` (new)
- `classicmodels-backend/src/main/java/.../service/CustomerService.java` — adds `bulkDelete()`
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java` — new endpoint
- `classicmodels-ui/src/app/customers/customer.service.ts` — adds `bulkDelete()` + `BulkOperationResult`
- `classicmodels-ui/src/app/customers/customer-bulk-delete-dialog.component.ts` (new)
- `classicmodels-ui/src/app/customers/customer-bulk-result-dialog.component.ts` (new)
- `classicmodels-ui/src/app/customers/customer-list.component.ts` — selection state, bulk-delete handler
- `classicmodels-ui/src/app/customers/customer-list.component.html` — checkbox column, bulk-bar

## Why this is worth learning

Bulk operations are the unglamorous workhorse of admin UIs. A
"delete one row" workflow is straightforward; a "delete fifty rows"
workflow has to grapple with three things at once.

**Selection as state.** Tracking which rows are selected through
pagination, sorting, and live data refreshes is a small but real
state-management problem. The `Set<id>` backed by a signal is the
right abstraction; a `selected` boolean on each Customer object is
the wrong one — see "id-set vs row-flag" below.

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

This is your second pass through these patterns. The new lesson,
specific to Customer, is that **the same UX shape can apply with a
narrower strategy menu** without complicating the dialog component
— it just renders the available options, whatever they are.

## Background

### Selection state — id-set vs. row-flag

Two ways to track "which rows are selected":

**Per-row `selected: boolean` flag** — extend the `Customer` shape.
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
library you've heard of (AG Grid, MUI DataGrid, TanStack Table) does,
for the same reasons.

References:

- [MDN — `Set`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set)
- [TanStack Table — Row Selection](https://tanstack.com/table/v8/docs/api/features/row-selection) — same id-set model, well-documented

### Signals + Set: why we copy on every mutation

Signals fire when their stored *reference* changes. Mutating the
Set in place (`this.selection().add(id)`) doesn't change the
reference, so subscribers don't fire and the template doesn't
re-render. The fix:

```ts
toggleRow(c: Customer): void {
  if (c.customerNumber == null) return;
  const next = new Set(this.selection());   // copy
  if (next.has(c.customerNumber)) next.delete(c.customerNumber);
  else next.add(c.customerNumber);
  this.selection.set(next);                  // new reference
}
```

The copy is `O(n)` on selection size — for n ≤ a few hundred,
imperceptible. For very large selections (thousands), an immutable
Set library (Immer, Immutable.js) would be the right answer.

Reference: [Angular — Signals reactivity](https://angular.dev/guide/signals#reactivity)

### Tri-state checkboxes

A tri-state checkbox has three visual states: checked, unchecked,
and **indeterminate** (a horizontal line, signalling "neither but
something").

```html
<mat-checkbox
  [checked]="pageSelectionState() === 'all'"
  [indeterminate]="pageSelectionState() === 'some'"
  (change)="togglePage()">
</mat-checkbox>
```

The state is computed from the data:

```ts
pageSelectionState = computed<'none' | 'some' | 'all'>(() => {
  const visible = this.customers();
  if (visible.length === 0) return 'none';
  const sel = this.selection();
  let count = 0;
  for (const c of visible) {
    if (c.customerNumber != null && sel.has(c.customerNumber)) count++;
  }
  if (count === 0) return 'none';
  if (count === visible.length) return 'all';
  return 'some';
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
- [HTML spec — `indeterminate`](https://html.spec.whatwg.org/multipage/input.html#the-input-element:dom-input-indeterminate)

### Atomic vs. per-item bulk semantics

The backend has to choose: process the batch as **one transaction**
(atomic) or **per-item** (some succeed, some fail).

**Atomic.** One database transaction wraps the entire batch. Any
failure → ROLLBACK → nothing changes. The HTTP response is binary:
200 (everything worked) or 4xx/5xx (nothing worked).

**Per-item** (what we use). Each id is its own logical operation.
Failures are collected into a result envelope:

```java
for (Integer id : distinct) {
    try {
        delete(id, strategy);
        successCount++;
    } catch (Exception e) {
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        failures.add(new CustomerBulkOperationResultDTO.Failure(id, reason));
    }
}
```

The trade-off:

- **Atomic** — cleaner semantics; one bad row blocks the entire
  batch. Right for "transfer money between accounts" — partial
  success would corrupt invariants.
- **Per-item** — messier semantics; admins can make progress when
  one row is broken. Right for "clean out 50 dormant customers" —
  one stuck row shouldn't waste the whole effort.

For UI-driven bulk operations on small sets (10s of rows), per-item
is almost always the right choice. The frontend reads the envelope
and shows a "5 succeeded, 2 failed" summary that lets the user fix
and retry the failures.

References:

- [Stripe — API design: bulk operations](https://stripe.com/blog/idempotency)
- [Microsoft REST guidelines — Long-running ops](https://github.com/microsoft/api-guidelines/blob/vNext/Guidelines.md#long-running-operations)

### The customer strategy menu is shorter

For customers, only SOFT and DEEP_CASCADE are valid. NULLIFY,
CASCADE, REASSIGN_DELETE don't apply (see C6 for the FK-shape
explanation). The dialog renders whatever's in
`data.availableStrategies`, so:

- With `app.delete.allow-deep-cascade=false`, the dropdown shows
  only SOFT.
- With the flag on, the dropdown shows SOFT and DEEP_CASCADE.

The dialog component itself is unaware of how many options exist —
it just iterates. No code branching on "is this an employee or a
customer?", just "render the list you were given."

Default is SOFT. Differs from the employee bulk dialog (which
defaults to NULLIFY) because for customers the safe choice is to
preserve orders and payments.

### Type-to-confirm for destructive strategies

```ts
canSubmit(): boolean {
  if (!this.strategy) return false;
  if (this.isDestructive()) return this.confirmation.trim() === this.confirmPhrase();
  return true;
}

confirmPhrase(): string {
  return `DELETE ${this.data.selectedIds.length}`;
}
```

The confirm phrase includes the count — typing "DELETE 47" is
deliberately less likely to be muscle memory than typing
"DELETE EVERYTHING". The number forces the user to look at how
many rows they're about to obliterate.

Same destructive-confirmation discipline as the C6 single-row dialog,
extended with the count for bulk.

### Live events during bulk operations

Each successful delete inside the loop calls `delete(id, strategy)`,
which broadcasts a `DELETED` event on `/topic/customers`. So a bulk
delete of 47 customers broadcasts 47 events. The frontend's
subscription calls `load()` for each — RxJS doesn't naturally
deduplicate these into one refresh.

In practice this doesn't cause visible problems because:
- The browser's event loop coalesces multiple `load()` calls happening
  in close succession into one rendered frame.
- The HTTP cache (Spring's `@CacheEvict` on the bulk method) means
  the first `load()` after the bulk completes hits the cache for
  the others (briefly), and they're effectively no-ops at the DB
  level.

If a use case ever genuinely needs "one event per bulk operation,"
the right pattern is to skip per-item events from inside `bulkDelete()`
and emit a single "BULK_DELETED" event at the end. For our current
scale (hundreds of rows max) the per-item events are fine.

## The code, walked through

### Service-layer bulk-delete loop

```java
@CacheEvict(cacheNames = {"customers", "customersAll", "customersPaged"}, allEntries = true)
public CustomerBulkOperationResultDTO bulkDelete(List<Integer> ids, DeleteStrategy strategy) {
    if (ids == null || ids.isEmpty()) {
        return new CustomerBulkOperationResultDTO(0, 0, 0, List.of());
    }

    List<Integer> distinct = ids.stream().distinct().toList();

    int successCount = 0;
    var failures = new ArrayList<CustomerBulkOperationResultDTO.Failure>();
    for (Integer id : distinct) {
        try {
            delete(id, strategy);
            successCount++;
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            failures.add(new CustomerBulkOperationResultDTO.Failure(id, reason));
        }
    }

    return new CustomerBulkOperationResultDTO(distinct.size(), successCount, failures.size(), failures);
}
```

`distinct()` defends against client-side double-counting bugs.
`delete(id, strategy)` is the same single-row method we wrote in
C6 — bulk just loops over it, so the strategy semantics, the
feature-flag gate, and the live-event broadcast all apply
unchanged. One implementation, one place to fix bugs.

### Controller endpoint

```java
@PostMapping("/bulk-delete")
public ResponseEntity<CustomerBulkOperationResultDTO> bulkDelete(
        @RequestBody CustomerBulkDeleteRequestDTO request) {
    DeleteStrategy strategy = request.strategy() != null
            ? request.strategy()
            : DeleteStrategy.SOFT;
    return ResponseEntity.ok(service.bulkDelete(request.ids(), strategy));
}
```

`POST` rather than `DELETE` because the request has a body (DELETE
with a body works in spec but many clients/proxies misbehave). The
endpoint always returns 200 — partial-success is a normal outcome,
the client reads the envelope.

### Frontend bulk handler with cancel-aware nesting

```ts
bulkDelete(): void {
  const ids = Array.from(this.selection());
  if (ids.length === 0) return;

  this.service.getAvailableStrategies().subscribe(availableStrategies => {
    const ref = this.dialog.open(CustomerBulkDeleteDialogComponent, {
      data: { selectedIds: ids, availableStrategies },
      width: '520px',
    });

    ref.afterClosed().subscribe(result => {
      if (!result) return;  // user cancelled

      this.service.bulkDelete(ids, result.strategy).subscribe({
        next: outcome => {
          this.dialog.open(CustomerBulkResultDialogComponent, {
            data: outcome,
            width: '520px',
          });
          // Drop only the SUCCEEDED ids from the selection.
          // Failures stay selected so the user can retry.
          const failedIds = new Set(outcome.failures.map(f => f.id));
          const next = new Set<number>();
          for (const id of this.selection()) {
            if (failedIds.has(id)) next.add(id);
          }
          this.selection.set(next);
          this.load();
        },
        error: err => this.dialog.open(ErrorDialogComponent, ...),
      });
    });
  });
}
```

The "keep failures selected" detail matters. After a partial-success
batch, the user wants to act on the failures — usually retry with a
different strategy, or click each one to investigate. If we cleared
the whole selection, they'd have to re-tick the failed ones from a
result list that doesn't even know what they look like. Keeping the
failures selected is the natural recovery affordance.

The backend doesn't return the SUCCEEDED ids, only failures, so we
infer "succeeded = current selection MINUS failures." If the
backend ever changes shape to also return successes, this becomes a
trivial cleanup.

## How to test

### Single-page selection + bulk soft-delete

1. Navigate to **Customers**. Tick three rows.
2. Bulk-bar appears: "3 selected · Delete selected · Clear".
3. Click Delete selected. Dialog opens: "Bulk delete 3 customer(s)".
4. Strategy is SOFT by default. No confirmation typing required.
5. Click Delete 3.
6. Result dialog: "3 of 3 deleted successfully" with green checkmark.
7. List refreshes; the three rows are gone (soft-deleted; visit
   their detail pages directly to verify the Inactive chip).

### Multi-page selection survival

1. Tick 2 rows on page 1.
2. Click Next → page 2. Bulk-bar still says "2 selected".
3. Tick 1 row on page 2. Bulk-bar says "3 selected".
4. Click Prev → page 1. The page-1 selections are still ticked.
5. Run bulk-delete; all 3 rows from across the two pages get deleted.

### Tri-state header checkbox

1. Page is empty selection. Header checkbox: unchecked.
2. Tick one row. Header checkbox: indeterminate (horizontal bar).
3. Tick all rows on the page individually. Header checkbox: checked.
4. Click header. All page rows deselect.
5. Click header again. All page rows select. (The "select all" branch
   from "indeterminate" — verify by going back to step 2 and clicking
   the header instead of more rows.)

### DEEP_CASCADE bulk delete (flag on)

1. Set `app.delete.allow-deep-cascade=true`, restart backend.
2. Select 2 customers.
3. Open the bulk dialog. Strategy dropdown now shows
   "DEEP CASCADE — delete customers + every order, order detail,
   and payment".
4. Pick it. The confirm phrase appears: "Type `DELETE 2` to enable
   the delete button."
5. Type "DELETE 2" → button enables. Click.
6. The customers, their orders, their order details, their payments
   are all gone. Result dialog confirms.

### Partial-failure path

The easiest way to force a failure: pick a customer-id that doesn't
exist (e.g. via direct API):

```bash
curl -X POST -H "Content-Type: application/json" \
     -H "Authorization: Bearer $JWT" \
     -d '{"ids":[103,99999,112],"strategy":"SOFT"}' \
     'http://localhost:9090/api/v1/customers/bulk-delete'
```

Response should be:

```json
{
  "requested": 3,
  "successCount": 2,
  "failureCount": 1,
  "failures": [
    {"id": 99999, "reason": "Customer 99999 not found"}
  ]
}
```

### Live updates from bulk

Open two browser windows on the customer list. Bulk-delete in
window A. Window B's list refreshes — the deleted rows are gone.
(Each per-item DELETED event triggers a `load()`; the browser
collapses multiple loads into one rendered frame.)

## What you just learned

- **Selection-state as `Set<id>` in a signal** — survives pagination,
  sorting, and refetches; the right abstraction beyond a few rows.
- **Copy-on-mutate for signal-wrapped Sets** — signals fire on
  reference change, not in-place mutation.
- **Tri-state checkboxes via `MatCheckbox.indeterminate`** — and
  the design choice of "page" vs "whole dataset" for the "all" set.
- **Per-item vs atomic bulk semantics** — the result envelope
  pattern (`successCount`, `failureCount`, `failures: [...]`) as
  the wire format for partial success.
- **Reusing the single-row delete inside the bulk loop** — strategy
  semantics, feature gating, and live events all flow through one
  code path.
- **Keeping failed selections selected** as the natural retry
  affordance after a partial-success batch.

## Study materials

### Selection / row state

- [TanStack Table — Row Selection](https://tanstack.com/table/v8/docs/api/features/row-selection)
- [AG Grid — Row Selection](https://www.ag-grid.com/javascript-data-grid/row-selection/) — opinionated, well-tested patterns
- [MDN — `Set`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set)

### Tri-state UI patterns

- [Material — Checkbox indeterminate state](https://material.angular.io/components/checkbox/overview#indeterminate-state)
- [Nielsen Norman Group — Tri-state checkboxes](https://www.nngroup.com/articles/tri-state-checkboxes/)

### Bulk operation API design

- [Stripe blog — Idempotency in API design](https://stripe.com/blog/idempotency)
- [Google Cloud — Bulk write best practices](https://cloud.google.com/firestore/docs/bulk-data-entry)
- [Microsoft REST guidelines — Bulk operations](https://github.com/microsoft/api-guidelines/blob/vNext/Guidelines.md)

### Angular Material dialog patterns

- [Angular Material — MatDialog overview](https://material.angular.io/components/dialog/overview)
- [Angular Material — Dialog with data + result](https://material.angular.io/components/dialog/examples)
