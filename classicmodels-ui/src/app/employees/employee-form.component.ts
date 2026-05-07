import { Component, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
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

import { Employee, EmployeeService } from './employee.service';
import { Office, OfficeService } from '../offices/office.service';
import { ErrorDialogComponent } from '../shared/error-dialog.component';
import { EmployeePhotoComponent } from './employee-photo.component';

/**
 * Create / edit form for an employee.
 *
 * <p>Replaces the flat list of free-text inputs with a card-based, grouped
 * layout. The two FK fields (officeCode, reportsTo) are now real dropdowns
 * sourced from the API, so the user picks "Paris" instead of typing "4"
 * and "Gerard Bondur (Sales Manager EMEA)" instead of typing "1102".</p>
 *
 * <p>Same component handles both create and edit modes — distinguished by
 * the presence of an :id route param.</p>
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
    EmployeePhotoComponent,
  ],
  templateUrl: './employee-form.component.html',
  // Inline styles to avoid the same dev-server stale-file issue we hit
  // on the detail page. They're scoped to the component anyway.
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
    @media (max-width: 540px) {
      .row { grid-template-columns: 1fr; }
    }

    .full-width { width: 100%; }

    /*
      Add breathing room on the right and bottom so the action buttons
      aren't flush against the card edge. The top padding stays for
      separation from the form content above.
    */
    mat-card-actions { padding: 1rem 1rem 1rem 0; gap: 0.5rem; }

    .photo-row {
      display: flex; align-items: center; gap: 1rem;
      margin-bottom: 1rem;
      padding-bottom: 1rem;
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
    }
    .photo-controls {
      display: flex; flex-direction: column; gap: 0.25rem;
    }
    .photo-hint {
      color: rgba(0, 0, 0, 0.55);
      font-size: 0.8rem;
    }
  `],
})
export class EmployeeFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly employees = inject(EmployeeService);
  private readonly offices = inject(OfficeService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  // ----- Form -----
  form = this.fb.group({
    lastName:   ['', Validators.required],
    firstName:  ['', Validators.required],
    extension:  ['', Validators.required],
    email:      ['', [Validators.required, Validators.email]],
    officeCode: ['', Validators.required],
    reportsTo:  [null as number | null],
    jobTitle:   ['', Validators.required],
    /*
     * Hidden form field carrying the optimistic-lock version. Loaded
     * from the server when the form opens; sent back on save. The
     * user never sees or types it — patchValue() populates it
     * automatically because the backend's response shape includes it.
     * If the DB's current version is greater than this value, the
     * save fails with 409 and we show a "stale data" dialog.
     */
    version:    [null as number | null],
  });

  // ----- View state -----
  // Signals (not plain fields) so the template re-evaluates @if blocks
  // when these change after ngOnInit. With Angular's zoneless change
  // detection, plain class-field assignments don't always propagate
  // into the template.
  isEdit = signal(false);
  id = signal<number | undefined>(undefined);
  loading = signal(false);
  saving = signal(false);
  error = signal<string | undefined>(undefined);

  /** True while a photo upload is in flight. */
  uploadingPhoto = signal(false);

  /**
   * Handle to the embedded photo component, so we can call its
   * reload() after a successful upload (the employeeId input doesn't
   * change, so ngOnChanges wouldn't otherwise fire).
   */
  @ViewChild(EmployeePhotoComponent) photoView?: EmployeePhotoComponent;

  /**
   * File-input change handler. The (change) event on
   * {@code <input type="file">} fires when the user picks a file.
   * We grab the first selected file, upload it, and reload the photo
   * preview on success.
   */
  onPhotoSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const currentId = this.id();
    if (!file || currentId == null) return;

    this.uploadingPhoto.set(true);
    this.employees.uploadPhoto(currentId, file).subscribe({
      next: () => {
        this.uploadingPhoto.set(false);
        this.photoView?.reload();
      },
      error: err => {
        this.uploadingPhoto.set(false);
        const detail = err?.error?.detail
            ?? err?.error?.message
            ?? err?.message
            ?? 'Upload failed';
        this.dialog.open(ErrorDialogComponent, {
          data: { title: 'Photo upload failed', message: detail },
          width: '480px',
        });
      },
    });

    // Reset the input so picking the same file again still fires (change).
    input.value = '';
  }

  // ----- Reference data for dropdowns -----
  /** All offices, used for the Office dropdown. */
  offices_ = signal<Office[]>([]);

  /** All employees, used for the Reports-to dropdown. */
  private allEmployees = signal<Employee[]>([]);

  /**
   * Eligible managers: every active employee EXCEPT the current one
   * (an employee can't report to themselves). Computed from the all-
   * employees list and the route id, so it stays correct as either
   * dependency changes.
   */
  managers = computed(() =>
    this.allEmployees()
      .filter(e => e.active !== false)
      .filter(e => e.employeeNumber !== this.id())
      .sort((a, b) =>
        (a.lastName + a.firstName).localeCompare(b.lastName + b.firstName))
  );

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.isEdit.set(!!idParam);
    this.id.set(idParam ? Number(idParam) : undefined);

    this.loading.set(true);

    // Always need offices and the employee list for the dropdowns.
    // In edit mode also fetch the employee being edited so we can
    // patch the form. forkJoin parallelises everything.
    const requests = {
      offices: this.offices.list(),
      employees: this.employees.list(),
      ...(this.isEdit() && this.id() != null
          ? { employee: this.employees.get(this.id()!) }
          : {}),
    };

    forkJoin(requests as { offices: any; employees: any; employee?: any }).subscribe({
      next: (r: any) => {
        this.offices_.set(r.offices);
        this.allEmployees.set(r.employees);
        if (r.employee) {
          this.form.patchValue(r.employee);
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

    const value = this.form.value as Employee;
    const obs = this.isEdit()
      ? this.employees.update(this.id()!, value)
      : this.employees.create(value);

    obs.subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigate(['../'], { relativeTo: this.route });
      },
      error: err => {
        this.saving.set(false);
        // 409 from the optimistic-lock check gets a dedicated dialog
        // because the user has to take action (reload or discard) —
        // an inline banner is too easy to ignore. Other errors stay
        // inline; they're usually validation failures the user can
        // self-diagnose from the response message.
        if (err?.status === 409) {
          const detail = err?.error?.detail
              ?? 'This employee was modified by someone else while you were editing.';
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
