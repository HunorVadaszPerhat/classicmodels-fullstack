import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatRadioModule } from '@angular/material/radio';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { FormsModule } from '@angular/forms';

import {
  CustomerService,
  CustomerMergeCandidate,
  CustomerMergeRequest,
} from './customer.service';
import { Customer } from './customer.model';
import { ErrorDialogComponent } from '../shared/error-dialog.component';

/**
 * Two-phase merge / duplicate-detection page (C12).
 *
 * <h3>Phase 1 — pick a candidate</h3>
 *
 * <p>Loads the source customer + the fuzzy-matched candidate list in
 * parallel. Renders a ranked list with per-row similarity scores and
 * which fields drove the match ("name + phone matched, contact
 * name differed"). Clicking Choose advances to phase 2 with that
 * candidate as the loser.</p>
 *
 * <h3>Phase 2 — resolve conflicts</h3>
 *
 * <p>Side-by-side compare table: one row per mergeable field, three
 * columns (field name, winner value, loser value, plus a radio
 * picker). Default selection per row:</p>
 * <ul>
 *   <li>Identical values → no picker, "no conflict" tag.</li>
 *   <li>One side null/empty → default to the non-null side.</li>
 *   <li>Both differ → default to "winner" (the conservative default —
 *       the surviving row stays unchanged unless the user picks).</li>
 * </ul>
 *
 * <p>Clicking Merge POSTs the field-overrides map to
 * {@code /customers/{winnerId}/merge}; on success we navigate to the
 * winner's detail page.</p>
 */
