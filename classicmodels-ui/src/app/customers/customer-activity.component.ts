import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule, DatePipe } from '@angular/common';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonToggleModule } from '@angular/material/button-toggle';

import {
  CustomerService,
  CustomerActivity,
  CustomerActivityItem,
} from './customer.service';
import { Customer } from './customer.model';

/**
 * Unified Customer Activity timeline (C11).
 *
 * <p>Renders one customer's orders and payments interleaved into a
 * single date-sorted stream, with header aggregations and a chip
 * group to filter to one type at a time.</p>
 *
 * <h3>Why one stream rather than two side-by-side lists</h3>
 *
 * <p>Two parallel lists ("Orders" and "Payments" tabs) is the easy
 * thing to build but the wrong shape for the question users actually
 * have: <em>"what's the recent history with this customer?"</em>
 * That question is best answered by a chronological feed where an
 * order on the 5th and a payment on the 7th sit next to each other,
 * not in separate columns. The chip filter is the escape hatch for
 * the rare moments when a single-type view is more useful.</p>
 *
 * <h3>Polymorphic row rendering</h3>
 *
 * <p>The {@code @switch} on {@code item.kind} narrows the TypeScript
 * type so each branch can read the kind-specific fields without
 * non-null assertions. Order rows show item count + total + status;
 * payment rows show amount + check number. Same row container, two
 * shapes — the discriminated union is doing the work.</p>
 */
