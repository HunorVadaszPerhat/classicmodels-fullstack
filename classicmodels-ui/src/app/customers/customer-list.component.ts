import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';

import { CustomerService } from './customer.service';
import { Customer } from './customer.model';
import { CustomerEventsService } from '../realtime/customer-events.service';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import {
  CustomerDeleteDialogComponent,
  CustomerDeleteDialogData,
  CustomerDeleteDialogResult,
} from './customer-delete-dialog.component';
import {
  CustomerBulkDeleteDialogComponent,
  CustomerBulkDeleteDialogData,
  CustomerBulkDeleteDialogResult,
} from './customer-bulk-delete-dialog.component';
import { CustomerBulkResultDialogComponent } from './customer-bulk-result-dialog.component';
import { ErrorDialogComponent } from '../shared/error-dialog.component';

/**
 * Customers list page. As of Feature C2 this uses SERVER-SIDE pagination,
 * sorting, and filtering — the backend returns just the rows for the
 * current page, and reports the total count separately so the
 * paginator can render correctly.
 *
 * <p>Same pattern as the employee-list rewrite from Feature 6, applied
 * to a different entity. The customer-specific bits are which columns
 * are sortable (customerName, contactLastName, city, country, creditLimit)
 * and which are searched (customerName, contactLastName, contactFirstName).</p>
 *
 * <h3>State management</h3>
 *
 * <p>Five pieces of UI state drive the data fetch: pageIndex, pageSize,
 * sort field, sort direction, and search term. Whenever any of them
 * changes we call {@link load} to fetch a fresh page. The user-facing
 * Material widgets emit events on every change; we update local state
 * and refire.</p>
 *
 * <h3>Search debounce</h3>
 *
 * <p>The search input uses an RxJS Subject with debounceTime(300) so
 * we don't fire a request on every keystroke — we wait until the user
 * has stopped typing for 300ms. This is a standard "search as you type"
 * pattern; without it, fast typists would generate one wasted request
 * per character.</p>
 */
@Component({
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatCheckboxModule,
    MatDialogModule,
  ],
  templateUrl: './customer-list.component.html',
  styles: [`
    .toolbar {
      display: flex; align-items: center; gap: 0.75rem;
      margin-bottom: 0.5rem; flex-wrap: wrap;
    }
    .toolbar h2 { margin: 0; flex: 1; }
    .search-field { width: 280px; max-width: 100%; }

    /*
      The bulk-action toolbar that appears once at least one row is
      selected. Anchored above the regular toolbar; styled blue to read
      as a "what's selected" status bar rather than a generic toolbar.
      Same treatment as the employee list's bulk-bar (Feature 14).
    */
    .bulk-bar {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 0.5rem 0.75rem;
      margin-bottom: 0.5rem;
      background: #e3f2fd;
      border-radius: 4px;
      color: #0d47a1;
      animation: slideIn 120ms ease-out;
    }
    .bulk-bar .count {
      font-weight: 500;
      flex: 1;
    }
    @keyframes slideIn {
      from { opacity: 0; transform: translateY(-4px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    /* The leading select column gets a tighter footprint than data columns. */
    .mat-column-select { width: 48px; padding-left: 0.5rem; padding-right: 0; }

    /*
      C14 country-filter chip. Renders above the table when ?country=
      brought the user here from the dashboard. The same shape as
      Material's mat-chip-with-trailing-icon, but inlined so we don't
      need to register the chips module just for this one element.
    */
    .filter-chips {
      display: flex; align-items: center; gap: 0.5rem;
      margin-bottom: 0.5rem;
      flex-wrap: wrap;
    }
    .filter-chip {
      display: inline-flex; align-items: center; gap: 0.25rem;
      padding: 4px 4px 4px 12px;
      border-radius: 16px;
      background: #e3f2fd;
      color: #0d47a1;
      font-size: 0.85rem;
    }
    .filter-chip-clear {
      display: inline-flex; align-items: center; justify-content: center;
      width: 22px; height: 22px;
      border: none;
      background: rgba(13, 71, 161, 0.18);
      color: #0d47a1;
      border-radius: 50%;
      cursor: pointer;
    }
    .filter-chip-clear:hover { background: rgba(13, 71, 161, 0.32); }
    .filter-chip-clear mat-icon {
      font-size: 14px; height: 14px; width: 14px;
    }
  `],
})
export class CustomerListComponent implements OnInit, OnDestroy {
  private readonly service = inject(CustomerService);
  private readonly dialog = inject(MatDialog);
  private readonly liveEvents = inject(CustomerEventsService);
  private readonly route = inject(ActivatedRoute);

  /** Track the events-subscription so we can unsubscribe on destroy. */
  private eventsSub?: Subscription;