@Component({
  standalone: true,
  selector: 'app-customer-merge',
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatRadioModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <a [routerLink]="['../']" class="back-link">
      <mat-icon>arrow_back</mat-icon> Back to customer
    </a>

    @if (loading()) {
      <div class="loading">
        <mat-spinner diameter="32"></mat-spinner>
        <span>Loading…</span>
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

    <!-- ===================== Phase 1 — candidate picker ===================== -->

    @if (!loading() && phase() === 'PICK' && winner(); as w) {
      <header class="page-head">
        <h2>Find duplicates of <strong>{{ w.customerName }}</strong></h2>
        <div class="muted">Customer #{{ w.customerNumber }}</div>
      </header>

      @if (candidates().length === 0) {
        <div class="empty">
          <p>No potential duplicates found.</p>
          <p class="muted">
            We searched every active customer for similar names,
            contact names, and phone numbers — nothing crossed the
            similarity threshold.
          </p>
        </div>
      } @else {
        <p class="muted">
          {{ candidates().length }} potential duplicate{{ candidates().length === 1 ? '' : 's' }}
          ranked by similarity. Click <strong>Choose</strong> to compare
          field-by-field and merge.
        </p>

        <ul class="candidates">
          @for (c of candidates(); track c.customerNumber) {
            <li class="candidate-row">
              <div class="cand-main">
                <div class="cand-name">
                  <strong>{{ c.customerName }}</strong>
                  <span class="cand-id">#{{ c.customerNumber }}</span>
                </div>
                <div class="cand-sub">
                  {{ c.contactLastName }}, {{ c.contactFirstName }}
                  · {{ c.phone }}
                  · {{ c.city }}, {{ c.country }}
                </div>
                @if (c.matchedFields.length > 0) {
                  <div class="match-tags">
                    @for (f of c.matchedFields; track f) {
                      <span class="match-tag">{{ describeField(f) }} matched</span>
                    }
                  </div>
                }
              </div>
              <div class="cand-score">
                <div class="score-value" [class.high]="c.score >= 0.9"
                                          [class.medium]="c.score >= 0.8 && c.score < 0.9">
                  {{ formatScore(c.score) }}
                </div>
                <div class="score-label">match</div>
              </div>
              <button mat-flat-button color="primary" (click)="choose(c)">
                Choose →
              </button>
            </li>
          }
        </ul>
      }
    }

    <!-- ===================== Phase 2 — field-by-field compare ===================== -->

    @if (!loading() && phase() === 'COMPARE' && winner(); as w) {
      @if (loserCustomer(); as l) {
        <header class="page-head">
          <h2>Merge <strong>{{ l.customerName }}</strong> into
              <strong>{{ w.customerName }}</strong></h2>
          <button mat-button (click)="backToPick()">
            <mat-icon>arrow_back</mat-icon> Pick a different candidate
          </button>
        </header>

        <p class="muted">
          For each field where the two customers disagree, pick which
          value the surviving customer should keep. Identical values
          are shown for context but don't need a choice.
          <br>
          On Merge: the loser's <strong>orders and payments</strong> are
          reassigned to the winner; the loser is then deleted.
          The action runs as one transaction — if any step fails,
          nothing changes.
        </p>

        <table class="compare">
          <thead>
            <tr>
              <th>Field</th>
              <th>
                Winner
                <div class="th-id">#{{ w.customerNumber }}</div>
              </th>
              <th>
                Loser
                <div class="th-id">#{{ l.customerNumber }}</div>
              </th>
              <th>Use</th>
            </tr>
          </thead>
          <tbody>
            @for (row of comparisonRows(); track row.field) {
              <tr [class.identical]="row.identical">
                <td class="field-label">{{ row.label }}</td>
                <td class="field-value">{{ row.winnerValue || '—' }}</td>
                <td class="field-value">{{ row.loserValue || '—' }}</td>
                <td class="picker-cell">
                  @if (row.identical) {
                    <span class="identical-tag">no conflict</span>
                  } @else {
                    <mat-radio-group
                        [ngModel]="overrides()[row.field] ?? 'WINNER'"
                        (ngModelChange)="setOverride(row.field, $event)"
                        [attr.aria-label]="'Pick value for ' + row.label">
                      <mat-radio-button value="WINNER">winner</mat-radio-button>
                      <mat-radio-button value="LOSER">loser</mat-radio-button>
                    </mat-radio-group>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>

        <div class="actions">
          <button mat-button (click)="cancel()">Cancel</button>
          <button mat-flat-button color="primary"
                  [disabled]="merging()"
                  (click)="confirmMerge()">
            @if (merging()) {
              <mat-spinner diameter="16" style="display: inline-block; margin-right: 0.5rem;"></mat-spinner>
            } @else {
              <mat-icon>merge</mat-icon>
            }
            {{ merging() ? 'Merging…' : 'Merge customers' }}
          </button>
        </div>
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

    .page-head {
      display: flex; justify-content: space-between; align-items: flex-end;
      margin-bottom: 1rem;
    }
    .page-head h2 { margin: 0; }

    .loading {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 2rem 0; color: rgba(0, 0, 0, 0.6);
    }
    .error-card { border-left: 4px solid #b71c1c; max-width: 720px; }
    .error-icon { color: #b71c1c; vertical-align: middle; margin-right: 0.5rem; }

    .muted { color: rgba(0, 0, 0, 0.6); }

    .empty {
      padding: 3rem 1rem;
      text-align: center;
      border: 1px dashed rgba(0, 0, 0, 0.18);
      border-radius: 4px;
      color: rgba(0, 0, 0, 0.7);
    }
    .empty p { margin: 0.4rem 0; }

    /* --- Candidate list (phase 1) --- */
    .candidates { list-style: none; padding: 0; margin: 1rem 0 0; }
    .candidate-row {
      display: grid;
      grid-template-columns: 1fr auto auto;
      gap: 1rem;
      align-items: center;
      padding: 0.75rem 1rem;
      background: #fafafa;
      border: 1px solid rgba(0, 0, 0, 0.08);
      border-radius: 6px;
      margin-bottom: 0.75rem;
    }
    .cand-name { font-size: 1rem; }
    .cand-id { color: rgba(0, 0, 0, 0.5); margin-left: 0.4rem; font-size: 0.85em; }
    .cand-sub { color: rgba(0, 0, 0, 0.65); font-size: 0.85rem; margin-top: 0.2rem; }
    .match-tags { margin-top: 0.4rem; display: flex; gap: 0.4rem; flex-wrap: wrap; }
    .match-tag {
      font-size: 0.75rem;
      padding: 1px 8px;
      border-radius: 10px;
      background: #e3f2fd;
      color: #0d47a1;
    }

    .cand-score { text-align: center; }
    .score-value {
      font-size: 1.4rem;
      font-weight: 500;
      font-variant-numeric: tabular-nums;
      color: rgba(0, 0, 0, 0.7);
      line-height: 1;
    }
    .score-value.medium { color: #e65100; }
    .score-value.high { color: #1b5e20; }
    .score-label { font-size: 0.75rem; color: rgba(0, 0, 0, 0.5); }

    /* --- Compare table (phase 2) --- */
    .compare {
      width: 100%;
      border-collapse: collapse;
      margin-top: 1rem;
    }
    .compare th, .compare td {
      padding: 0.5rem 0.75rem;
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      vertical-align: top;
      text-align: left;
    }
    .compare th {
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: rgba(0, 0, 0, 0.55);
      font-weight: 600;
    }
    .th-id { font-weight: 400; font-size: 0.8rem; color: rgba(0, 0, 0, 0.5); }
    .field-label { font-weight: 500; min-width: 140px; }
    .field-value {
      color: rgba(0, 0, 0, 0.85);
      max-width: 240px;
      word-break: break-word;
    }
    .compare tr.identical .field-value { color: rgba(0, 0, 0, 0.55); }
    .picker-cell mat-radio-group { display: flex; gap: 0.6rem; }
    .identical-tag {
      font-size: 0.75rem;
      color: rgba(0, 0, 0, 0.5);
      font-style: italic;
    }

    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1.25rem;
    }
  `],
})
export class CustomerMergeComponent implements OnInit {
  private readonly customers = inject(CustomerService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  // -- State --
  loading = signal(true);
  error = signal<string | undefined>(undefined);
  merging = signal(false);

  /** 'PICK' = phase 1 (candidate list); 'COMPARE' = phase 2 (field picker). */
  phase = signal<'PICK' | 'COMPARE'>('PICK');

  winner = signal<Customer | undefined>(undefined);
  candidates = signal<CustomerMergeCandidate[]>([]);

  /** The full customer record for the chosen loser (loaded on entering phase 2). */
  loserCustomer = signal<Customer | undefined>(undefined);

  /** Per-field winner/loser choices. Empty map means "winner everywhere." */
  overrides = signal<{ [field: string]: 'WINNER' | 'LOSER' }>({});

  /**
   * Static list of fields shown in the compare table, in the order
   * they appear. Same field set the customer form (C3) edits — so
   * the merge view is the natural inverse: the form unifies, the
   * merge picks-and-collapses.
   */
  private static readonly FIELDS: Array<{ field: keyof Customer; label: string }> = [
    { field: 'customerName',       label: 'Customer name' },
    { field: 'contactFirstName',   label: 'Contact first' },
    { field: 'contactLastName',    label: 'Contact last' },
    { field: 'phone',              label: 'Phone' },
    { field: 'addressLine1',       label: 'Address line 1' },
    { field: 'addressLine2',       label: 'Address line 2' },
    { field: 'city',               label: 'City' },
    { field: 'state',              label: 'State / province' },
    { field: 'postalCode',         label: 'Postal code' },
    { field: 'country',            label: 'Country' },
    { field: 'salesRepEmployeeNumber', label: 'Sales rep #' },
    { field: 'creditLimit',        label: 'Credit limit' },
  ];

  /** Compare-table rows derived from winner + loser. */
  comparisonRows = computed(() => {
    const w = this.winner();
    const l = this.loserCustomer();
    if (!w || !l) return [];
    return CustomerMergeComponent.FIELDS.map(({ field, label }) => {
      const wVal = w[field];
      const lVal = l[field];
      const winnerValue = wVal == null || wVal === '' ? '' : String(wVal);
      const loserValue  = lVal == null || lVal === '' ? '' : String(lVal);
      const identical = winnerValue === loserValue;
      return { field: field as string, label, winnerValue, loserValue, identical };
    });
  });

  ngOnInit(): void {
    const raw = this.route.snapshot.paramMap.get('id');
    const id = Number(raw);
    if (raw === null || !Number.isInteger(id)) {
      this.error.set(`Invalid customer id in URL: "${raw}"`);
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    forkJoin({
      winner: this.customers.get(id),
      candidates: this.customers.getMergeCandidates(id),
    }).subscribe({
      next: ({ winner, candidates }) => {
        this.winner.set(winner);
        this.candidates.set(candidates);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load merge candidates');
        this.loading.set(false);
      },
    });
  }

  /**
   * Phase 1 → phase 2. Fetches the full loser record (the candidate
   * payload only carries the display fields) so the compare table
   * can read every mergeable field.
   */
  choose(c: CustomerMergeCandidate): void {
    this.loading.set(true);
    this.customers.get(c.customerNumber).subscribe({
      next: loser => {
        this.loserCustomer.set(loser);
        // Sensible default: any field where one side is empty and the
        // other isn't, default to whichever side has a value. Where
        // both have values, default to winner (no override entry —
        // the backend keeps the winner's value when a key is absent).
        const w = this.winner();
        if (w) {
          const defaults: { [field: string]: 'WINNER' | 'LOSER' } = {};
          for (const { field } of CustomerMergeComponent.FIELDS) {
            const wVal = (w as any)[field];
            const lVal = (loser as any)[field];
            const wEmpty = wVal == null || wVal === '';
            const lEmpty = lVal == null || lVal === '';
            if (wEmpty && !lEmpty) defaults[field as string] = 'LOSER';
            // else keep winner (no entry needed; backend defaults to winner)
          }
          this.overrides.set(defaults);
        }
        this.phase.set('COMPARE');
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Failed to load candidate');
        this.loading.set(false);
      },
    });
  }

  /** Phase 2 → phase 1; resets per-merge state. */
  backToPick(): void {
    this.phase.set('PICK');
    this.loserCustomer.set(undefined);
    this.overrides.set({});
  }

  /** Cancel — go back to the customer's detail page. */
  cancel(): void {
    this.router.navigate(['../'], { relativeTo: this.route });
  }

  /** Update one field's choice. */
  setOverride(field: string, choice: 'WINNER' | 'LOSER'): void {
    const next = { ...this.overrides() };
    next[field] = choice;
    this.overrides.set(next);
  }

  confirmMerge(): void {
    const w = this.winner();
    const l = this.loserCustomer();
    if (!w || !l || this.merging()) return;

    const request: CustomerMergeRequest = {
      loserId: l.customerNumber,
      // Strip 'WINNER' entries; the backend defaults to keeping winner
      // when a field is absent, so they're noise on the wire.
      fieldOverrides: Object.fromEntries(
        Object.entries(this.overrides()).filter(([, choice]) => choice === 'LOSER')
      ) as { [field: string]: 'WINNER' | 'LOSER' },
    };

    this.merging.set(true);
    this.customers.merge(w.customerNumber, request).subscribe({
      next: () => {
        this.merging.set(false);
        // Navigate to the surviving customer's detail page.
        this.router.navigate(['../'], { relativeTo: this.route });
      },
      error: err => {
        this.merging.set(false);
        this.dialog.open(ErrorDialogComponent, {
          data: {
            title: 'Merge failed',
            message: err?.error?.message ?? err?.message ?? 'Unknown error',
          },
          width: '480px',
        });
      },
    });
  }

  /** "name" / "contact" / "phone" → human label. */
  describeField(f: string): string {
    switch (f) {
      case 'name': return 'Name';
      case 'contact': return 'Contact';
      case 'phone': return 'Phone';
      default: return f;
    }
  }

  /** Format 0.83 → "83%". */
  formatScore(s: number): string {
    return Math.round(s * 100) + '%';
  }
}
