import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { CustomerService, CustomerCreditStatus } from './customer.service';
import { Customer } from './customer.model';
import { Employee, EmployeeService } from '../employees/employee.service';
import { MiniMapComponent } from '../shared/mini-map.component';

/**
 * Rich customer-detail page.
 *
 * <p>Replaces the previous flat field-list with a card-based layout
 * grouped by topic: header (name + quick actions), contact (person,
 * phone), address (multi-line, formatted), and account (sales-rep,
 * credit limit). Same shape as the employee-detail page from
 * Feature 24, applied to a different entity.</p>
 *
 * <h3>Why a parallel fetch</h3>
 *
 * <p>The customer record we get from {@code GET /customers/{id}} only
 * carries the sales-rep employee NUMBER (an FK), not the rep's name.
 * To render a useful card we have to resolve that FK to an Employee.
 * That's a second HTTP call. We fire both in parallel via {@code forkJoin}
 * — same pattern as Feature 1 used to resolve an employee's office
 * and manager — so the page paints once when both responses are in.</p>
 *
 * <p>The sales-rep call is wrapped in {@code catchError(() => of(null))}
 * so a missing or failed lookup doesn't blank out the page. We fall
 * back to "Employee #{n} (couldn't resolve)" instead.</p>
 */
@Component({
  standalone: true,
  selector: 'app-customer-detail',
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MiniMapComponent,
  ],
  templateUrl: './customer-detail.component.html',
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

    .detail-card { max-width: 720px; padding: 1rem; }

    .card-head {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      padding-bottom: 0.5rem;
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      margin-bottom: 1rem;
    }
    .card-head-text { flex: 1 1 auto; min-width: 0; }
    .card-title {
      font-size: 1.4rem;
      font-weight: 500;
      margin: 0;
      line-height: 1.2;
    }
    .card-subtitle {
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.9rem;
      margin-top: 0.2rem;
      display: flex;
      align-items: center;
      gap: 0.4rem;
      flex-wrap: wrap;
    }

    /*
      Quick-action buttons live in the header for visibility. They
      stack vertically on narrow screens (the flex-wrap on .card-head
      lets the text + buttons line break together).
    */
    .quick-actions {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    /*
      Sections are h3 + a definition-list grid. The grid keeps labels
      and values aligned across rows even when label widths vary.
    */
    .section { margin-top: 1.25rem; }
    .section h3 {
      margin: 0 0 0.5rem;
      font-size: 0.85rem;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.55);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .fields {
      display: grid;
      grid-template-columns: minmax(140px, max-content) 1fr;
      column-gap: 1.25rem;
      row-gap: 0.5rem;
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

    /* Multi-line address with each line on its own row */
    .address-block {
      display: block;
      line-height: 1.45;
    }

    /* The credit-limit value gets a slight emphasis to read as money */
    .money {
      font-variant-numeric: tabular-nums;
      font-weight: 500;
    }

    .muted {
      color: rgba(0, 0, 0, 0.55);
      margin-left: 0.4rem;
      font-size: 0.875rem;
    }

    /* Resolved-to-employee link styled subtly */
    .rep-link {
      color: #1565c0;
      text-decoration: none;
    }
    .rep-link:hover { text-decoration: underline; }
    .rep-title {
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.85rem;
    }

    /* Status chip styling. Inactive uses a subdued red so the chip
       reads "soft-deleted, not error." Same treatment as the
       employee-detail terminated chip. */
    .status-chip.inactive {
      background-color: #fdecea;
      color: #b71c1c;
    }

    /* Credit-status chip (C13). Three colour states matching the
       backend's Status enum:
         OK         — no chip rendered
         NEAR_LIMIT — orange
         OVER_LIMIT — red
         NO_LIMIT   — grey, italic ("no limit set")
       The chip sits inline next to the credit-limit value. */
    .credit-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      margin-left: 0.5rem;
      padding: 1px 8px;
      border-radius: 10px;
      font-size: 0.75rem;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .credit-chip.near {
      background: #fff3e0;
      color: #e65100;
    }
    .credit-chip.over {
      background: #fdecea;
      color: #b71c1c;
    }
    .credit-chip.none {
      background: rgba(0, 0, 0, 0.06);
      color: rgba(0, 0, 0, 0.55);
      text-transform: none;
      font-style: italic;
    }
    .credit-chip mat-icon {
      font-size: 14px; height: 14px; width: 14px;
    }

    /* Give the embedded map some breathing room from the address line.
       Same treatment as the employee-detail map. */
    .map-wrap {
      margin-top: 0.6rem;
      max-width: 480px;
    }

    /* Geocode status message — transient confirmation/error after the
       button click. Plain inline text rather than a chip or dialog so
       it doesn't fight for attention with the map. */
    .geocode-status {
      margin-top: 0.5rem;
      font-size: 0.85rem;
      color: rgba(0, 0, 0, 0.65);
    }
    .geocode-status.error {
      color: #b71c1c;
    }

    /* Empty-state placeholder when no coordinates are stored yet. */
    .map-empty {
      margin-top: 0.6rem;
      padding: 1rem;
      border: 1px dashed rgba(0, 0, 0, 0.18);
      border-radius: 4px;
      color: rgba(0, 0, 0, 0.55);
      font-size: 0.9rem;
    }

    /* Quiet "who and when" line at the bottom of the card.
       Same treatment as the employee-detail audit footer. */
    .audit-footer {
      margin-top: 1.25rem;
      padding-top: 0.75rem;
      border-top: 1px solid rgba(0, 0, 0, 0.08);
      color: rgba(0, 0, 0, 0.55);
      font-size: 0.8rem;
    }
  `],
})
export class CustomerDetailComponent implements OnInit {
  private readonly customers = inject(CustomerService);
  private readonly employees = inject(EmployeeService);
  private readonly route = inject(ActivatedRoute);

  /** The customer being viewed. */
  customer = signal<Customer | undefined>(undefined);

  /**
   * The resolved sales-rep Employee, or null on lookup failure
   * (deleted rep, network error, etc.). Undefined while loading.
   */
  salesRep = signal<Employee | null | undefined>(undefined);

  /**
   * Credit status from C13 — null while loading, undefined if the
   * fetch failed (we degrade silently — the chip just doesn't render).
   */
  creditStatus = signal<CustomerCreditStatus | null | undefined>(undefined);

  loading = signal(false);
  error = signal<string | undefined>(undefined);

  /** True while a geocode call is in flight. Disables the button. */
  geocoding = signal(false);

  /**
   * Transient status string shown next to the map after a geocode
   * call. Cleared when the user navigates away or starts another
   * geocode. The {@code .error} class is applied if {@link geocodeError}
   * is true.
   */
  geocodeMessage = signal<string | undefined>(undefined);
  geocodeError = signal(false);

  /**
   * Numeric versions of {@code lat}/{@code lng}. The wire format is
   * BigDecimal-as-string (Jackson default), so we parse here once.
   * Returns null if either is absent or unparseable — the template
   * uses that to decide whether to render the map.
   */
  coords = computed<{ lat: number; lng: number } | null>(() => {
    const c = this.customer();
    if (!c?.lat || !c?.lng) return null;
    const lat = Number(c.lat);
    const lng = Number(c.lng);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
    return { lat, lng };
  });

  /**
   * Popup HTML rendered inside the marker. Built from sanitised
   * customer fields — Leaflet renders the string as raw HTML, so we
   * escape any user-provided value before concatenating.
   */
  popupHtml = computed<string | undefined>(() => {
    const c = this.customer();
    if (!c) return undefined;
    const esc = (s: string | undefined | null) =>
      (s ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
      }[ch]!));
    return `<strong>${esc(c.customerName)}</strong><br>${esc(c.city)}, ${esc(c.country)}`;
  });

  /**
   * Pre-formatted multi-line address. Computed so the template can
   * just render the result without conditional logic.
   */
  addressLines = computed(() => {
    const c = this.customer();
    if (!c) return [];
    const lines: string[] = [c.addressLine1];
    if (c.addressLine2) lines.push(c.addressLine2);
    // City, State Postal — comma-separated where parts exist.
    const cityLine = [c.city, c.state, c.postalCode]
      .filter(Boolean)
      .join(c.state || c.postalCode ? ', ' : '');
    if (cityLine) lines.push(cityLine);
    if (c.country) lines.push(c.country);
    return lines;
  });

  ngOnInit() {
    // Validate the route param. Number("foo") is NaN, which would
    // silently propagate to the backend as ?id=NaN. Catch it here.
    const raw = this.route.snapshot.paramMap.get('id');
    const id = Number(raw);
    if (raw === null || !Number.isInteger(id)) {
      this.error.set(`Invalid customer id in URL: "${raw}"`);
      return;
    }

    this.loading.set(true);
    this.customers.get(id).subscribe({
      next: customer => {
        this.customer.set(customer);

        // Sales rep is best-effort: a 404 (deleted employee) shouldn't
        // tank the page, just leaves the rep slot showing the raw id
        // with a "couldn't resolve" hint.
        const rep$ = customer.salesRepEmployeeNumber
          ? this.employees.get(customer.salesRepEmployeeNumber).pipe(catchError(() => of(null)))
          : of(null);

        // Credit status (C13) — also best-effort. If the call fails
        // we leave the chip un-rendered rather than failing the page.
        const credit$ = this.customers.getCreditStatus(id).pipe(catchError(() => of(null)));

        forkJoin({ rep: rep$, credit: credit$ }).subscribe(({ rep, credit }) => {
          this.salesRep.set(rep);
          this.creditStatus.set(credit);
          this.loading.set(false);
        });
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load customer');
        this.loading.set(false);
      },
    });
  }

  /**
   * Trigger a geocode for the current customer. Updates the local
   * customer signal with the freshly-read row so the map appears (or
   * repositions) without a page reload.
   */
  runGeocode(): void {
    const c = this.customer();
    if (!c || this.geocoding()) return;

    this.geocoding.set(true);
    this.geocodeMessage.set(undefined);
    this.geocodeError.set(false);

    this.customers.geocode(c.customerNumber).subscribe({
      next: updated => {
        this.customer.set(updated);
        this.geocoding.set(false);
        this.geocodeMessage.set('Geocoded successfully. Map updated.');
      },
      error: err => {
        this.geocoding.set(false);
        this.geocodeError.set(true);
        const detail = err?.error?.detail
          ?? err?.error?.message
          ?? err?.message
          ?? 'Unknown error';
        this.geocodeMessage.set(`Geocoding failed: ${detail}`);
      },
    });
  }

  /**
   * Format the credit-utilisation ratio as a percent ("83%").
   * Used in the chip body next to the status icon.
   */
  formatUtilization(u: string | null | undefined): string {
    if (u == null) return '';
    const v = Number(u);
    if (!Number.isFinite(v)) return '';
    return Math.round(v * 100) + '%';
  }

  /**
   * Format a credit limit as USD. Returns "—" for null/undefined so
   * the template doesn't have to add another conditional.
   */
  formatMoney(n: number | null | undefined): string {
    if (n == null) return '—';
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 0,
    }).format(n);
  }
}