  // -- Table data --
  customers = signal<Customer[]>([]);
  total = signal(0);
  loading = signal(false);
  error = signal<string | undefined>(undefined);
  displayedColumns = ['select', 'customerName', 'contactLastName', 'phone', 'city', 'country', 'actions'];

  // -- Selection state (C8: bulk actions) -----------------------------
  //
  // We store ids in a Set rather than a list of full Customer objects so
  // that selection survives across pagination, sort changes, and live
  // refreshes. The selected customers on page 2 are still in the Set
  // even when only page 1 is visible.
  //
  // Wrapped in a signal so changes flow to the template; we copy/replace
  // the Set on every mutation rather than mutating in place, because
  // signals only fire when the reference changes (Angular treats Sets
  // by reference).
  selection = signal<Set<number>>(new Set<number>());

  /** True when at least one row is selected. Drives the bulk-bar visibility. */
  hasSelection = computed(() => this.selection().size > 0);

  /** Number of currently-selected rows. Shown in the bulk-bar. */
  selectedCount = computed(() => this.selection().size);

  /**
   * Tri-state for the header checkbox:
   *   - 'none'         (no row on the current page is selected)
   *   - 'all'          (every row on the current page is selected)
   *   - 'some'         (some — but not all — rows on the current page)
   *
   * We compute against the CURRENT PAGE only because that's what the
   * checkbox reasonably toggles. Selecting "all" should mean all visible
   * rows; not the entire 122-row dataset.
   */
  pageSelectionState = computed<'none' | 'some' | 'all'>(() => {
    const visible = this.customers();
    if (visible.length === 0) return 'none';
    const sel = this.selection();
    let count = 0;
    for (const c of visible) {
      if (c.customerNumber != null && sel.has(c.customerNumber)) count++;
    }
    if (count === 0) return 'none';
    if (count === visible.length) return 'all';
    return 'some';
  });

  // -- Server-side pagination/sort/filter state --
  pageIndex = signal(0);
  pageSize = signal(10);
  sortField = signal<string>('customerName');
  sortDir = signal<'asc' | 'desc'>('asc');
  /** Bound to the search input via [(ngModel)]. */
  searchText = '';
  /**
   * C14 country filter. Populated from the {@code ?country=} query
   * parameter when the user arrives via the dashboard's
   * "revenue by country" chart drill-down. Rendered as a removable
   * chip above the table; clearing it reverts to the unfiltered list.
   */
  countryFilter = signal<string | null>(null);

  /**
   * Emits every search-input change. Debounced before triggering a
   * fetch so we don't hammer the backend on every keystroke.
   */
  private readonly search$ = new Subject<string>();

  ngOnInit() {
    // C14: read ?country= from the URL once on init. We don't subscribe
    // to queryParams changes because the chip's "clear" action mutates
    // local state and re-loads — there's no live URL→state sync needed.
    const initialCountry = this.route.snapshot.queryParamMap.get('country');
    if (initialCountry) {
      this.countryFilter.set(initialCountry);
    }

    // Wire up the debounced search.
    // 300ms is the conventional middle ground: long enough to coalesce
    // a typist's bursts, short enough to feel snappy.
    // distinctUntilChanged stops us from re-fetching when the user
    // pauses but the value is unchanged (e.g., they pressed Shift).
    this.search$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
    ).subscribe(() => {
      this.pageIndex.set(0); // reset to page 1 on new search
      this.load();
    });

    // Listen for live customer-changed events from the backend.
    // Whenever ANYONE creates/updates/deletes a customer, every
    // open list re-fetches its current page. The backend pushes
    // these via STOMP (see CustomerEventsService).
    this.eventsSub = this.liveEvents.events$.subscribe(event => {
      console.log('Live customer event:', event);
      this.load();
    });

