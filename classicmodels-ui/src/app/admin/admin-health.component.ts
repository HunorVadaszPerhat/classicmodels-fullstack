import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';

/**
 * Admin "system status" page — a thin UI on top of Spring Boot
 * Actuator's HTTP endpoints.
 *
 * <p>What's shown:</p>
 *
 * <ul>
 *   <li>Overall health badge (UP / DOWN), driven by
 *       {@code /actuator/health}.</li>
 *   <li>Per-component health rows (db, diskSpace, ping, ssl) with the
 *       same status indicator each.</li>
 *   <li>Build / runtime info from {@code /actuator/info}.</li>
 *   <li>A few metrics handpicked from {@code /actuator/metrics/{name}} —
 *       JVM memory, request count, custom counters we added.</li>
 * </ul>
 *
 * <p>Production teams usually wouldn't build this page themselves —
 * Grafana + Prometheus give you a much richer dashboard for free.
 * It's here mainly as a learning artefact and a "is the backend
 * alive?" quick check that doesn't require firing up a separate
 * monitoring stack.</p>
 */
@Component({
  standalone: true,
  selector: 'app-admin-health',
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="page">
      <div class="header">
        <h2>System status</h2>
        <button mat-stroked-button (click)="refresh()" [disabled]="loading()">
          <mat-icon>refresh</mat-icon> Refresh
        </button>
      </div>

      @if (loading() && !health()) {
        <div class="loading">
          <mat-spinner diameter="32"></mat-spinner>
          <span>Querying actuator…</span>
        </div>
      }

      @if (error()) {
        <div class="error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }

      @if (health(); as h) {
        <mat-card class="status-card">
          <div class="overall" [class.up]="h.status === 'UP'" [class.down]="h.status !== 'UP'">
            <mat-icon>{{ h.status === 'UP' ? 'check_circle' : 'error' }}</mat-icon>
            <span>{{ h.status }}</span>
          </div>

          @if (h.components) {
            <table class="components">
              <tr>
                <th>Component</th><th>Status</th><th>Details</th>
              </tr>
              @for (entry of objEntries(h.components); track entry.key) {
                <tr>
                  <td>{{ entry.key }}</td>
                  <td [class.up]="entry.value.status === 'UP'"
                      [class.down]="entry.value.status !== 'UP'">
                    {{ entry.value.status }}
                  </td>
                  <td class="details">{{ formatDetails(entry.value.details) }}</td>
                </tr>
              }
            </table>
          }
        </mat-card>
      }

      @if (info(); as i) {
        <mat-card class="info-card">
          <h3>Build info</h3>
          <pre>{{ i | json }}</pre>
        </mat-card>
      }

      @if (metrics().length) {
        <mat-card class="metrics-card">
          <h3>Selected metrics</h3>
          <table class="metrics">
            <tr><th>Metric</th><th>Value</th></tr>
            @for (m of metrics(); track m.name) {
              <tr>
                <td>{{ m.name }}</td>
                <td>{{ formatNumber(m.value) }} {{ m.unit }}</td>
              </tr>
            }
          </table>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .page { padding: 0.5rem; }

    .header {
      display: flex; align-items: center; gap: 0.75rem;
      margin-bottom: 1rem;
    }
    .header h2 { margin: 0; flex: 1; }

    .loading, .error {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 1rem 0;
    }
    .error { color: #b71c1c; }

    .status-card, .info-card, .metrics-card {
      padding: 1rem; margin-bottom: 1rem;
    }
    h3 { margin: 0 0 0.6rem; font-size: 1rem; font-weight: 500; }

    .overall {
      display: inline-flex; align-items: center; gap: 0.4rem;
      padding: 0.4rem 0.8rem;
      border-radius: 4px;
      font-weight: 500;
      margin-bottom: 0.75rem;
    }
    .overall.up   { background: #e6f4ea; color: #1b5e20; }
    .overall.down { background: #fdecea; color: #b71c1c; }

    table { width: 100%; border-collapse: collapse; }
    th, td {
      text-align: left; padding: 0.4rem 0.6rem;
      border-bottom: 1px solid rgba(0, 0, 0, 0.06);
    }
    th { color: rgba(0, 0, 0, 0.6); font-weight: 500; font-size: 0.85rem; }
    td.up   { color: #1b5e20; font-weight: 500; }
    td.down { color: #b71c1c; font-weight: 500; }
    td.details { color: rgba(0, 0, 0, 0.6); font-size: 0.85rem; word-break: break-word; }

    pre {
      background: #fafafa;
      border: 1px solid rgba(0, 0, 0, 0.06);
      border-radius: 4px;
      padding: 0.75rem;
      font-size: 0.8rem;
      overflow-x: auto;
      white-space: pre-wrap;
      word-break: break-word;
    }
  `],
})
export class AdminHealthComponent implements OnInit {
  private readonly http = inject(HttpClient);

  loading = signal(false);
  error   = signal<string | undefined>(undefined);
  health  = signal<any | undefined>(undefined);
  info    = signal<any | undefined>(undefined);

  /**
   * Hand-picked list of metrics to surface. Add or remove names from
   * here to change what shows up; full list lives at
   * {@code /actuator/metrics}.
   */
  metrics = signal<{ name: string; value: number; unit?: string }[]>([]);

  private readonly METRIC_NAMES: { key: string; label: string }[] = [
    { key: 'jvm.memory.used',                label: 'JVM heap used (bytes)' },
    { key: 'process.cpu.usage',              label: 'CPU usage' },
    { key: 'http.server.requests',           label: 'HTTP requests (total count)' },
    { key: 'classicmodels.customers.count',  label: 'Customers (rows in DB)' },
    { key: 'hikaricp.connections.active',    label: 'DB connections (active)' },
  ];

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.error.set(undefined);

    // Fire health, info, and N metric calls in parallel. forkJoin
    // resolves once all complete; if any one fails the whole batch
    // errors. For a status page that's fine — we'd rather see no
    // partial picture than a confusing half-loaded one.
    forkJoin({
      health:  this.http.get<any>('/api/v1/actuator/health'),
      info:    this.http.get<any>('/api/v1/actuator/info'),
      metrics: forkJoin(this.METRIC_NAMES.map(m =>
        this.http.get<any>(`/api/v1/actuator/metrics/${m.key}`).toPromise()
          .then(r => ({ name: m.label, value: r?.measurements?.[0]?.value ?? 0,
                        unit: r?.baseUnit })),
      )),
    }).subscribe({
      next: ({ health, info, metrics }) => {
        this.health.set(health);
        this.info.set(info);
        this.metrics.set(metrics);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message
            ?? 'Could not reach actuator endpoints. Make sure you are signed in as ADMIN.');
        this.loading.set(false);
      },
    });
  }

  // --- template helpers -----------------------------------------------

  objEntries(obj: Record<string, any>): { key: string; value: any }[] {
    return Object.entries(obj ?? {}).map(([key, value]) => ({ key, value }));
  }

  formatDetails(d: any): string {
    if (!d) return '';
    return Object.entries(d).map(([k, v]) => `${k}=${v}`).join(', ');
  }

  formatNumber(n: number): string {
    if (n == null) return '—';
    if (n > 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
    if (n > 1_000)     return (n / 1_000).toFixed(1) + 'K';
    if (n < 1 && n > 0) return n.toFixed(4);
    return Math.round(n).toString();
  }
}
