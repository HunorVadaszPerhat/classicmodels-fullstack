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
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import {
  Chart,
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  ChartConfiguration,
  DoughnutController,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';

import { DashboardService, SalesDashboard } from './dashboard.service';

// Chart.js v4 ships as a tree-shakable library — the controllers,
// scales, elements, and plugins you use must be registered explicitly.
// Doing it once at module load is the recommended pattern, and it
// makes the bundle smaller because tree-shaking can drop everything
// we don't import.
//
// (If we add a new chart type later — pie, polar area, radar, scatter —
// remember to add the relevant controller/element/scale to this list.)
Chart.register(
  // Controllers — one per chart "type" we use
  LineController,
  BarController,
  DoughnutController,

  // Scales — line + bar use linear/category axes; doughnut needs none
  LinearScale,
  CategoryScale,

  // Elements — geometric primitives
  LineElement,
  PointElement,
  BarElement,
  ArcElement,

  // Plugins
  Legend,
  Tooltip,
  Filler,
);

/**
 * Sales dashboard — KPIs at the top, four charts below.
 *
 * <h3>Architecture</h3>
 *
 * <p>One HTTP call fetches the entire snapshot
 * (see {@link SalesDashboard}). The component then creates four
 * Chart.js instances, each bound to a different {@code <canvas>}
 * referenced via {@link ViewChild}. Charts are kept in instance
 * variables so we can {@link Chart#update} them without recreating
 * the canvases on the next refresh.</p>
 *
 * <h3>Why Chart.js, not D3?</h3>
 *
 * <p>D3 (Feature 11) is a toolkit you assemble yourself. Chart.js is
 * a chart library — it has opinions about what a "line chart" looks
 * like, handles tooltips and legends out of the box, and trades
 * flexibility for productivity. For an org-chart visualization, you
 * <i>need</i> D3's flexibility. For an "X by month" line chart, the
 * eight lines below to configure Chart.js beat re-implementing axes,
 * grid lines, and a legend in D3.</p>
 *
 * <p>Both are good — the choice between them is "do I have a chart
 * Chart.js can already do?" If yes, save time and use it.</p>
 */
@Component({
  standalone: true,
  selector: 'app-dashboard',
  imports: [
    CommonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './dashboard.component.html',
  styles: [`
    .page { padding: 0.5rem; }

    .header {
      display: flex; align-items: center; gap: 0.75rem;
      margin-bottom: 1rem;
    }
    .header h2 { margin: 0; flex: 1; }

    .loading, .error {
      padding: 1rem 0;
      display: flex; align-items: center; gap: 0.75rem;
    }
    .error { color: #b71c1c; }

    /*
      KPI tiles — auto-fitting grid that wraps on narrow screens.
      minmax(200px, 1fr) means each card is at least 200px wide and
      grows to fill leftover space; auto-fit collapses empty tracks.
    */
    .kpi-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-bottom: 1.25rem;
    }
    .kpi-card { padding: 1rem; }
    .kpi-label {
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      display: flex; align-items: center; gap: 0.4rem;
    }
    .kpi-label mat-icon {
      font-size: 18px; height: 18px; width: 18px;
      color: rgba(0, 0, 0, 0.45);
    }
    .kpi-value {
      font-size: 1.75rem; font-weight: 500;
      margin-top: 0.4rem;
      color: #1a237e;
    }

    /* Two-up grid for the charts; collapses to 1-up on narrow screens. */
    .charts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
      gap: 1rem;
    }
    .chart-card { padding: 1rem; }
    .chart-title {
      font-size: 1rem; font-weight: 500;
      margin-bottom: 0.6rem;
    }

    /*
      Canvases need an explicit height; Chart.js sizes itself to the
      parent's box. responsive: true + maintainAspectRatio: false in
      the chart options lets it stretch horizontally too.
    */
    .chart-canvas-wrap { position: relative; height: 280px; }

    .footer-note {
      margin-top: 1.5rem;
      font-size: 0.8rem;
      color: rgba(0, 0, 0, 0.55);
    }
  `],
})
export class DashboardComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly dashboardService = inject(DashboardService);
  private readonly router = inject(Router);

  // ---- Canvas refs (one per chart) ------------------------------------
  @ViewChild('revenueByMonthCanvas')   revenueByMonthRef!:   ElementRef<HTMLCanvasElement>;
  @ViewChild('productLineCanvas')      productLineRef!:      ElementRef<HTMLCanvasElement>;
  @ViewChild('topCustomersCanvas')     topCustomersRef!:     ElementRef<HTMLCanvasElement>;
  @ViewChild('ordersByStatusCanvas')   ordersByStatusRef!:   ElementRef<HTMLCanvasElement>;

  // ---- Chart.js instances ---------------------------------------------
  // Stored so we can call .update(...) when fresh data arrives instead
  // of destroying and recreating the chart (which would be visually
  // jarring and waste DOM work).
  private revenueByMonthChart?:   Chart;
  private productLineChart?:      Chart;
  private topCustomersChart?:     Chart;
  private ordersByStatusChart?:   Chart;

  // ---- UI state -------------------------------------------------------
  data    = signal<SalesDashboard | undefined>(undefined);
  loading = signal(true);
  error   = signal<string | undefined>(undefined);

  /** Stable color palette — referenced from multiple chart configs. */
  private readonly palette = [
    '#1a237e', '#1565c0', '#2e7d32', '#ef6c00',
    '#6a1b9a', '#ad1457', '#00838f', '#558b2f',
    '#f9a825', '#4527a0',
  ];

  ngOnInit(): void {
    this.fetch();
  }

  ngAfterViewInit(): void {
    // Charts can only be created once the canvases exist. If data
    // arrived before view init, build them now.
    if (this.data()) this.renderAllCharts();
  }

  ngOnDestroy(): void {
    // Chart.js doesn't auto-clean if the canvas leaves the DOM during
    // navigation — destroy explicitly to free the WebGL/2D context
    // and detach event listeners.
    this.revenueByMonthChart?.destroy();
    this.productLineChart?.destroy();
    this.topCustomersChart?.destroy();
    this.ordersByStatusChart?.destroy();
  }

  refresh(): void {
    this.fetch();
  }

  private fetch(): void {
    this.loading.set(true);
    this.error.set(undefined);

    this.dashboardService.getSalesDashboard().subscribe({
      next: data => {
        this.data.set(data);
        this.loading.set(false);
        // Defer one frame so the @if(loading()) block has time to
        // un-hide the canvases before Chart.js measures them. Same
        // bug we saw on the org chart — getBoundingClientRect would
        // otherwise report 0×0.
        requestAnimationFrame(() => this.renderAllCharts());
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load dashboard');
        this.loading.set(false);
      },
    });
  }

  // ---- Number formatting helpers (called from the template) -----------

  /**
   * "$2.4M" for big numbers; "$1,234" for small. Locale-aware.
   *
   * <p>Accepts {@code null} / {@code undefined} because Chart.js v4
   * types tooltip-context numerics as {@code number | null}. Returning
   * an em-dash for missing values is fine in tooltips and KPI tiles
   * alike.</p>
   */
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

  // ---- Chart construction --------------------------------------------

  private renderAllCharts(): void {
    const d = this.data();
    if (!d) return;
    this.renderRevenueByMonth(d);
    this.renderProductLine(d);
    this.renderTopCustomers(d);
    this.renderOrdersByStatus(d);
  }

  /**
   * Line chart of revenue per calendar month.
   *
   * <p>{@code maintainAspectRatio: false} is the magic that lets the
   * chart fill its (CSS-sized) parent. Without it, Chart.js picks an
   * arbitrary aspect ratio and ignores parent height.</p>
   *
   * <p>{@code fill: true} + a translucent {@code backgroundColor}
   * shades the area under the line — turns a "spline" into an
   * "area chart" without a separate chart type.</p>
   */
  private renderRevenueByMonth(d: SalesDashboard): void {
    if (!this.revenueByMonthRef) return;

    const labels = d.revenueByMonth.map(r => r.month);
    const values = d.revenueByMonth.map(r => r.revenue);

    const config: ChartConfiguration = {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Revenue',
          data: values,
          borderColor: this.palette[0],
          backgroundColor: 'rgba(26, 35, 126, 0.12)',
          tension: 0.3,
          fill: true,
          pointRadius: 3,
          pointHoverRadius: 5,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: { label: ctx => '  ' + this.formatMoney(ctx.parsed.y) },
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

    if (this.revenueByMonthChart) {
      // Update path: mutate the chart's data and call update(). This
      // animates the change rather than tearing down the canvas.
      this.revenueByMonthChart.data.labels = labels;
      this.revenueByMonthChart.data.datasets[0].data = values;
      this.revenueByMonthChart.update();
    } else {
      this.revenueByMonthChart = new Chart(this.revenueByMonthRef.nativeElement, config);
    }
  }

  /**
   * Doughnut chart of revenue by product line. Doughnut and Pie are
   * the same chart type with different {@code cutout} values; doughnut
   * happens to be more readable because the labels can sit beside the
   * ring rather than overlap a solid pie.
   */
  private renderProductLine(d: SalesDashboard): void {
    if (!this.productLineRef) return;

    const labels = d.revenueByProductLine.map(r => r.productLine);
    const values = d.revenueByProductLine.map(r => r.revenue);

    const config: ChartConfiguration = {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data: values,
          backgroundColor: this.palette,
          borderWidth: 1,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'right' },
          tooltip: {
            callbacks: {
              label: ctx => ` ${ctx.label}: ${this.formatMoney(ctx.parsed)}`,
            },
          },
        },
      },
    };

    if (this.productLineChart) {
      this.productLineChart.data.labels = labels;
      this.productLineChart.data.datasets[0].data = values;
      this.productLineChart.update();
    } else {
      this.productLineChart = new Chart(this.productLineRef.nativeElement, config);
    }
  }

  /**
   * Horizontal bar chart of the top 10 customers by revenue. The
   * {@code indexAxis: 'y'} flips Chart.js's normal "x is category"
   * orientation — long customer names along the y-axis read better
   * than rotated -45° on the x-axis.
   */
  private renderTopCustomers(d: SalesDashboard): void {
    if (!this.topCustomersRef) return;

    const labels = d.topCustomers.map(c => c.customerName);
    const values = d.topCustomers.map(c => c.revenue);
    const ids    = d.topCustomers.map(c => c.customerNumber);

    const config: ChartConfiguration = {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Revenue',
          data: values,
          backgroundColor: this.palette[1],
          borderRadius: 3,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        indexAxis: 'y',
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: { label: ctx => '  ' + this.formatMoney(ctx.parsed.x) },
          },
        },
        scales: {
          x: {
            beginAtZero: true,
            ticks: { callback: v => this.formatMoney(v as number) },
          },
        },
        // Click a bar to navigate to that customer's detail page.
        onClick: (_event, elements) => {
          if (!elements.length) return;
          const idx = elements[0].index;
          this.router.navigate(['/customers', ids[idx]]);
        },
      },
    };

    if (this.topCustomersChart) {
      this.topCustomersChart.data.labels = labels;
      this.topCustomersChart.data.datasets[0].data = values;
      this.topCustomersChart.update();
    } else {
      this.topCustomersChart = new Chart(this.topCustomersRef.nativeElement, config);
    }
  }

  /** Doughnut for the order-status mix. Mirrors the product-line chart. */
  private renderOrdersByStatus(d: SalesDashboard): void {
    if (!this.ordersByStatusRef) return;

    const labels = Object.keys(d.ordersByStatus);
    const values = Object.values(d.ordersByStatus);

    const config: ChartConfiguration = {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data: values,
          backgroundColor: this.palette,
          borderWidth: 1,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'right' },
          tooltip: {
            callbacks: {
              label: ctx => ` ${ctx.label}: ${this.formatNumber(ctx.parsed)} orders`,
            },
          },
        },
      },
    };

    if (this.ordersByStatusChart) {
      this.ordersByStatusChart.data.labels = labels;
      this.ordersByStatusChart.data.datasets[0].data = values;
      this.ordersByStatusChart.update();
    } else {
      this.ordersByStatusChart = new Chart(this.ordersByStatusRef.nativeElement, config);
    }
  }
}
