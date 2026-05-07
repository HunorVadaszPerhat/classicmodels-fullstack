import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OfficeService, Office } from './office.service';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

import { MapPoint, MarkersMapComponent } from '../shared/markers-map.component';

@Component({
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    RouterLink,
    MatButtonModule,
    MarkersMapComponent,
  ],
  templateUrl: './office-list.component.html',
  styles: [`
    .map-section { margin-bottom: 1.5rem; }
    .map-section h3 {
      margin: 0 0 0.5rem;
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: rgba(0, 0, 0, 0.55);
    }
  `],
})
export class OfficeListComponent implements OnInit {
  private readonly service = inject(OfficeService);
  offices = signal<Office[]>([]);
  total = signal(0);
  loading = signal(false);
  error = signal<string | undefined>(undefined);
  displayedColumns = ['officeCode','city','phone','country','actions'];

  /**
   * Derive the marker list reactively from the offices signal. Only
   * offices that have lat/lng become markers — un-geocoded rows show
   * up in the table below but not on the map.
   *
   * <p>computed() is Angular's signal-derivative primitive: the
   * function re-runs (and the result is cached) whenever any signal it
   * reads changes. So when the offices list arrives from the API, the
   * markers signal updates automatically and the map re-renders without
   * any imperative code.</p>
   */
  readonly markers = computed<MapPoint[]>(() =>
    this.offices()
      .filter(o => o.lat != null && o.lng != null)
      .map(o => ({
        id: o.officeCode,
        lat: o.lat!,
        lng: o.lng!,
        popupHtml: this.buildPopup(o),
      })),
  );

  ngOnInit() {
    this.load();
  }

  load(event?: PageEvent | Sort) {
    this.loading.set(true);
    this.service.list().subscribe({
      next: data => { this.offices.set(data); this.total.set(data.length); this.loading.set(false); },
      error: err => { this.error.set(err.message); this.loading.set(false); }
    });
  }

  remove(id: string) {
    this.service.delete(id).subscribe(() => this.load());
  }

  // -- Popup helpers (same pattern as F1, kept local to this component) --

  private buildPopup(office: Office): string {
    const e = (s: string | null | undefined) => this.escapeHtml(s);
    const route = `/offices/${encodeURIComponent(office.officeCode)}`;
    return `
      <div style="min-width: 160px;">
        <strong>${e(office.city)}</strong>
        <span style="color: rgba(0,0,0,0.6);">— ${e(office.country)}</span><br>
        ${e(office.addressLine1)}<br>
        <a href="${route}">View office →</a>
      </div>
    `;
  }

  private escapeHtml(s: string | null | undefined): string {
    if (s == null) return '';
    return s.replace(/[&<>"']/g, c => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;',
      '"': '&quot;', "'": '&#39;',
    } as const)[c as '&' | '<' | '>' | '"' | "'"]);
  }
}
