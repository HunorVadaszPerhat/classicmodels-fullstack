import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged, forkJoin } from 'rxjs';
import { EmployeeEventsService } from '../realtime/employee-events.service';

import { EmployeeService, Employee } from './employee.service';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';

import {
  EmployeeDependentsDialogComponent,
  DependentsDialogData,
  DependentsDialogResult,
} from './employee-dependents-dialog.component';
import {
  EmployeeBulkDeleteDialogComponent,
  BulkDeleteDialogData,
  BulkDeleteDialogResult,
} from './employee-bulk-delete-dialog.component';
import { EmployeeBulkResultDialogComponent } from './employee-bulk-result-dialog.component';
import { ErrorDialogComponent } from '../shared/error-dialog.component';

/**
 * Employees list page. As of Feature 6 this uses SERVER-SIDE pagination,
 * sorting, and filtering — the backend returns just the rows for the
 * current page, and reports the total count separately so the
 * paginator can render correctly.
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
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatCheckboxModule,
  ],
  templateUrl: './employee-list.component.html',
  styles: [`
    .toolbar {
      display: flex; align-items: center; gap: 0.75rem;
      margin-bottom: 0.5rem; flex-wrap: wrap;
    }
    .toolbar h2 { margin: 0; flex: 1; }
    .search-field { width: 280px; max-width: 100%; }

    /*
      The bulk-action toolbar that appears once at least one row is
      selected. Anchored above the table; styled blue to read as a
      "what's selected" status bar rather than a regular toolbar.
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
  `],
})
export class EmployeeListComponent implements OnInit, OnDestroy {
  private readonly service = inject(EmployeeService);
  private readonly dialog = inject(MatDialog);
  private readonly liveEvents = inject(EmployeeEventsService);

  /** Track the events-subscription so we can unsubscribe on destroy. */
  private eventsSub?: Subscription;

  // -- Table data --
  employees = signal<Employee[]>([]);
  total = signal(0);
  loading = signal(false);
  error = signal<string | undefined>(undefined);
  displayedColumns = ['select', 'employeeNumber', 'lastName', 'firstName', 'email', 'actions'];

  // -- Selection state (Feature 14: bulk actions) -----------------------
  //
  // We store ids in a Set rather than a list of full Employee objects so
  // that selection survives across pagination, sort changes, and live
  // refreshes. The selected employees on page 2 are still in the Set
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
   *   - 'indeterminate' (some — but not all — rows on the current page)
   *
   * We compute it against the CURRENT PAGE only because that's what the
   * checkbox reasonably toggles. Selecting "all" should mean all visible
   * rows; not the entire 25-row dataset.
   */
  pageSelectionState = computed<'none' | 'some' | 'all'>(() => {
    const visible = this.employees();
    if (visible.length === 0) return 'none';
    const sel = this.selection();
    let count = 0;
    for (const e of visible) {
      if (e.employeeNumber != null && sel.has(e.employeeNumber)) count++;
    }
    if (count === 0) return 'none';
    if (count === visible.length) return 'all';
    return 'some';
  });

  // -- Server-side pagination/sort/filter state --
  pageIndex = signal(0);
  pageSize = signal(10);
  sortField = signal<string>('lastName');
  sortDir = signal<'asc' | 'desc'>('asc');
  /** Bound to the search input via [(ngModel)]. */
  searchText = '';

  /**
   * Emits every search-input change. Debounced before triggering a
   * fetch so we don't hammer the backend on every keystroke.
   */
  private readonly search$ = new Subject<string>();

  ngOnInit() {
    // Wire up the debounced search.
    // 300ms is the conventional middle ground: long enough to coalesce
    // a typist's bursts, short enough to feel snappy.
    // distinctUntilChanged stops us from re-fetching when the user
    // pauses but the value is unchanged (e.g., they pressed Shift).
    this.search$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
    ).subscribe(value => {
      this.pageIndex.set(0); // reset to page 1 on new search
      this.load();
    });

    // Listen for live employee-changed events from the backend.
    // Whenever ANYONE creates/updates/deletes an employee, every
    // open list re-fetches its current page. The backend pushes
    // these via STOMP (see EmployeeEventsService).
    this.eventsSub = this.liveEvents.events$.subscribe(event => {
      console.log('Live employee event:', event);
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
    }).subscribe({
      next: page => {
        this.employees.set(page.content);
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
      this.sortField.set('lastName');
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

  // -- Selection mutations (Feature 14) -------------------------------

  /** True if this row's id is in the selection. */
  isSelected(e: Employee): boolean {
    return e.employeeNumber != null && this.selection().has(e.employeeNumber);
  }

  /** Flip a single row's selection state on/off. */
  toggleRow(e: Employee): void {
    if (e.employeeNumber == null) return;
    const next = new Set(this.selection());
    if (next.has(e.employeeNumber)) {
      next.delete(e.employeeNumber);
    } else {
      next.add(e.employeeNumber);
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
    const visibleIds = this.employees()
      .map(e => e.employeeNumber)
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

  // -- Bulk delete (Feature 14) ---------------------------------------

  /**
   * Open the bulk-delete dialog with the current selection. On confirm,
   * fire the bulk-delete API call and show a result dialog.
   */
  bulkDelete(): void {
    const ids = Array.from(this.selection());
    if (ids.length === 0) return;

    // Get the strategies allowed by this environment first — same call
    // used by the per-row delete flow. Cheap; tiny response.
    this.service.getAvailableStrategies().subscribe(allStrategies => {
      // REASSIGN_DELETE only makes sense with one source + one target,
      // not bulk; filter it out.
      const availableStrategies = allStrategies.filter(s => s !== 'REASSIGN_DELETE');

      const ref = this.dialog.open<
        EmployeeBulkDeleteDialogComponent,
        BulkDeleteDialogData,
        BulkDeleteDialogResult
      >(EmployeeBulkDeleteDialogComponent, {
        data: { selectedIds: ids, availableStrategies },
        width: '520px',
      });

      ref.afterClosed().subscribe(result => {
        if (!result) return;

        this.service.bulkDelete(ids, result.strategy).subscribe({
          next: outcome => {
            this.dialog.open(EmployeeBulkResultDialogComponent, {
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

  // -- Delete flow (unchanged from Feature 4) --

  remove(employee: Employee) {
    const id = employee.employeeNumber!;
    // Three parallel reads: the FK references, the allowed delete
    // strategies, and the list of every active employee (used to
    // populate the "reassign to" dropdown after filtering out the
    // employee being deleted).
    forkJoin({
      dependents: this.service.getDependents(id),
      strategies: this.service.getAvailableStrategies(),
      everyone: this.service.list(),
    }).subscribe({
      next: ({ dependents, strategies, everyone }) => {
        const reassignTargets = everyone
          .filter(e => e.employeeNumber !== id && e.active !== false)
          .sort((a, b) => (a.lastName + a.firstName).localeCompare(b.lastName + b.firstName));

        const ref = this.dialog.open<
          EmployeeDependentsDialogComponent,
          DependentsDialogData,
          DependentsDialogResult
        >(EmployeeDependentsDialogComponent, {
          data: {
            employeeNumber: id,
            firstName: employee.firstName,
            lastName: employee.lastName,
            dependents,
            availableStrategies: strategies,
            reassignTargets,
          },
          width: '560px',
        });

        ref.afterClosed().subscribe(result => {
          if (!result) return;

          // Route to the right backend endpoint based on the chosen strategy.
          // REASSIGN_DELETE has its own dedicated endpoint with a target
          // employee id in the body; everything else goes through the
          // normal delete endpoint with a query-param strategy.
          const operation = result.strategy === 'REASSIGN_DELETE'
            ? this.service.reassignAndDelete(id, result.targetEmployeeId!)
            : this.service.delete(id, result.strategy);

          operation.subscribe({
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
      },
      error: err => this.error.set(err.message),
    });
  }
}
