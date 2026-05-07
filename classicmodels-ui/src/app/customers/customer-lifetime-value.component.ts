import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  Chart,
  BarController,
  BarElement,
  CategoryScale,
  ChartConfiguration,
  Legend,
  LinearScale,
  Tooltip,
} from 'chart.js';

import { CustomerLifetimeValue, CustomerSegment, CustomerService } from './customer.service';

// Same registration pattern as the dashboard. Chart.js v4 requires
// explicit registration of every controller/scale/element in use.
Chart.register(
  BarController, BarElement,
  CategoryScale, LinearScale,
  Legend, Tooltip,
);

/**
 * Per-customer CLV / RFM detail page.
 *
 * <p>Shown at {@code /customers/:id/lifetime-value}. Pulls one
 * snapshot from the backend (which already does the RFM math via
 * SQL window functions) and renders it across four bands:</p>
 *
 * <ol>
 *   <li><b>Header</b> — name + segment badge with explanation tooltip.</li>
 *   <li><b>KPI tiles</b> — total revenue, orders, AOV, predicted CLV.</li>
 *   <li><b>RFM scores</b> — three small cards showing 1-4 ratings.</li>
 *   <li><b>Order timeline chart</b> — bar chart, one bar per order.</li>
 * </ol>
 *
 * <h3>Why a separate page, not a tab on customer detail?</h3>
 *
 * <p>CLV is a <b>derived</b> view — analytical, not transactional.
 * Mixing it with the customer's static profile would conflate two
 * different mental models (record-of-truth vs. computed insight).
 * Real-world CRMs typically have a separate "insights" or
 * "customer intelligence" surface for the same reason.</p>
 */
