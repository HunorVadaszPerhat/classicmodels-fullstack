import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription, forkJoin } from 'rxjs';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { CustomerService, CustomerMapPoint } from './customer.service';
import { CustomerEventsService } from '../realtime/customer-events.service';
import { MarkersMapComponent, MapPoint } from '../shared/markers-map.component';

/**
 * Full-page map showing every active, geocoded customer in the
 * dataset. Markers are coloured by sales-rep assignment status —
 * a quick visual for "where do we have territory gaps?"
 *
 * <h3>Why a separate page from the list</h3>
 *
 * <p>The list (C2) is for browsing rows: pagination, sort, search,
 * row-level actions. The map answers a different question: "where
 * are these customers, geographically?" Two views over the same
 * data, optimised for different tasks. Same pattern as the offices
 * had a separate global map (F3).</p>
 *
 * <h3>What's plotted</h3>
 *
 * <p>Active customers (the same {@code WHERE active = 1} filter the
 * list uses) that have been geocoded (have non-null lat/lng).
 * Un-geocoded customers are excluded server-side; the page reports
 * "X of Y geocoded" so the gap is visible. Use the Geocode button
 * on each customer's detail page to fill in the missing coords.</p>
 *
 * <h3>Status colours</h3>
 *
 * <ul>
 *   <li><b>Blue</b> — has an assigned sales rep (default).</li>
 *   <li><b>Orange</b> — no rep assigned (territory gap).</li>
 * </ul>
 *
 * <h3>Live updates</h3>
 *
 * <p>Subscribed to the {@link CustomerEventsService} same as the list
 * — any create / update / delete / geocode triggers a refresh, so
 * markers appear / move / disappear in near-real-time across browser
 * tabs.</p>
 */
@Component({
  standalone: true,
  selector: 'app-customer-map',
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MarkersMapComponent,
  ],
  template: `
    <div class="page-head">
      <h2>Customers — map</h2>
      <a mat-stroked-button routerLink="..">
        <mat-icon>list</mat-icon>
        Back to list
      </a>
    </div>

    @if (loading()) {
      <div class="loading">
        <mat-spinner diameter="32"></mat-spinner>
        <span>Loading map…</span>
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
      <!--
        Status bar — always visible. Tells the user how many
        customers are plotted vs. total active. The colour-coded
        breakdown also serves as the legend (no separate Legend
        card is needed).
      -->
      <div class="status-bar">
        <span>
          <strong>{{ mapPoints().length }}</strong> of
          <strong>{{ activeCount() }}</strong> active customers geocoded
          @if (mapPoints().length < activeCount()) {
            <span class="muted">
              ({{ activeCount() - mapPoints().length }} un-geocoded —
              use the per-customer Geocode button on the detail page)
            </span>
          }
        </span>
      </div>

      <!-- Legend + the map. Two-column layout on wide screens, stacks below. -->
      <div class="map-row">
        <div class="legend">
          <h3>Legend</h3>
          <div class="legend-row">
            <span class="legend-dot customer-marker-assigned"></span>
            <div class="legend-text">
              <div><strong>{{ assignedCount() }}</strong> assigned</div>
              <div class="muted">Has a sales rep</div>
            </div>
          </div>
          <div class="legend-row">
            <span class="legend-dot customer-marker-unassigned"></span>
            <div class="legend-text">
              <div><strong>{{ unassignedCount() }}</strong> unassigned</div>
              <div class="muted">No sales rep — territory gap</div>
            </div>
          </div>
        </div>

        <div class="map-host">
          @if (markerPoints().length > 0) {
            <app-markers-map [points]="markerPoints()"></app-markers-map>
          } @else {
            <div class="empty">
              <p>No geocoded customers yet.</p>
              <p class="muted">
                Open any customer's detail page and click
                <strong>Geocode address</strong> to add them to the map.
              </p>
            </div>
          }
        </div>
      </div>
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
    .error-icon {
      color: #b71c1c;
      vertical-align: middle;
      margin-right: 0.5rem;
    }

    .status-bar {
      padding: 0.5rem 0.75rem;
      margin-bottom: 0.75rem;
      background: #e3f2fd;
      border-radius: 4px;
      color: #0d47a1;
      font-size: 0.95rem;
    }

    /* Two-column on wide screens — legend on the right (~220px),
       map fills the rest. Stacks below on narrow screens. */
    .map-row {
      display: grid;
      grid-template-columns: 1fr 220px;
      gap: 1rem;
    }
    @media (max-width: 720px) {
      .map-row { grid-template-columns: 1fr; }
    }

    .map-host { min-height: 480px; }
    /* Override the default 360px from the shared markers-map for this
       full-page view — the map is the main content, give it room. */
    .map-host ::ng-deep .map-host {
      height: 480px !important;
    }

    .legend {
      padding: 0.75rem 1rem;
      background: #fafafa;
      border: 1px solid rgba(0, 0, 0, 0.08);
      border-radius: 4px;
      align-self: start;
    }
    .legend h3 {
      margin: 0 0 0.75rem;
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: rgba(0, 0, 0, 0.55);
      font-weight: 600;
    }
    .legend-row {
      display: flex; align-items: flex-start; gap: 0.6rem;
      margin-bottom: 0.6rem;
    }
    .legend-row:last-child { margin-bottom: 0; }
    .legend-dot {
      flex: 0 0 auto;
      width: 14px;
      height: 14px;
      border-radius: 50%;
      margin-top: 4px;
      border: 2px solid #fff;
      box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.18);
    }
    .legend-text { font-size: 0.9rem; line-height: 1.3; }

    .muted { color: rgba(0, 0, 0, 0.55); font-size: 0.85rem; }

    .empty {
      padding: 3rem 1rem;
      text-align: center;
      border: 1px dashed rgba(0, 0, 0, 0.18);
      border-radius: 4px;
      color: rgba(0, 0, 0, 0.7);
    }
    .empty p { margin: 0.4rem 0; }
  `],
  // NOTE: the marker colour classes (.customer-marker-assigned,
  // .customer-marker-unassigned) live in src/styles.scss because
  // Leaflet renders divIcons outside Angular's component tree and
  // component-scoped CSS doesn't reach them.
})
export class CustomerMapComponent implements OnInit, OnDestroy {
  private readonly customers = inject(CustomerService);
  private readonly liveEvents = inject(CustomerEventsService);