    this.load();
  }

  ngOnDestroy(): void {
    this.eventsSub?.unsubscribe();
  }

  /**
   * Fetch the current page from the server using the current
   * (pageIndex, pageSize, sortField, sortDir, searchText) state.
   */
  load() {
    this.loading.set(true);
    this.service.listPaged({
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: this.sortField(),
      dir: this.sortDir(),
      search: this.searchText,
      country: this.countryFilter() ?? undefined,
    }).subscribe({
      next: page => {
        this.customers.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Load failed');
        this.loading.set(false);
      },
    });
  }

  // -- Material event handlers --

  /** Material's MatPaginator emits this on Next/Prev/page-size change. */
  onPageChange(event: PageEvent) {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  /** MatSort emits this when the user clicks a sortable header. */
  onSortChange(sort: Sort) {
    if (!sort.active || !sort.direction) {
      // Direction = '' means "reset sort" — fall back to the default.
      this.sortField.set('customerName');
      this.sortDir.set('asc');
    } else {
      this.sortField.set(sort.active);
      this.sortDir.set(sort.direction);
    }
    this.pageIndex.set(0); // reset to page 1 when re-sorting
    this.load();
  }

  /** Bound to the search field's (ngModelChange). */
  onSearchInput(value: string) {
    this.searchText = value;
    this.search$.next(value);
  }

  /** Clear the C14 country filter and reload the unfiltered list. */
  clearCountryFilter(): void {
    this.countryFilter.set(null);
    this.pageIndex.set(0);
    this.load();
  }

  // -- Selection mutations (C8) --------------------------------------

  /** True if this row's id is in the selection. */
  isSelected(c: Customer): boolean {
    return c.customerNumber != null && this.selection().has(c.customerNumber);
  }

  /** Flip a single row's selection state on/off. */
  toggleRow(c: Customer): void {
    if (c.customerNumber == null) return;
    const next = new Set(this.selection());
    if (next.has(c.customerNumber)) {
      next.delete(c.customerNumber);
    } else {
      next.add(c.customerNumber);
    }
    this.selection.set(next);
  }

  /**
   * Header checkbox click — toggles every visible row.
   *
   * <p>Mirrors the spreadsheet idiom: clicking the header when
   * everything is selected DESELECTS the page; clicking when some or
   * none are selected SELECTS the page. The "indeterminate" state
   * collapses to "select all" because that's the most useful action.</p>
   */
  togglePage(): void {
    const next = new Set(this.selection());
    const state = this.pageSelectionState();
    const visibleIds = this.customers()
      .map(c => c.customerNumber)
      .filter((n): n is number => n != null);

    if (state === 'all') {
      for (const id of visibleIds) next.delete(id);
    } else {
      for (const id of visibleIds) next.add(id);
    }
    this.selection.set(next);
  }

  /** Wipe the selection — used after a bulk operation completes. */
  clearSelection(): void {
    this.selection.set(new Set());
  }

  // -- Bulk delete (C8) ----------------------------------------------

  /**
   * Open the bulk-delete dialog with the current selection. On confirm,
   * fire the bulk-delete API call and show a result dialog. Mirrors
   * the employee bulk-delete flow from F14.
   */
  bulkDelete(): void {
    const ids = Array.from(this.selection());
    if (ids.length === 0) return;

    // Get the strategies allowed by this environment first — same call
    // used by the per-row delete flow. Cheap; tiny response.
    this.service.getAvailableStrategies().subscribe(availableStrategies => {
      const ref = this.dialog.open<
        CustomerBulkDeleteDialogComponent,
        CustomerBulkDeleteDialogData,
        CustomerBulkDeleteDialogResult
      >(CustomerBulkDeleteDialogComponent, {
        data: { selectedIds: ids, availableStrategies },
        width: '520px',
      });

      ref.afterClosed().subscribe(result => {
        if (!result) return;

        this.service.bulkDelete(ids, result.strategy).subscribe({
          next: outcome => {
            this.dialog.open(CustomerBulkResultDialogComponent, {
              data: outcome,
              width: '520px',
            });
            // Drop only the successfully-deleted ids from the selection
            // — surviving (failed) ones stay selected so the user can
            // retry. We approximate by removing every id NOT in the
            // failures list. (The backend doesn't echo back the
            // succeeded ids; if it did we'd use that directly.)
            const failedIds = new Set(outcome.failures.map(f => f.id));
            const next = new Set<number>();
            for (const id of this.selection()) {
              if (failedIds.has(id)) next.add(id);
            }
            this.selection.set(next);
            this.load();
          },
          error: err => {
            this.dialog.open(ErrorDialogComponent, {
              data: {
                title: 'Bulk delete failed',
                message: err?.error?.message ?? err?.message ?? 'Unknown error',
              },
              width: '480px',
            });
          },
        });
      });
    });
  }

  /**
   * Open the delete dialog with the strategies the backend currently
   * allows, then dispatch the chosen strategy. The strategies request
   * is cheap; we fire it on every click rather than caching, so a
   * feature-flag flip applies immediately.
   */
  remove(customer: Customer) {
    this.service.getAvailableStrategies().subscribe(availableStrategies => {
      const ref = this.dialog.open<
        CustomerDeleteDialogComponent,
        CustomerDeleteDialogData,
        CustomerDeleteDialogResult
      >(CustomerDeleteDialogComponent, {
        data: {
          customerNumber: customer.customerNumber,
          customerName: customer.customerName,
          availableStrategies,
        },
        width: '520px',
      });

      ref.afterClosed().subscribe(result => {
        if (!result) return; // user cancelled

        this.service.delete(customer.customerNumber, result.strategy).subscribe({
          next: () => this.load(),
          error: err => {
            const msg = err?.error?.message ?? err?.message ?? 'Delete failed';
            this.dialog.open(ErrorDialogComponent, {
              data: { title: `Delete (${result.strategy}) failed`, message: msg },
              width: '480px',
            });
          },
        });
      });
    });
  }
}