@Component({
  standalone: true,
  selector: 'app-customer-lifetime-value',
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './customer-lifetime-value.component.html',
  styles: [`
    .page { padding: 0.5rem; }

    .back-link {
      display: inline-flex; align-items: center; gap: 0.25rem;
      margin-bottom: 0.75rem;
      text-decoration: none; color: rgba(0,0,0,0.7);
    }
    .back-link mat-icon { font-size: 18px; height: 18px; width: 18px; }

    .header {
      display: flex; align-items: center; gap: 0.75rem;
      margin-bottom: 1rem; flex-wrap: wrap;
    }
    .header h2 { margin: 0; flex: 1; min-width: 200px; }

    /* Segment badge — color picks itself based on segment via [class]. */
    .segment-badge {
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 0.85rem; font-weight: 500;
      white-space: nowrap;
      cursor: help;             /* hint that there's a tooltip */
    }
    .segment-badge.CHAMPIONS           { background: #e6f4ea; color: #1b5e20; }
    .segment-badge.LOYAL               { background: #e3f2fd; color: #0d47a1; }
    .segment-badge.POTENTIAL_LOYALISTS { background: #ede7f6; color: #4527a0; }
    .segment-badge.NEW_CUSTOMERS       { background: #fff3e0; color: #e65100; }
    .segment-badge.AT_RISK             { background: #fff8e1; color: #ff6f00; }
    .segment-badge.CANT_LOSE           { background: #fbe9e7; color: #bf360c; }
    .segment-badge.HIBERNATING         { background: #eceff1; color: #455a64; }
    .segment-badge.LOST                { background: #fdecea; color: #b71c1c; }

    .loading, .error {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 1rem 0;
    }
    .error { color: #b71c1c; }

    .kpi-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 1rem; margin-bottom: 1.25rem;
    }
    .kpi-card { padding: 1rem; }
    .kpi-label {
      color: rgba(0,0,0,0.6);
      font-size: 0.8rem;
      text-transform: uppercase; letter-spacing: 0.04em;
    }
    .kpi-value { font-size: 1.5rem; font-weight: 500; color: #1a237e; margin-top: 0.4rem; }
    .kpi-sub   { font-size: 0.8rem; color: rgba(0,0,0,0.55); margin-top: 0.2rem; }

    /* RFM trio — three little dial-like cards */
    .rfm-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 1rem; margin-bottom: 1.25rem;
    }
    .rfm-card { padding: 1rem; text-align: center; cursor: help; }
    .rfm-letter {
      font-size: 1.1rem; font-weight: 500;
      color: rgba(0,0,0,0.7); margin-bottom: 0.4rem;
    }
    .rfm-score {
      font-size: 2.4rem; font-weight: 600; color: #1a237e; line-height: 1;
    }
    .rfm-out-of {
      color: rgba(0,0,0,0.45); font-size: 1rem; vertical-align: top;
    }
    .rfm-meaning { color: rgba(0,0,0,0.6); font-size: 0.85rem; margin-top: 0.4rem; }

    .chart-card { padding: 1rem; margin-bottom: 1rem; }
    .chart-title { font-size: 1rem; font-weight: 500; margin-bottom: 0.6rem; }
    .chart-canvas-wrap { position: relative; height: 280px; }
  `],
})
export class CustomerLifetimeValueComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly customerService = inject(CustomerService);

  @ViewChild('historyCanvas') historyRef?: ElementRef<HTMLCanvasElement>;
  private historyChart?: Chart;

  loading = signal(true);
  error   = signal<string | undefined>(undefined);
  data    = signal<CustomerLifetimeValue | undefined>(undefined);

  /**
   * Human-readable explanation per segment, shown as a tooltip on the
   * badge. Kept here in the component so the marketing copy can be
   * tweaked without redeploying the backend.
   */
  private readonly segmentDescriptions: Record<CustomerSegment, string> = {
    CHAMPIONS:           'Top scores across recency, frequency, and spend. Best customers — reward and retain.',
    LOYAL:               'Frequent and high-spending; slightly less recent. Keep them engaged.',
    POTENTIAL_LOYALISTS: 'Recent activity with moderate frequency/spend. Nurture toward Champions.',
    NEW_CUSTOMERS:       'Recent buyers with only a few orders. Onboard and convert into repeat buyers.',
    AT_RISK:             'Used to be valuable but haven\'t bought lately. Win-back campaign target.',
    CANT_LOSE:           'High historic value going cold. Highest priority for re-engagement.',
    HIBERNATING:         'Low scores across the board. Inexpensive to email, low ROI per touch.',
    LOST:                'Disengaged across all three axes. Likely churned.',
  };

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id)) {
      this.error.set('Invalid customer id in URL');
      this.loading.set(false);
      return;
    }
    this.fetch(id);
  }

  ngAfterViewInit(): void {
    if (this.data()) this.renderChart();
  }

  ngOnDestroy(): void {
    this.historyChart?.destroy();
  }

  // -- helpers used in the template ---------------------------------

  formatMoney(n: number | null | undefined): string {
    if (n == null) return '—';
    if (Math.abs(n) >= 1_000_000) return '$' + (n / 1_000_000).toFixed(2) + 'M';
    if (Math.abs(n) >= 1_000)     return '$' + (n / 1_000).toFixed(1) + 'K';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD',
                                            maximumFractionDigits: 0 }).format(n);
  }

  formatNumber(n: number | null | undefined): string {
    return n != null ? new Intl.NumberFormat('en-US').format(n) : '—';
  }

  /** "1y 6m" / "8m" / "12d" — short tenure display. */
  formatDuration(days: number): string {
    if (days <= 0) return '0d';
    const years = Math.floor(days / 365);
    const months = Math.floor((days % 365) / 30);
    if (years > 0) return months > 0 ? `${years}y ${months}m` : `${years}y`;
    if (months > 0) return `${months}m`;
    return `${days}d`;
  }

  /** Pretty-print enum values: NEW_CUSTOMERS → "New Customers". */
  formatSegment(s: CustomerSegment): string {
    return s.replace('_', ' ').toLowerCase()
            .replace(/\b\w/g, c => c.toUpperCase());
  }

  segmentTooltip(s: CustomerSegment): string {
    return this.segmentDescriptions[s] ?? '';
  }

  /** Per-axis explanatory text shown under each RFM dial. */
  rfmMeaning(axis: 'r' | 'f' | 'm', score: number): string {
    const labels = ['', 'Low', 'Below avg.', 'Above avg.', 'High'];
    const label  = labels[score] ?? 'Unknown';
    const axisLabel =
        axis === 'r' ? 'recency'   :
        axis === 'f' ? 'frequency' :
                       'monetary value';
    return `${label} ${axisLabel}`;
  }

  // -- data + chart ----------------------------------------------------

  private fetch(id: number): void {
    this.loading.set(true);
    this.error.set(undefined);

    this.customerService.getLifetimeValue(id).subscribe({
      next: data => {
        this.data.set(data);
        this.loading.set(false);
        // Same one-frame defer as the dashboard charts: gives the
        // template's @if to flip from spinner to canvas before we try
        // to measure the canvas.
        requestAnimationFrame(() => this.renderChart());
      },
      error: err => {
        if (err?.status === 404) {
          this.error.set('This customer has no order history yet.');
        } else {
          this.error.set(err?.error?.detail ?? err?.error?.message ?? err?.message ?? 'Failed to load CLV');
        }
        this.loading.set(false);
      },
    });
  }

  /**
   * Bar chart of the order timeline. One bar per order, sized by
   * dollar amount. Status is shown in the tooltip; if we wanted to
   * encode it visually too we could color-by-status.
   */
  private renderChart(): void {
    const d = this.data();
    if (!d || !this.historyRef) return;

    const labels   = d.orderHistory.map(o => o.orderDate);
    const values   = d.orderHistory.map(o => o.amount);
    const tooltips = d.orderHistory.map(o => `Order #${o.orderNumber} (${o.status})`);

    const config: ChartConfiguration = {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Order amount',
          data: values,
          backgroundColor: '#1565c0',
          borderRadius: 3,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: items => tooltips[items[0].dataIndex] ?? '',
              label: ctx  => '  ' + this.formatMoney(ctx.parsed.y),
            },
          },
        },
        scales: {
          y: {
            beginAtZero: true,
            ticks: { callback: v => this.formatMoney(v as number) },
          },
        },
      },
    };

    if (this.historyChart) {
      this.historyChart.data.labels = labels;
      this.historyChart.data.datasets[0].data = values;
      this.historyChart.update();
    } else {
      this.historyChart = new Chart(this.historyRef.nativeElement, config);
    }
  }
}
