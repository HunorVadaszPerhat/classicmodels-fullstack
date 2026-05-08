import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { CustomerService, CustomerCreditStatus } from './customer.service';

/**
 * Full-page list of customers near or over their credit limit (C13).
 *
 * <p>Reached either from the dashboard's Credit-alerts KPI tile or
 * the customer-list toolbar. Renders a sortable table with one row
 * per at-risk customer; each row links to the customer's detail
 * page.</p>
 *
 * <h3>What's displayed</h3>
 *
 * <p>Three columns by default — Customer, Credit limit, Outstanding,
 * Utilisation, Status. The status column carries a colour-coded chip
 * matching the detail-page chip (orange for NEAR, red for OVER), so
 * users can scan a long list and pick out the most urgent ones first.</p>
 *
 * <h3>Why no client-side filtering / paging</h3>
 *
 * <p>The backend already filters server-side to the at-risk subset.
 * Realistically that's tens of rows even at 10k customers — adding
 * pagination on top would be empty UI most of the time. If alert
 * counts ever genuinely overwhelm the page, we'll layer
 * pagination on; not before.</p>
 */
@Component({
  standalone: true,
  selector: 'app-customer-credit-alerts',
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-head">
      <h2>Credit alerts</h2>
      <a mat-stroked-button routerLink="..">
        <mat-icon>list</mat-icon>
        Back to customers
      </a>
    </div>

    @if (loading()) {
      <div class="loading">
        <mat-spinner diameter="32"></mat-spinner>
        <span>Loading credit alerts…</span>
      </div>
    }

    @if (!loading() && error()) {
      <mat-card class="error-card">
        <mat-card-content>
          <mat-icon class="error-icon">error_outline</mat-icon>
          {{ error() }}
        </mat-card-content>
      </mat-card>
    }

    @if (!loading() && !error()) {
      <div class="status-bar">
        <span>
          <strong>{{ overCount() }}</strong> over limit
          ·
          <strong>{{ nearCount() }}</strong> near limit
          @if (alerts().length === 0) {
            <span class="muted"> — nothing flagged 🎉</span>
          }
        </span>
      </div>

      @if (alerts().length === 0) {
        <div class="empty">
          <p>Every active customer is below 80% of their credit limit.</p>
          <p class="muted">No action needed.</p>
        </div>
      } @else {
        <table mat-table [dataSource]="alerts()" class="alerts-table">
          <ng-container matColumnDef="customer">
            <th mat-header-cell *matHeaderCellDef>Customer</th>
            <td mat-cell *matCellDef="let a">
              <a [routerLink]="['..', a.customerNumber]" class="row-link">
                {{ a.customerName }}
                <span class="muted-id">#{{ a.customerNumber }}</span>
              </a>
            </td>
          </ng-container>

          <ng-container matColumnDef="creditLimit">
            <th mat-header-cell *matHeaderCellDef>Credit limit</th>
            <td mat-cell *matCellDef="let a" class="money">
              {{ formatMoney(a.creditLimit) }}
            </td>
          </ng-container>

          <ng-container matColumnDef="outstanding">
            <th mat-header-cell *matHeaderCellDef>Outstanding</th>
            <td mat-cell *matCellDef="let a" class="money">
              {{ formatMoney(a.outstandingBalance) }}
            </td>
          </ng-container>

          <ng-container matColumnDef="utilization">
            <th mat-header-cell *matHeaderCellDef>Utilisation</th>
            <td mat-cell *matCellDef="let a">
              {{ formatPercent(a.utilization) }}
            </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let a">
              @switch (a.status) {
                @case ('NEAR_LIMIT') {
                  <span class="credit-chip near">
                    <mat-icon>warning</mat-icon>
                    Near limit
                  </span>
                }
                @case ('OVER_LIMIT') {
                  <span class="credit-chip over">
                    <mat-icon>error</mat-icon>
                    Over limit
                  </span>
                }
              }
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>
      }
    }
  `,
  styles: [`
    .page-head {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .page-head h2 { margin: 0; }

    .loading {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 2rem 0; color: rgba(0, 0, 0, 0.6);
    }
    .error-card { border-left: 4px solid #b71c1c; max-width: 720px; }
    .error-icon { color: #b71c1c; vertical-align: middle; margin-right: 0.5rem; }

    .status-bar {
      padding: 0.5rem 0.75rem;
      margin-bottom: 0.75rem;
      background: #fff3e0;
      border-radius: 4px;
      color: #e65100;
      font-size: 0.95rem;
    }
    .muted { color: rgba(0, 0, 0, 0.55); }
    .muted-id { color: rgba(0, 0, 0, 0.5); margin-left: 0.4rem; font-size: 0.85em; }

    .empty {
      padding: 3rem 1rem;
      text-align: center;
      border: 1px dashed rgba(0, 0, 0, 0.18);
      border-radius: 4px;
      color: rgba(0, 0, 0, 0.7);
    }
    .empty p { margin: 0.4rem 0; }

    .alerts-table { width: 100%; }
    .row-link { color: #1565c0; text-decoration: none; }
    .row-link:hover { text-decoration: underline; }

    .money { font-variant-numeric: tabular-nums; }

    /* Same chip styles as customer-detail. Kept inline here rather
       than going global; the alerts page is the only other consumer
       and inlining keeps each component's visual contract self-
       contained. */
    .credit-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      padding: 1px 8px;
      border-radius: 10px;
      font-size: 0.75rem;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .credit-chip.near { background: #fff3e0; color: #e65100; }
    .credit-chip.over { background: #fdecea; color: #b71c1c; }
    .credit-chip mat-icon {
      font-size: 14px; height: 14px; width: 14px;
    }
  `],
})
export class CustomerCreditAlertsComponent implements OnInit {
  private readonly customers = inject(CustomerService);

  alerts = signal<CustomerCreditStatus[]>([]);
  loading = signal(true);
  error = signal<string | undefined>(undefined);

  displayedColumns = ['customer', 'creditLimit', 'outstanding', 'utilization', 'status'];

  overCount = computed(() => this.alerts().filter(a => a.status === 'OVER_LIMIT').length);
  nearCount = computed(() => this.alerts().filter(a => a.status === 'NEAR_LIMIT').length);

  ngOnInit(): void {
    this.customers.getCreditAlerts().subscribe({
      next: alerts => {
        this.alerts.set(alerts);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load alerts');
        this.loading.set(false);
      },
    });
  }

  formatMoney(s: string | null | undefined): string {
    if (s == null) return '—';
    const v = Number(s);
    if (!Number.isFinite(v)) return '—';
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 0,
    }).format(v);
  }

  formatPercent(s: string | null | undefined): string {
    if (s == null) return '—';
    const v = Number(s);
    if (!Number.isFinite(v)) return '—';
    return Math.round(v * 100) + '%';
  }
}