@Component({
  standalone: true,
  selector: 'app-customer-activity',
  imports: [
    CommonModule,
    RouterLink,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatButtonToggleModule,
  ],
  template: `
    <a [routerLink]="['../']" class="back-link">
      <mat-icon>arrow_back</mat-icon> Back to customer
    </a>

    @if (loading()) {
      <div class="loading">
        <mat-spinner diameter="32"></mat-spinner>
        <span>Loading activity…</span>
      </div>
    }

    @if (error()) {
      <mat-card class="error-card">
        <mat-card-content>
          <mat-icon class="error-icon">error_outline</mat-icon>
          {{ error() }}
        </mat-card-content>
      </mat-card>
    }

    @if (!loading() && activity(); as a) {
      <header class="page-head">
        <h2>
          @if (customer(); as c) {
            {{ c.customerName }} — activity
          } @else {
            Customer #{{ id() }} — activity
          }
        </h2>
      </header>

      <!--
        Header aggregations. Four "stat tiles" in a responsive row.
        Outstanding balance gets the emphasis treatment because it's
        the actionable number — "does this customer owe us money?"
      -->
      <div class="stats">
        <div class="stat">
          <div class="stat-label">Lifetime spend</div>
          <div class="stat-value money">{{ formatMoney(a.summary.lifetimeSpend) }}</div>
          <div class="stat-sub">{{ a.summary.orderCount }} order{{ a.summary.orderCount === 1 ? '' : 's' }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">Total paid</div>
          <div class="stat-value money">{{ formatMoney(a.summary.totalPaid) }}</div>
          <div class="stat-sub">{{ a.summary.paymentCount }} payment{{ a.summary.paymentCount === 1 ? '' : 's' }}</div>
        </div>
        <div class="stat" [class.warn]="hasOutstanding(a)">
          <div class="stat-label">Outstanding</div>
          <div class="stat-value money">{{ formatMoney(a.summary.outstandingBalance) }}</div>
          <div class="stat-sub">
            @if (hasOutstanding(a)) {
              owed by customer
            } @else {
              none
            }
          </div>
        </div>
        <div class="stat">
          <div class="stat-label">Last activity</div>
          <div class="stat-value">
            @if (a.summary.lastActivityDate; as d) {
              {{ d | date:'mediumDate' }}
            } @else {
              —
            }
          </div>
          <div class="stat-sub">
            @if (a.summary.firstActivityDate; as d) {
              first {{ d | date:'mediumDate' }}
            }
          </div>
        </div>
      </div>

      <!--
        Filter chips. Three options: All / Orders / Payments. Picks
        from {@code visibleItems} (computed from {@code filter} +
        the full list) to drive the rendered timeline.
      -->
      <mat-button-toggle-group [value]="filter()"
                               (change)="filter.set($event.value)"
                               aria-label="Filter activity"
                               class="filter-group">
        <mat-button-toggle value="ALL">
          All <span class="chip-count">({{ a.items.length }})</span>
        </mat-button-toggle>
        <mat-button-toggle value="ORDER">
          Orders <span class="chip-count">({{ a.summary.orderCount }})</span>
        </mat-button-toggle>
        <mat-button-toggle value="PAYMENT">
          Payments <span class="chip-count">({{ a.summary.paymentCount }})</span>
        </mat-button-toggle>
      </mat-button-toggle-group>

      <!--
        Timeline list. Polymorphic row body driven by item.kind.
        @switch narrows the TypeScript type so each branch can read
        kind-specific fields without bangs or assertions.
      -->
      @if (visibleItems().length === 0) {
        <div class="empty">
          <p>No activity in this view.</p>
          @if (filter() !== 'ALL') {
            <p class="muted">Try selecting "All" to see everything.</p>
          }
        </div>
      } @else {
        <ol class="timeline">
          @for (item of visibleItems(); track timelineKey(item)) {
            <li class="timeline-row" [class.is-order]="item.kind === 'ORDER'"
                                     [class.is-payment]="item.kind === 'PAYMENT'">
              <!-- Date column. Same width regardless of kind so rows align. -->
              <div class="t-date">
                {{ item.activityDate | date:'mediumDate' }}
              </div>

              <!-- Type chip — colours encode the kind. -->
              <div class="t-kind">
                @switch (item.kind) {
                  @case ('ORDER') {
                    <span class="kind-chip kind-chip-order">
                      <mat-icon>shopping_cart</mat-icon> Order
                    </span>
                  }
                  @case ('PAYMENT') {
                    <span class="kind-chip kind-chip-payment">
                      <mat-icon>payments</mat-icon> Payment
                    </span>
                  }
                }
              </div>

              <!-- Body — kind-specific. -->
              <div class="t-body">
                @switch (item.kind) {
                  @case ('ORDER') {
                    <div class="t-line">
                      <strong>#{{ item.orderNumber }}</strong>
                      <span class="status-badge"
                            [class]="'status-' + item.orderStatus?.toLowerCase()">
                        {{ item.orderStatus }}
                      </span>
                    </div>
                    <div class="t-sub">
                      {{ item.itemCount }} item{{ item.itemCount === 1 ? '' : 's' }}
                      · <span class="money">{{ formatMoney(item.orderTotal) }}</span>
                    </div>
                  }
                  @case ('PAYMENT') {
                    <div class="t-line">
                      Check <strong>{{ item.checkNumber }}</strong>
                    </div>
                    <div class="t-sub">
                      <span class="money">{{ formatMoney(item.paymentAmount) }}</span>
                    </div>
                  }
                }
              </div>
            </li>
          }
        </ol>
      }
    }
  `,
  styles: [`
    .back-link {
      display: inline-flex; align-items: center; gap: 0.25rem;
      margin-bottom: 1rem; text-decoration: none;
      color: rgba(0, 0, 0, 0.7);
    }
    .back-link mat-icon { font-size: 18px; height: 18px; width: 18px; }

    .page-head h2 { margin: 0 0 1rem; }

    .loading {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 2rem 0; color: rgba(0, 0, 0, 0.6);
    }
    .error-card { border-left: 4px solid #b71c1c; max-width: 720px; }
    .error-icon { color: #b71c1c; vertical-align: middle; margin-right: 0.5rem; }

    /* Stat tiles row. Auto-fit means they wrap to two/one columns
       on narrow screens without media queries. */
    .stats {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 0.75rem;
      margin-bottom: 1.25rem;
    }
    .stat {
      padding: 0.75rem 1rem;
      background: #fafafa;
      border: 1px solid rgba(0, 0, 0, 0.08);
      border-radius: 6px;
    }
    .stat.warn {
      background: #fff8e1;
      border-color: rgba(245, 124, 0, 0.4);
    }
    .stat-label {
      font-size: 0.75rem;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: rgba(0, 0, 0, 0.55);
      margin-bottom: 0.3rem;
    }
    .stat-value { font-size: 1.25rem; font-weight: 500; line-height: 1.2; }
    .stat-sub { font-size: 0.8rem; color: rgba(0, 0, 0, 0.55); margin-top: 0.2rem; }

    .money { font-variant-numeric: tabular-nums; }

    .filter-group { margin-bottom: 1rem; }
    .chip-count { color: rgba(0, 0, 0, 0.5); margin-left: 0.25rem; }

    .empty {
      padding: 3rem 1rem;
      text-align: center;
      border: 1px dashed rgba(0, 0, 0, 0.18);
      border-radius: 4px;
      color: rgba(0, 0, 0, 0.7);
    }
    .empty p { margin: 0.4rem 0; }
    .muted { color: rgba(0, 0, 0, 0.55); }

    .timeline {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .timeline-row {
      display: grid;
      grid-template-columns: 110px 130px 1fr;
      gap: 0.75rem;
      padding: 0.6rem 0;
      border-bottom: 1px solid rgba(0, 0, 0, 0.06);
      align-items: start;
    }
    .timeline-row:last-child { border-bottom: none; }
    @media (max-width: 540px) {
      .timeline-row {
        grid-template-columns: 1fr;
        gap: 0.25rem;
      }
    }

    .t-date {
      font-size: 0.85rem;
      color: rgba(0, 0, 0, 0.6);
      padding-top: 4px;
    }

    .kind-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      padding: 2px 8px;
      border-radius: 12px;
      font-size: 0.8rem;
      font-weight: 500;
    }
    .kind-chip mat-icon {
      font-size: 16px; height: 16px; width: 16px;
    }
    .kind-chip-order   { background: #e3f2fd; color: #0d47a1; }
    .kind-chip-payment { background: #e8f5e9; color: #1b5e20; }

    .t-line { font-size: 0.95rem; }
    .t-sub  { font-size: 0.85rem; color: rgba(0, 0, 0, 0.6); margin-top: 0.15rem; }

    /* Order-status badge — light pill that picks up colour by status name. */
    .status-badge {
      display: inline-block;
      margin-left: 0.4rem;
      padding: 1px 8px;
      border-radius: 10px;
      font-size: 0.75rem;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      background: rgba(0, 0, 0, 0.08);
      color: rgba(0, 0, 0, 0.7);
    }
    .status-badge.status-shipped    { background: #e8f5e9; color: #1b5e20; }
    .status-badge.status-resolved   { background: #e8f5e9; color: #1b5e20; }
    .status-badge.status-in-process { background: #fff3e0; color: #e65100; }
    .status-badge.status-on-hold    { background: #fff3e0; color: #e65100; }
    .status-badge.status-disputed   { background: #fdecea; color: #b71c1c; }
    .status-badge.status-cancelled  { background: #fdecea; color: #b71c1c; }
  `],
})
export class CustomerActivityComponent implements OnInit {
  private readonly customers = inject(CustomerService);
  private readonly route = inject(ActivatedRoute);