  /** Track the events-subscription so we can unsubscribe on destroy. */
  private eventsSub?: Subscription;

  loading = signal(true);
  error = signal<string | undefined>(undefined);

  mapPoints = signal<CustomerMapPoint[]>([]);
  activeCount = signal(0);

  /** Customers with an assigned sales rep. */
  assignedCount = computed(() =>
    this.mapPoints().filter(p => p.hasSalesRep).length);

  /** Customers without a sales rep — the "territory gap" segment. */
  unassignedCount = computed(() =>
    this.mapPoints().filter(p => !p.hasSalesRep).length);

  /**
   * MapPoint[] for the shared markers-map component. Builds the
   * popup HTML and assigns the colour class based on rep status.
   * Computed so it re-derives when {@link mapPoints} changes.
   */
  markerPoints = computed<MapPoint[]>(() => {
    return this.mapPoints().map(p => ({
      id: p.customerNumber,
      lat: Number(p.lat),
      lng: Number(p.lng),
      iconClass: p.hasSalesRep
        ? 'customer-marker-assigned'
        : 'customer-marker-unassigned',
      popupHtml: this.buildPopup(p),
    }));
  });

  ngOnInit(): void {
    this.load();

    // Re-fetch on any customer mutation (create / update / delete /
    // geocode). The events subscription debounces naturally — if a
    // bulk operation fires N events, the browser frame-coalesces the
    // load() calls.
    this.eventsSub = this.liveEvents.events$.subscribe(() => this.load());
  }

  ngOnDestroy(): void {
    this.eventsSub?.unsubscribe();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(undefined);

    forkJoin({
      points: this.customers.mapPoints(),
      total: this.customers.activeCount(),
    }).subscribe({
      next: ({ points, total }) => {
        this.mapPoints.set(points);
        this.activeCount.set(total);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load map');
        this.loading.set(false);
      },
    });
  }

  /**
   * Popup HTML for a marker. Plain Leaflet renders this as raw HTML,
   * so any user-controlled value is escaped first. Same convention
   * as the customer-detail page's popup builder.
   */
  private buildPopup(p: CustomerMapPoint): string {
    const esc = (s: string | undefined | null) =>
      (s ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
      }[ch]!));
    const status = p.hasSalesRep ? 'assigned' : '<strong>unassigned</strong>';
    // Anchor to the detail page. We can't use Angular routerLink here
    // because Leaflet renders the popup as raw HTML outside the Angular
    // tree; a plain <a href> still works because the Angular router
    // intercepts same-origin navigations.
    return `
      <strong>${esc(p.customerName)}</strong><br>
      ${esc(p.city)}, ${esc(p.country)}<br>
      <span style="color: rgba(0, 0, 0, 0.55); font-size: 0.85em;">${status}</span><br>
      <a href="/customers/${p.customerNumber}">View details →</a>
    `;
  }
}
