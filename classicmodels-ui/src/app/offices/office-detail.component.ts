import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpParams } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { MatChipsModule } from '@angular/material/chips';

import { Office, OfficeService } from './office.service';
import { Employee, EmployeeService } from '../employees/employee.service';
import { MiniMapComponent } from '../shared/mini-map.component';

/**
 * Office detail page. Shows the office's address + phone, the map, and
 * a list of employees who work there.
 *
 * <p>Two requests fire in parallel: GET the office, GET the employees
 * filtered by officeCode. forkJoin waits for both to complete before
 * the page renders, so the layout doesn't shuffle as data arrives.</p>
 */
@Component({
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatListModule,
    MatChipsModule,
    MiniMapComponent,
  ],
  templateUrl: './office-detail.component.html',
  styles: [`
    .back-link {
      display: inline-flex; align-items: center; gap: 0.25rem;
      margin-bottom: 1rem; text-decoration: none;
      color: rgba(0, 0, 0, 0.7);
    }
    .back-link:hover { color: rgba(0, 0, 0, 0.9); }
    .back-link mat-icon { font-size: 18px; height: 18px; width: 18px; }

    .loading {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 2rem 0; color: rgba(0, 0, 0, 0.6);
    }

    .detail-card { max-width: 720px; }

    .fields {
      display: grid;
      grid-template-columns: minmax(120px, max-content) 1fr;
      column-gap: 1.25rem;
      row-gap: 0.5rem;
      margin: 0;
    }
    .fields dt {
      display: flex; align-items: center; gap: 0.4rem;
      color: rgba(0, 0, 0, 0.6); font-weight: 500;
    }
    .fields dt mat-icon {
      font-size: 18px; height: 18px; width: 18px;
      color: rgba(0, 0, 0, 0.45);
    }
    .fields dd { margin: 0; }

    .map-wrap { margin-top: 1rem; max-width: 480px; }

    section.team {
      margin-top: 1.5rem;
      padding-top: 1rem;
      border-top: 1px solid rgba(0, 0, 0, 0.08);
    }
    section.team h3 {
      margin: 0 0 0.5rem;
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: rgba(0, 0, 0, 0.55);
    }

    .team-empty { color: rgba(0, 0, 0, 0.55); font-style: italic; }

    .team-row {
      display: flex; align-items: center; gap: 0.5rem;
      padding: 0.4rem 0;
      border-bottom: 1px solid rgba(0, 0, 0, 0.04);
    }
    .team-row:last-child { border-bottom: none; }
    .team-name { flex: 0 0 auto; }
    .team-title { color: rgba(0, 0, 0, 0.6); font-size: 0.9rem; }
    .team-name a { text-decoration: none; }
    .team-name a:hover { text-decoration: underline; }
  `],
})
export class OfficeDetailComponent implements OnInit {
  private readonly officeService = inject(OfficeService);
  private readonly employeeService = inject(EmployeeService);
  private readonly route = inject(ActivatedRoute);

  office = signal<Office | undefined>(undefined);
  team = signal<Employee[] | undefined>(undefined);
  loading = signal(false);
  error = signal<string | undefined>(undefined);

  /** True while the backend is talking to Nominatim. */
  geocoding = signal(false);
  /** Optional message to surface above the card after a geocode. */
  geocodeMessage = signal<string | undefined>(undefined);

  ngOnInit() {
    const code = this.route.snapshot.paramMap.get('id');
    if (!code) {
      this.error.set('Missing office code in URL.');
      return;
    }

    this.loading.set(true);

    // forkJoin runs both requests in parallel and emits once both have
    // completed. We tolerate a failed team fetch — if employees can't
    // be loaded for some reason, we still want the office card to
    // render. catchError returns an empty array in that case.
    const officeRequest = this.officeService.get(code);
    const teamRequest = this.employeeService
      .list(new HttpParams().set('officeCode', code))
      .pipe(catchError(() => of([] as Employee[])));

    forkJoin({ office: officeRequest, team: teamRequest }).subscribe({
      next: ({ office, team }) => {
        this.office.set(office);
        this.team.set(team);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load office');
        this.loading.set(false);
      },
    });
  }

  /**
   * Geocode the current office. Calls the backend, which calls
   * Nominatim, persists the new lat/lng, and returns the updated
   * office. We replace the local office signal with the response so
   * the map appears (or repositions) without a page reload.
   */
  runGeocode() {
    const o = this.office();
    if (!o || this.geocoding()) return;

    this.geocoding.set(true);
    this.geocodeMessage.set(undefined);

    this.officeService.geocode(o.officeCode).subscribe({
      next: updated => {
        this.office.set(updated);
        this.geocoding.set(false);
        this.geocodeMessage.set(updated.lat != null && updated.lng != null
          ? `Geocoded successfully. Map updated.`
          : `Geocoding finished but no coordinates were returned.`);
      },
      error: err => {
        this.geocoding.set(false);
        const msg = err?.error?.message ?? err?.message ?? 'Unknown error';
        this.geocodeMessage.set(`Geocoding failed: ${msg}`);
      },
    });
  }

  /**
   * The popup HTML for the marker. Lives in the component class so we
   * can use the same escapeHtml pattern we wrote for Feature 1.
   */
  buildPopup(office: Office, teamCount: number): string {
    const e = (s: string | null | undefined) => this.escapeHtml(s);
    return `
      <div style="min-width: 180px;">
        <strong>${e(office.city)} office</strong><br>
        ${e(office.addressLine1)}<br>
        ${office.state ? `${e(office.state)}, ` : ''}${e(office.country)}<br>
        <span style="color: rgba(0,0,0,0.6);">
          ${teamCount} employee${teamCount === 1 ? '' : 's'} here
        </span>
      </div>
    `;
  }

  private escapeHtml(s: string | null | undefined): string {
    if (s == null) return '';
    return s.replace(/[&<>"']/g, c => {
      switch (c) {
        case '&': return '&amp;';
        case '<': return '&lt;';
        case '>': return '&gt;';
        case '"': return '&quot;';
        case "'": return '&#39;';
        default: return c;
      }
    });
  }
}
