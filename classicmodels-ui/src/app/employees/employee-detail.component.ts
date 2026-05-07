import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Subscription, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { EmployeeEventsService } from '../realtime/employee-events.service';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { Employee, EmployeeService } from './employee.service';
import { Office, OfficeService } from '../offices/office.service';
import { MiniMapComponent } from '../shared/mini-map.component';
import { EmployeePhotoComponent } from './employee-photo.component';

/**
 * Rich employee-detail page.
 *
 * <p>Replaces the previous JSON dump with a card-based layout. The
 * three pieces of data needed (the employee itself, their office, their
 * manager) are fetched in parallel via {@code forkJoin}. The office and
 * manager calls are wrapped in {@code catchError} returning {@code null}
 * so that a missing or failing dependency doesn't blank out the whole
 * page — we just render the raw code/id and a small "couldn't resolve"
 * hint instead.</p>
 *
 * <p>The employee fetch IS allowed to fail loudly: that's the actual
 * subject of the page, and rendering a card for a non-existent employee
 * would be more confusing than helpful.</p>
 */
@Component({
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MiniMapComponent,
    EmployeePhotoComponent,
  ],
  templateUrl: './employee-detail.component.html',
  styles: [`
    .back-link {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      margin-bottom: 1rem;
      text-decoration: none;
      color: rgba(0, 0, 0, 0.7);
    }
    .back-link:hover { color: rgba(0, 0, 0, 0.9); }
    .back-link mat-icon {
      font-size: 18px; height: 18px; width: 18px;
    }

    .loading {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 1rem 0;
      color: rgba(0, 0, 0, 0.6);
    }

    .error-card { border-left: 4px solid #b71c1c; }
    .error-icon {
      color: #b71c1c;
      vertical-align: middle;
      margin-right: 0.5rem;
    }

    .detail-card { max-width: 720px; }

    /*
      Custom header layout: photo on the left in its own column, title
      + subtitle stacked on the right. Replaces what mat-card-header
      would have given us — mat-card-avatar's hard-coded 40×40 slot
      didn't play well with our 96px photo.
    */
    .card-head {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1rem 1rem 0.5rem;
    }
    .card-photo {
      flex: 0 0 auto;
      --photo-size: 80px;
    }
    .card-head-text {
      flex: 1 1 auto;
      min-width: 0;       /* lets long names ellipsis instead of pushing */
    }
    .card-title {
      font-size: 1.25rem;
      font-weight: 500;
    }
    .card-subtitle {
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.9rem;
      margin-top: 0.1rem;
    }

    .title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .status-chip {
      font-size: 0.75rem;
      min-height: 24px;
      height: 24px;
    }
    .status-chip.active     { background: #e6f4ea; color: #1b5e20; }
    .status-chip.terminated { background: #fdecea; color: #b71c1c; }

    .fields {
      display: grid;
      grid-template-columns: minmax(140px, max-content) 1fr;
      column-gap: 1.25rem;
      row-gap: 0.6rem;
      margin: 0;
    }
    .fields dt {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      color: rgba(0, 0, 0, 0.6);
      font-weight: 500;
    }
    .fields dt mat-icon {
      font-size: 18px; height: 18px; width: 18px;
      color: rgba(0, 0, 0, 0.45);
    }
    .fields dd {
      margin: 0;
      color: rgba(0, 0, 0, 0.87);
      word-break: break-word;
    }

    .muted {
      color: rgba(0, 0, 0, 0.55);
      margin-left: 0.4rem;
      font-size: 0.875rem;
    }
    .office-code { font-style: italic; }

    /* Give the embedded map some breathing room from the address line. */
    .map-wrap {
      margin-top: 0.6rem;
      max-width: 480px;
    }

    /* Quiet "who and when" line at the bottom of the card. */
    .audit-footer {
      margin-top: 1.25rem;
      padding-top: 0.75rem;
      border-top: 1px solid rgba(0, 0, 0, 0.08);
      color: rgba(0, 0, 0, 0.55);
      font-size: 0.8rem;
    }
  `],
})
export class EmployeeDetailComponent implements OnInit, OnDestroy {
  private readonly employees = inject(EmployeeService);
  private readonly offices = inject(OfficeService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly liveEvents = inject(EmployeeEventsService);

  private eventsSub?: Subscription;
  /** The currently-viewed employee id (set in ngOnInit, re-used by the events handler). */
  private currentId?: number;

  /** The subject of this page. */
  employee = signal<Employee | undefined>(undefined);

  /** Resolved office for the employee, or undefined while loading / null on error. */
  office = signal<Office | null | undefined>(undefined);

  /** Resolved manager (the employee referenced by reportsTo). */
  manager = signal<Employee | null | undefined>(undefined);

  loading = signal(false);
  error = signal<string | undefined>(undefined);

  /**
   * Build the HTML shown inside the office marker's popup.
   *
   * <p>We hand-build a small chunk of HTML (rather than rendering an
   * Angular template) because Leaflet's popup expects either an HTML
   * string or a raw DOM element — it's not Angular-aware. Building a
   * string is the simpler of the two, but it means we have to escape
   * any data that might contain characters the browser would interpret
   * as markup. See {@link escapeHtml}.</p>
   *
   * <p>Template literals (the backticks below) let us write multi-line
   * strings with {@code ${...}} interpolation. The whole thing is one
   * string when the function returns.</p>
   */
  buildOfficePopup(office: Office): string {
    const e = (s: string | null | undefined) => this.escapeHtml(s);
    const officeRoute = `/offices/${encodeURIComponent(office.officeCode)}`;

    return `
      <div style="min-width: 180px;">
        <strong>${e(office.city)} office</strong><br>
        ${e(office.addressLine1)}<br>
        ${office.addressLine2 ? `${e(office.addressLine2)}<br>` : ''}
        ${office.state ? `${e(office.state)}, ` : ''}${e(office.country)}<br>
        <span style="color: rgba(0,0,0,0.6);">📞 ${e(office.phone)}</span><br>
        <a href="${officeRoute}">View office details →</a>
      </div>
    `;
  }

  /**
   * Convert the five characters that have meaning in HTML to their
   * named-entity equivalents. Tiny helper, but absolutely required
   * any time you build HTML by string concatenation from data you
   * didn't originate yourself.
   *
   * <p>Without this, a hypothetical office named {@code "Tom <strong>'s</strong>"}
   * would inject real {@code <strong>} tags into the popup, and a
   * malicious one could inject scripts.</p>
   */
  private escapeHtml(s: string | null | undefined): string {
    if (s == null) return '';
    return s.replace(/[&<>"']/g, c => {
      switch (c) {
        case '&': return '&amp;';
        case '<': return '&lt;';
        case '>': return '&gt;';
        case '"': return '&quot;';
        case "'": return '&#39;';
        default:  return c;
      }
    });
  }

  ngOnInit() {
    // Validate the route param up front. Number("foo") returns NaN, which
    // would silently propagate to the backend as `?id=NaN` and surface as
    // a confusing 400 from MethodArgumentTypeMismatchException. Catch it
    // here so we render a sensible error instead.
    const raw = this.route.snapshot.paramMap.get('id');
    const id = Number(raw);
    if (raw === null || !Number.isInteger(id)) {
      this.error.set(`Invalid employee id in URL: "${raw}"`);
      return;
    }

    this.currentId = id;
    this.loading.set(true);

    // Subscribe to live employee events so this detail page reacts
    // when someone else (or another browser tab of ours) edits the
    // currently-viewed employee. UPDATED → re-fetch to pick up the
    // latest values; DELETED → bounce back to the list because the
    // page no longer represents anything.
    this.eventsSub = this.liveEvents.events$.subscribe(event => {
      if (event.employeeNumber !== this.currentId) return;
      if (event.type === 'DELETED') {
        // The row we're displaying is gone — there's nothing useful
        // to show here. Send the user back to the list, where the
        // row's absence will at least be self-explanatory.
        this.router.navigate(['/employees']);
      } else {
        // CREATED never targets an existing id, so this is effectively
        // "UPDATED" → reload the data on the page.
        this.reloadEmployee(this.currentId!);
      }
    });

    this.reloadEmployee(id);
  }

  ngOnDestroy(): void {
    this.eventsSub?.unsubscribe();
  }

  /**
   * Fetch the employee, then fan out to fetch their office and manager
   * in parallel. Extracted into its own method so we can re-fetch on
   * live UPDATED events without duplicating the loading sequence.
   */
  private reloadEmployee(id: number): void {
    this.loading.set(true);
    this.employees.get(id).subscribe({
      next: emp => {
        this.employee.set(emp);

        // Office and manager are best-effort. A 404 on either shouldn't
        // tank the page — it just means we render the raw code/id with
        // a small hint that we couldn't resolve it.
        const office$ = this.offices
          .get(emp.officeCode)
          .pipe(catchError(() => of(null)));

        const manager$ = emp.reportsTo
          ? this.employees.get(emp.reportsTo).pipe(catchError(() => of(null)))
          : of(null); // No manager — president-of-the-company case.

        forkJoin({ office: office$, manager: manager$ }).subscribe(({ office, manager }) => {
          this.office.set(office);
          this.manager.set(manager);
          this.loading.set(false);
        });
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load employee');
        this.loading.set(false);
      },
    });
  }
}
