# Feature C3 — Rich customer form with validation + sales-rep dropdown

## What we built

The customer create / edit form used to be a flat stack of plain
`<mat-form-field>` inputs with no inline validation messages, no
groupings, and a raw `<input type="number">` for the sales-rep
employee number. We rebuilt it as a card-based form with four
labelled sections — Identity, Contact, Address, Account — inline
validation that mirrors the database constraints, and a real
dropdown for the sales rep that shows the employee's name and job
title.

While we were in there we removed the bogus "Customer Number" field.
Customer numbers are auto-incremented by MySQL on insert; the form
asked the user to type one in and the backend silently discarded the
value. Now create mode just collects the meaningful data and lets the
DB assign the id. Edit mode shows the existing number in the card
subtitle for reference.

This is the customer-side application of the form pattern the
employee-form rewrite established. The two forms are now structurally
parallel — same layout primitives (`section`, `.row`, `.row.single`),
same loading / saving / error signals, same validator placement, same
dropdown idiom with a "— None" / "— Unassigned" option at the top.

Files touched:

- `classicmodels-ui/src/app/customers/customer-form.component.ts`
- `classicmodels-ui/src/app/customers/customer-form.component.html`

## Why this is worth learning

Reactive forms are the single biggest reason Angular applications
feel professional or feel sloppy. The patterns this feature exercises
— **mirroring DB constraints in `Validators`**, **inline error
messages on touched controls**, **`forkJoin` for parallel reference-
data loading**, **`computed()` over reference data for sorted /
filtered dropdowns**, **`markAllAsTouched()` to surface every error
on a blocked submit**, and **disabling the submit button on
`form.invalid || saving()`** — are the building blocks for every
form you'll write in this codebase from here on.

Most of these patterns recurred from the employee-form rewrite. The
new bit specific to Customer is the **`mat-select` dropdown over a
foreign-key field with a "— Unassigned" null option** — required
because the customer's `salesRepEmployeeNumber` is genuinely nullable
(unassigned customers exist) where the employee form's `officeCode`
was required.

## Background

### Reactive forms vs template-driven forms

Angular has two form APIs: template-driven (`ngModel` + simple
validators in the template) and reactive (`FormBuilder` +
`FormGroup` + validators in TypeScript). For anything beyond the
simplest input, reactive is the right choice:

```ts
form = this.fb.group({
  customerName: ['', [Validators.required, Validators.maxLength(50)]],
  ...
});
```

The form's structure, validators, default values, and submit logic
all live in the component class. The template just binds via
`formControlName="..."` and renders error messages. The class is
testable in isolation; the template is a thin view over it.

References:

