import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';

import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';

import { CustomerService } from './customer.service';
import { Customer } from './customer.model';
import { Employee, EmployeeService } from '../employees/employee.service';
import { ErrorDialogComponent } from '../shared/error-dialog.component';

/**
 * Create / edit form for a customer.
 *
 * <p>Replaces the flat list of free-text inputs with a card-based, grouped
 * layout matching the employee-form pattern: four sections (Identity,
 * Contact, Address, Account), inline validation messages, and a sales-rep
 * dropdown sourced from {@code GET /employees}.</p>
 *
 * <p>The same component handles both create and edit modes — distinguished
 * by the presence of an {@code :id} route param. Edit mode loads the
 * existing customer in parallel with the employee list (for the rep
 * dropdown) via {@code forkJoin}.</p>
 *
 * <h3>Why no customerNumber field</h3>
 *
 * <p>Customer numbers are auto-incremented by MySQL on insert (see
 * {@code customers.customerNumber INT AUTO_INCREMENT}). The previous
 * form asked the user to type one in, but the value was discarded by
 * the backend. We removed the field rather than leave a confusing
 * input that does nothing. The number is shown in the card subtitle
 * on edit mode for reference.</p>
 *
 * <h3>Field length validators</h3>
 *
 * <p>Mirror the DB column widths exactly so client-side errors fire
 * before the user submits. {@code maxLength(50)} on most {@code VARCHAR(50)}
 * columns; {@code maxLength(15)} on {@code postalCode}. Catching at the
 * form layer is the cheapest place — no round-trip, immediate feedback.</p>
 */