  id = signal<number | undefined>(undefined);

  /** The full customer record — used only for the page title. Optional. */
  customer = signal<Customer | undefined>(undefined);

  activity = signal<CustomerActivity | undefined>(undefined);

  loading = signal(true);
  error = signal<string | undefined>(undefined);

  /** Current filter selection. Drives {@link visibleItems}. */
  filter = signal<'ALL' | 'ORDER' | 'PAYMENT'>('ALL');

  /**
   * Items currently visible after filter. Computed from the loaded
   * activity + the chip selection. Re-derives when either changes.
   */
  visibleItems = computed<CustomerActivityItem[]>(() => {
    const a = this.activity();
    if (!a) return [];
    const f = this.filter();
    if (f === 'ALL') return a.items;
    return a.items.filter(item => item.kind === f);
  });

  ngOnInit(): void {
    const raw = this.route.snapshot.paramMap.get('id');
    const id = Number(raw);
    if (raw === null || !Number.isInteger(id)) {
      this.error.set(`Invalid customer id in URL: "${raw}"`);
      this.loading.set(false);
      return;
    }
    this.id.set(id);

    this.loading.set(true);
    // Fetch the customer for the title and the activity in parallel.
    // Activity is the main payload; the customer fetch is just for the
    // page title and could be skipped without losing functionality.
    this.customers.get(id).subscribe({
      next: c => this.customer.set(c),
      // Title-only fetch — silently swallow failures so the activity
      // page still loads with "Customer #N — activity" instead.
      error: () => {},
    });

    this.customers.getActivity(id).subscribe({
      next: a => {
        this.activity.set(a);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load activity');
        this.loading.set(false);
      },
    });
  }

  /**
   * Stable key for *@for tracking. Order numbers and check numbers
   * are unique within their kind but not necessarily globally, so
   * we prefix with kind to be safe.
   */
  timelineKey(item: CustomerActivityItem): string {
    return item.kind === 'ORDER'
      ? `O-${item.orderNumber}`
      : `P-${item.checkNumber}`;
  }

  /** True when the customer has an outstanding (positive) balance. */
  hasOutstanding(a: CustomerActivity): boolean {
    return Number(a.summary.outstandingBalance) > 0;
  }

  /**
   * Currency formatter shared by the stat tiles and the timeline rows.
   * Accepts a string (BigDecimal-as-string from the wire) or null.
   * Returns "—" for null/undefined so the template doesn't have to
   * conditional-render the value.
   */
  formatMoney(n: string | null | undefined): string {
    if (n == null) return '—';
    const v = Number(n);
    if (!Number.isFinite(v)) return '—';
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 0,
    }).format(v);
  }
}