- [Angular — Reactive forms](https://angular.dev/guide/forms/reactive-forms)
- [Angular — Form validation](https://angular.dev/guide/forms/form-validation)

### Mirroring DB constraints in `Validators`

Every server-side constraint should have a client-side echo. The DB
column `customerName VARCHAR(50) NOT NULL` becomes:

```ts
customerName: ['', [Validators.required, Validators.maxLength(50)]],
```

Why duplicate? Three reasons:

1. **Latency** — client-side validation surfaces errors instantly.
   Round-tripping to the server for "this is required" is wasteful
   for the user and noisy for the backend.
2. **UX** — `mat-error` rendered next to the field is more
   discoverable than a banner reading "Constraint violation:
   customerName_NotNull".
3. **Correctness floor** — client validators are advisory; the
   backend is authoritative. Never *only* validate on the client (a
   forged HTTP request bypasses the form entirely). But client
   validation should match the backend's, character for character,
   so the contract is consistent.

The customer table is small enough that we can read the column types
straight from the schema and translate them line-by-line:

| Column | DB constraint | Validator |
|---|---|---|
| customerName | VARCHAR(50) NOT NULL | required, maxLength(50) |
| contactFirstName | VARCHAR(50) NOT NULL | required, maxLength(50) |
| contactLastName | VARCHAR(50) NOT NULL | required, maxLength(50) |
| phone | VARCHAR(50) NOT NULL | required, maxLength(50) |
| addressLine1 | VARCHAR(50) NOT NULL | required, maxLength(50) |
| addressLine2 | VARCHAR(50) NULL | maxLength(50) |
| city | VARCHAR(50) NOT NULL | required, maxLength(50) |
| state | VARCHAR(50) NULL | maxLength(50) |
| postalCode | VARCHAR(15) NULL | maxLength(15) |
| country | VARCHAR(50) NOT NULL | required, maxLength(50) |
| salesRepEmployeeNumber | INT NULL FK | (none — dropdown) |
| creditLimit | DECIMAL(10,2) NULL | min(0) |

The DB row constraints don't translate one-for-one, though. We
deliberately *don't* enforce the DECIMAL(10,2) upper bound (99,999,999.99)
client-side: hitting it is almost always a typo, and a "max value"
error message is more confusing than a backend rejection.

References:

- [Angular — Built-in validators](https://angular.dev/api/forms/Validators)
- [MDN — HTML form validation attributes](https://developer.mozilla.org/en-US/docs/Web/HTML/Constraint_validation)

### Inline errors on **touched** controls

Reactive forms expose three "interaction state" flags per control:
`pristine` / `dirty` (has the user edited it?), `touched` /
`untouched` (has the user focused-then-blurred it?), and `valid` /
`invalid` (does the value pass validators?). The convention is to
show errors only after `touched` — otherwise the form lights up red
the instant it loads, which is hostile.

```html
@if (form.controls.customerName.touched && form.controls.customerName.errors?.['required']) {
  <mat-error>Customer name is required</mat-error>
}
```

Each `@if` checks a *specific* error key — `required`, `maxlength`,
`min`, `email`, etc. — so the message can be tailored to the failure
mode. A control can have multiple validators failing simultaneously;
showing all of them is overwhelming, so the convention is to render
in order of specificity (required first, then format, then range).

**Important angular quirk**: validator names are sometimes lowercase
(`maxlength`, not `maxLength`) when read from the `errors` object —
that's the HTML attribute name, not the TypeScript validator name.
Angular's `Validators.maxLength(...)` registers under the key
`maxlength`. Get this wrong and your error block silently never
renders.

Reference: [Angular — ValidationErrors keys](https://angular.dev/api/forms/Validators)

### `markAllAsTouched()` to force errors on submit

Without intervention, a user who clicks Submit on a fresh form sees
nothing happen — the controls aren't `touched`, so no errors render,
even though the form is invalid. The fix:

```ts
save() {
  if (this.form.invalid) {
    this.form.markAllAsTouched();
    return;
  }
  ...
}
```

`markAllAsTouched()` recursively touches every descendant control,
which causes every error block to render in one shot. The user sees
exactly what's missing. This is the standard "tell me everything
that's wrong" submit behaviour for a form that hasn't been used yet.

Reference: [Angular — AbstractControl.markAllAsTouched](https://angular.dev/api/forms/AbstractControl#markAllAsTouched)

### `forkJoin` for parallel reference-data loading

A form that has dropdowns sourced from the API needs to load the
dropdown data before it can render. In edit mode it also needs to
load the entity being edited. Both can fire in parallel:

```ts
forkJoin({
  employees: this.employees.list(),
  ...(this.isEdit() && this.id() != null
      ? { customer: this.customers.get(this.id()!) }
      : {}),
}).subscribe({
  next: r => {
    this.allEmployees.set(r.employees);
    if (r.customer) this.form.patchValue(r.customer);
    this.loading.set(false);
  },
  error: ...
});
```

Two things to notice. The conditional spread `...(condition ? { customer: ... } : {})`
adds a request to the forkJoin only in edit mode — the create flow
doesn't need to fetch a non-existent customer. And `patchValue()` is
the right tool for "fill in what you have" — it ignores keys that
don't exist on the form (so the customer's `customerNumber`, which
isn't in the form anymore, just gets dropped silently).

References:

- [RxJS forkJoin](https://rxjs.dev/api/index/function/forkJoin)
- [Angular — `patchValue` vs `setValue`](https://angular.dev/api/forms/FormGroup#patchValue) — `patchValue` is forgiving about missing keys; `setValue` is strict

### `computed()` over reference data for the dropdown

The sales-rep dropdown options aren't the raw employee list —
they're the *active* employees, sorted by name. Both transformations
should be derived, not stored, so a future update to the underlying
list flows through automatically:

```ts
salesReps = computed(() =>
  this.allEmployees()
    .filter(e => e.active !== false)
    .sort((a, b) =>
      (a.lastName + a.firstName).localeCompare(b.lastName + b.firstName))
);
```

This is the same `computed()` pattern from C1's `addressLines`,
applied to a different shape of derivation. `computed` recomputes
lazily (only when read) and memoises (only when its inputs change),
which means the sort runs once per "list-of-employees changes," not
once per render.

References:

- [Angular — `computed()` signals](https://angular.dev/guide/signals#computed-signals)
- [MDN — `Array.prototype.sort`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/sort)
- [MDN — `String.prototype.localeCompare`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/localeCompare)

### Nullable FKs and the "— Unassigned" option

The customer's `salesRepEmployeeNumber` is nullable — there are
customers with no assigned rep. A `<mat-select>` whose value is
`null` doesn't render a placeholder by default; the user has no way
to *choose* "no rep" once they've picked one. The fix is an explicit
option at the top of the list:

```html
<mat-select formControlName="salesRepEmployeeNumber">
  <mat-option [value]="null">— Unassigned</mat-option>
  @for (rep of salesReps(); track rep.employeeNumber) {
    <mat-option [value]="rep.employeeNumber">
      {{ rep.lastName }}, {{ rep.firstName }}
    </mat-option>
  }
</mat-select>
```

The em-dash prefix is a small typographic cue that this is a
"meta" option, not a real value. Same pattern as the employee
form's "— None (top of org chart)" entry for `reportsTo`.

Reference: [Angular Material — MatSelect](https://material.angular.io/components/select/overview)

### Disabling submit on invalid + saving

```html
<button mat-flat-button color="primary"
        type="button"
        [disabled]="form.invalid || saving()"
        (click)="save()">
```

Two-condition disable:

- `form.invalid` — don't let the user submit a form they haven't
  finished. (We *also* call `markAllAsTouched()` in `save()` for the
  case where the user mashes Enter.)
- `saving()` — once the request is in flight, don't let them
  double-submit. The button shows an inline spinner while disabled,
  so it's visually obvious why nothing's happening.

This is the cheapest way to prevent the most common form failure
mode (duplicate POSTs from a frustrated user clicking twice).

### Why the submit button is `type="button"`

Subtle but important. A `<button>` inside a `<form>` defaults to
`type="submit"`, which means pressing Enter in any input fires the
form's submit. We *want* that — but the button is also bound to
`(click)="save()"`. If both fire, `save()` runs twice. Setting
`type="button"` makes the click handler the only path. The form's
`(ngSubmit)="save()"` still catches Enter-key submits.

If you want full keyboard parity (Enter in any input submits) and
mouse parity (clicking the button submits), this is the safe combo.

Reference: [MDN — `<button type>` attribute](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/button#type)

### Why we removed `customerNumber` from the form

The `customers` table has `customerNumber INT AUTO_INCREMENT`. On
insert, MySQL assigns the next id automatically and the JDBC repo
reads it back via `RETURN_GENERATED_KEYS`. The previous form asked
the user to type a number — this number was sent to the backend and
*silently discarded*. Worse, the form marked it `required`, so users
couldn't submit without typing in something that did nothing.

There are two valid handlings of an auto-incremented PK on a create
form: (a) hide it entirely, let the DB assign it; (b) show it as
read-only on edit mode for reference. We do both — no field on
create, card subtitle "#103" on edit.

References:

- [MySQL — AUTO_INCREMENT](https://dev.mysql.com/doc/refman/8.0/en/example-auto-increment.html)
- [JDBC — `RETURN_GENERATED_KEYS`](https://docs.oracle.com/javase/tutorial/jdbc/basics/retrieving.html)

### Autocomplete attributes on inputs

Every `<input>` in the form carries an `autocomplete="..."` attribute
(`given-name`, `family-name`, `tel`, `address-line1`, `address-level1`,
`postal-code`, `country-name`). Browsers use these to offer
autocompletion from saved data — typing one customer's contact info
becomes mostly two clicks. They're the cheapest UX win in form
building, and most code doesn't bother. Always add them.

Reference: [MDN — `autocomplete` attribute values](https://developer.mozilla.org/en-US/docs/Web/HTML/Attributes/autocomplete)

## The code, walked through

### Form definition with mixed-required validators

```ts
form = this.fb.group({
  customerName:     ['', [Validators.required, Validators.maxLength(50)]],
  contactFirstName: ['', [Validators.required, Validators.maxLength(50)]],
  contactLastName:  ['', [Validators.required, Validators.maxLength(50)]],
  phone:            ['', [Validators.required, Validators.maxLength(50)]],
  addressLine1:     ['', [Validators.required, Validators.maxLength(50)]],
  addressLine2:     ['',  Validators.maxLength(50)],
  city:             ['', [Validators.required, Validators.maxLength(50)]],
  state:            ['',  Validators.maxLength(50)],
  postalCode:       ['',  Validators.maxLength(15)],
  country:          ['', [Validators.required, Validators.maxLength(50)]],
  salesRepEmployeeNumber: [null as number | null],
  creditLimit:            [null as number | null, Validators.min(0)],
});
```

Reading top-to-bottom: every required string column gets the same
two validators. Optional columns lose `Validators.required` but keep
`maxLength`. The two FK / numeric columns sit at the bottom with
their own bespoke handling — `salesRepEmployeeNumber` has no
validators because nullability is fine, `creditLimit` gets a `min(0)`.

The `null as number | null` type cast on the numeric columns is
needed because TypeScript would otherwise infer the field as
`number`, and we want the union `number | null` to allow the form
to clear back to null.

### Sales-rep dropdown wired to a `computed()`

```ts
private allEmployees = signal<Employee[]>([]);

salesReps = computed(() =>
  this.allEmployees()
    .filter(e => e.active !== false)
    .sort((a, b) =>
      (a.lastName + a.firstName).localeCompare(b.lastName + b.firstName))
);
```

```html
<mat-select formControlName="salesRepEmployeeNumber">
  <mat-option [value]="null">— Unassigned</mat-option>
  @for (rep of salesReps(); track rep.employeeNumber) {
    <mat-option [value]="rep.employeeNumber">
      {{ rep.lastName }}, {{ rep.firstName }}
      <span style="color: rgba(0,0,0,0.45)">— {{ rep.jobTitle }}</span>
    </mat-option>
  }
</mat-select>
```

The `track rep.employeeNumber` is Angular's "stable identity" hint
— without it, scrolling or filtering the list re-renders every option
because Angular can't tell which is which. With it, only changed
options re-render.

The grey-tinted `jobTitle` is a small disambiguation when two reps
share a last name. "Patterson, William — Sales Manager (NA)" vs
"Patterson, Steve — Sales Rep" reads cleanly even at a glance.

### Submit handler with form-state guard

```ts
save() {
  if (this.form.invalid) {
    this.form.markAllAsTouched();
    return;
  }

  this.saving.set(true);
  this.error.set(undefined);

  const raw = this.form.value;
  const value: Customer = {
    customerNumber: this.id() ?? 0, // ignored on create, present on edit
    customerName: raw.customerName!,
    ...
    addressLine2: raw.addressLine2 || undefined,
    state: raw.state || undefined,
    postalCode: raw.postalCode || undefined,
    salesRepEmployeeNumber: raw.salesRepEmployeeNumber ?? undefined,
    creditLimit: raw.creditLimit ?? undefined,
  };

  const obs = this.isEdit()
    ? this.customers.update(this.id()!, value)
    : this.customers.create(value);

  obs.subscribe({
    next: () => { ... navigate back ... },
    error: err => {
      this.saving.set(false);
      this.error.set(err?.error?.message ?? err?.message ?? 'Save failed');
    },
  });
}
```

A few small choices worth flagging:

- The `raw.x || undefined` pattern collapses `''` to `undefined` for
  optional fields, so they round-trip as DB nulls rather than empty
  strings. Required fields use `raw.x!` (non-null assertion) because
  we just verified `form.invalid` is false.
- `customerNumber: this.id() ?? 0` is a small white lie — the
  backend's `CustomerRequestDTO` doesn't have a `customerNumber`
  field, but the frontend's `Customer` type does. We pass `0` on
  create just to satisfy the TypeScript type; the backend ignores
  it. (When we get to C5's optimistic locking, this field will
  matter and we'll need to revisit.)
- The error subscription captures both Spring-style `err.error.message`
  and generic `err.message` — same pattern the employee form uses.

## How to test

### Create flow

1. Save the modified files; the dev server hot-reloads.
2. Sign in (admin / admin123).
3. Navigate to **Customers** → click **New Customer**.
4. The form should render with four sections (Identity, Contact,
   Address, Account). The header reads "New customer" with no
   subtitle. There's no Customer Number field.
5. Click **Create customer** without filling anything. Every required
   field should light up with a red error message. The button stays
   disabled (or re-disables after the click).
6. Fill required fields. The Submit button enables.
7. Pick a sales rep from the dropdown — should show "Lastname,
   Firstname — Job Title". Pick the "— Unassigned" option — also
   valid.
8. Type 51 characters in Customer Name. The field should show
   "Maximum 50 characters" and the submit re-disables.
9. Submit a valid form. You should land back on /customers, with the
   new customer visible in the table (page 1, sorted by customerName
   ASC by default — the new customer might not be on page 1).

### Edit flow

1. Open any customer's detail page → click **Edit**.
2. The form should render pre-populated, with the header reading
   "Edit customer" and subtitle "#103".
3. Sales rep dropdown should show the current rep selected (or
   "— Unassigned" if there isn't one).
4. Change a field, click **Save changes**, you should land back on
   /customers/103 (the detail page).

### Validation edge cases

- Type a negative credit limit → "Credit limit cannot be negative".
- Submit with only whitespace in a required field → still flagged
  required (Angular `Validators.required` treats whitespace as empty).
- Reload the form during the initial fetch — should show the
  loading spinner, not an empty form.

## What you just learned

- **Reactive forms with grouped sections** — TypeScript-defined
  validators, template-bound controls, sectioned layout for
  scannability.
- **Mirroring DB constraints in `Validators`** — every server-side
  rule echoed client-side for fast feedback, with the backend as the
  source of truth.
- **Inline errors on touched controls** — `(touched && errors?.['key'])`
  is the standard guard, and validator names in the errors object
  are lowercase (`maxlength`, not `maxLength`).
- **`markAllAsTouched()`** to surface every error on a fresh-form
  submit attempt.
- **`forkJoin` for parallel reference-data loading**, with
  conditional spreads to add edit-mode requests only when needed.
- **`computed()` over reference data** for sorted/filtered dropdown
  options.
- **The "— Unassigned" pattern** for nullable foreign keys in a
  `mat-select`.
- **`type="button"` on the submit button** to avoid double-fire when
  click and form-submit are both wired.
- **Autocomplete attributes** on every input as a free UX win.

## Study materials

### Angular reactive forms

- [Angular — Reactive forms](https://angular.dev/guide/forms/reactive-forms)
- [Angular — Form validation](https://angular.dev/guide/forms/form-validation)
- [Angular — `FormBuilder`](https://angular.dev/api/forms/FormBuilder)
- [Angular — `AbstractControl`](https://angular.dev/api/forms/AbstractControl) — methods like `markAllAsTouched`, `setValue`, `patchValue`, `reset`

### Validators

- [Angular — Built-in validators](https://angular.dev/api/forms/Validators)
- [Angular — Custom validators guide](https://angular.dev/guide/forms/form-validation#custom-validators) — for when built-ins aren't enough

### Angular Material form widgets

- [Angular Material — Form fields](https://material.angular.io/components/form-field/overview)
- [Angular Material — MatSelect](https://material.angular.io/components/select/overview)
- [Angular Material — MatInput](https://material.angular.io/components/input/overview)
- [Angular Material — Theming forms](https://material.angular.io/components/form-field/styling)

### UX patterns for forms

- [Nielsen Norman Group — Form design](https://www.nngroup.com/articles/web-form-design/) — long but excellent
- [GOV.UK Design System — Forms](https://design-system.service.gov.uk/patterns/) — pragmatic, real-world conventions
- [MDN — `autocomplete` attribute](https://developer.mozilla.org/en-US/docs/Web/HTML/Attributes/autocomplete)
- [WCAG — Labels or instructions](https://www.w3.org/WAI/WCAG21/Understanding/labels-or-instructions.html) — accessibility floor for form labels