@Component({
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './customer-form.component.html',
  styles: [`
    .back-link {
      display: inline-flex; align-items: center; gap: 0.25rem;
      margin-bottom: 1rem; text-decoration: none;
      color: rgba(0, 0, 0, 0.7);
    }
    .back-link:hover { color: rgba(0, 0, 0, 0.9); }
    .back-link mat-icon { font-size: 18px; height: 18px; width: 18px; }

    .form-card { max-width: 720px; }

    .loading {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 2rem 0; color: rgba(0, 0, 0, 0.6);
    }

    .error-banner {
      border-left: 4px solid #b71c1c;
      padding: 0.5rem 1rem;
      margin-bottom: 1rem;
      background: #fdecea;
      color: #b71c1c;
      border-radius: 4px;
    }

    section {
      border-top: 1px solid rgba(0, 0, 0, 0.08);
      padding-top: 1rem;
      margin-top: 1rem;
    }
    section:first-of-type { border-top: none; padding-top: 0; margin-top: 0; }

    section h3 {
      margin: 0 0 0.75rem;
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: rgba(0, 0, 0, 0.55);
      font-weight: 600;
    }

    /* Two-column responsive grid for fields. Stacks on narrow screens. */
    .row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0 1rem;
    }
    .row.single { grid-template-columns: 1fr; }
    /* 1:2 split for postalCode + country — postal codes are short. */
    .row.short-long { grid-template-columns: minmax(120px, 1fr) 2fr; }
    @media (max-width: 540px) {
      .row { grid-template-columns: 1fr; }
      .row.short-long { grid-template-columns: 1fr; }
    }

    .full-width { width: 100%; }

    mat-card-actions { padding: 1rem 1rem 1rem 0; gap: 0.5rem; }
  `],
})
export class CustomerFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly customers = inject(CustomerService);
  private readonly employees = inject(EmployeeService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  // ----- Form -----
  // Validator lengths mirror the customers table's VARCHAR widths.
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
    /*
     * creditLimit is a DECIMAL(10,2): max 99,999,999.99. Min(0) keeps
     * the form sane — a negative credit limit doesn't model anything
     * the rest of the app cares about. We don't enforce the upper bound
     * client-side because hitting it usually means a typo (the typical
     * value is in the tens of thousands), and surfacing a "max value"
     * error for an out-of-band typo is more confusing than letting the
     * backend reject it.
     */
    creditLimit: [null as number | null, Validators.min(0)],
    /*
     * Hidden form field carrying the optimistic-lock version (V6).
     * Loaded from the server when the form opens; sent back on save.
     * The user never sees or types it — patchValue() populates it
     * automatically because the backend's response shape includes it.
     * C5 wires the 409 handling: if the DB's current version is greater
     * than this value, the save fails and we show a "stale data" dialog.
     */
    version: [null as number | null],
  });

  // ----- View state -----
  // Signals so the template re-evaluates @if blocks when these change
  // after ngOnInit (zoneless change detection won't catch plain field
  // assignments).
  isEdit = signal(false);
  id = signal<number | undefined>(undefined);
  loading = signal(false);
  saving = signal(false);
  error = signal<string | undefined>(undefined);

  // ----- Reference data for dropdown -----
  /** Active employees, used for the Sales Rep dropdown. */
  private allEmployees = signal<Employee[]>([]);

  /**
   * The salesRep options. Active employees only, sorted by lastName +
   * firstName for a predictable browsing order. Computed from
   * allEmployees so it stays current if the underlying list changes.
   */
  salesReps = computed(() =>
    this.allEmployees()
      .filter(e => e.active !== false)
      .sort((a, b) =>
        (a.lastName + a.firstName).localeCompare(b.lastName + b.firstName))
  );

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.isEdit.set(!!idParam);
    this.id.set(idParam ? Number(idParam) : undefined);

    this.loading.set(true);

    // Always need the employee list for the sales-rep dropdown.
    // In edit mode also fetch the customer being edited so we can
    // patch the form. forkJoin runs both in parallel.
    const requests = {
      employees: this.employees.list(),
      ...(this.isEdit() && this.id() != null
          ? { customer: this.customers.get(this.id()!) }
          : {}),
    };

    forkJoin(requests as { employees: any; customer?: any }).subscribe({
      next: (r: any) => {
        this.allEmployees.set(r.employees);
        if (r.customer) {
          // patchValue ignores keys not present on the form; customerNumber
          // on the customer record is silently dropped, which is what we
          // want — the form doesn't have a customerNumber field anymore.
          this.form.patchValue(r.customer);
        }
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load form');
        this.loading.set(false);
      },
    });
  }

  save() {
    if (this.form.invalid) {
      // Reactive forms don't auto-show errors until a control is touched;
      // markAllAsTouched() forces every error message to surface so the
      // user can see what's blocking the submit.
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(undefined);

    // Empty optional strings come through as '' from the form. The DB
    // tolerates that, but treating "" as "not provided" gives cleaner
    // round-trips (an unfilled address line round-trips as null, not
    // an empty string the user has to compare against).
    const raw = this.form.value;
    const value: Customer = {
      customerNumber: this.id() ?? 0, // ignored on create, present on edit
      customerName: raw.customerName!,
      contactFirstName: raw.contactFirstName!,
      contactLastName: raw.contactLastName!,
      phone: raw.phone!,
      addressLine1: raw.addressLine1!,
      addressLine2: raw.addressLine2 || undefined,
      city: raw.city!,
      state: raw.state || undefined,
      postalCode: raw.postalCode || undefined,
      country: raw.country!,
      salesRepEmployeeNumber: raw.salesRepEmployeeNumber ?? undefined,
      creditLimit: raw.creditLimit ?? undefined,
      // Carry the version through so C5's 409 check can use it. On
      // create the form has version=null (no row exists yet); on edit
      // patchValue populated it from the loaded customer.
      version: raw.version ?? undefined,
    };

    const obs = this.isEdit()
      ? this.customers.update(this.id()!, value)
      : this.customers.create(value);

    obs.subscribe({
      next: () => {
        this.saving.set(false);
        // Edit mode lives at /customers/:id/edit — go up two levels to
        // /customers. Create mode lives at /customers/new — go up one.
        const back = this.isEdit() ? '../../' : '../';
        this.router.navigate([back], { relativeTo: this.route });
      },
      error: err => {
        this.saving.set(false);
        // 409 from the optimistic-lock check (C5) gets a dedicated dialog
        // because the user has to take action (reload or discard) — an
        // inline banner is too easy to ignore. The shape mirrors the
        // employee-form's 409 handler. Other errors stay inline; they're
        // usually validation failures the user can self-diagnose from
        // the response message.
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
  }
}
