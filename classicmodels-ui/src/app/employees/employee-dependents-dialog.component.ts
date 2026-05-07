import { Component, Inject, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import {
  DeleteStrategy,
  DeletionPlan,
  DeletionStep,
  Employee,
  EmployeeDependent,
  EmployeeService,
} from './employee.service';

/**
 * Data passed into the dialog from the parent component. The dialog is
 * a "dumb" presenter — it doesn't fetch anything itself, the caller
 * provides the employee identity and the pre-fetched dependents list.
 *
 * Keeping data flow one-directional like this makes the dialog easier
 * to test (just instantiate with mock data) and avoids splitting the
 * "what happens when delete is clicked" logic across two components.
 */
export interface DependentsDialogData {
  employeeNumber: number;
  firstName: string;
  lastName: string;
  dependents: EmployeeDependent[];
  availableStrategies: DeleteStrategy[];
  /**
   * Active employees other than the one being deleted. Populates the
   * dropdown for the REASSIGN_DELETE option. If empty, REASSIGN_DELETE
   * is hidden — there's no one to reassign to.
   */
  reassignTargets: Employee[];
}

/**
 * The literal phrase the user must type to enable the DEEP_CASCADE
 * delete button. Case-sensitive. This is intentionally awkward to type
 * — the cost of an accidental click for this option is "the entire
 * customer + order + payment chain is gone."
 */
export const DEEP_CASCADE_CONFIRM_PHRASE = 'DELETE EVERYTHING';

/**
 * Result returned to the caller when the user clicks "Delete".
 * `undefined` (the default close result) means the user cancelled.
 *
 * <p>{@code targetEmployeeId} is required iff strategy is REASSIGN_DELETE.</p>
 */
export interface DependentsDialogResult {
  strategy: DeleteStrategy;
  targetEmployeeId?: number;
}

@Component({
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatRadioModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>
      Delete {{ data.firstName }} {{ data.lastName }}
      <span class="muted">(#{{ data.employeeNumber }})</span>
    </h2>

    <mat-dialog-content class="content">
      <!--
        Section 1 — show the actual rows that reference this employee.
        Empty state is treated as "any strategy is safe" rather than
        "you can hard delete without thinking" — the choice still
        matters for audit trail.
      -->
      <section>
        <h3>References</h3>
        @if (data.dependents.length === 0) {
          <p class="muted">
            No other tables reference this employee. Any delete strategy is safe.
          </p>
        } @else {
          @for (dep of data.dependents; track dep.tableName) {
            <h4>
              {{ dep.tableName }}.{{ dep.columnName }}
              <span class="muted">— {{ dep.count }} row(s)</span>
            </h4>
            <ul>
              @for (rec of dep.records; track rec.id) {
                <li>{{ rec.label }} <span class="muted">(#{{ rec.id }})</span></li>
              }
              @if (dep.count > dep.records.length) {
                <li class="muted">… and {{ dep.count - dep.records.length }} more</li>
              }
            </ul>
          }
        }
      </section>

      <!--
        Section 2 — strategy picker. SOFT is selected by default because
        it's the safest, most reversible option. The hint text under
        each radio option spells out the consequences in business terms.

        Strategy change also triggers loading the deletion plan when
        the user picks one of the destructive options. See onStrategyChange().
      -->
      <section>
        <h3>How should we delete?</h3>
        <mat-radio-group [(ngModel)]="strategy"
                         (ngModelChange)="onStrategyChange()"
                         class="strategy-group">

          <mat-radio-button value="SOFT">
            <strong>Soft delete</strong> <span class="muted">(recommended)</span>
            <p class="hint">
              Marks the employee as <em>terminated</em>. No row is removed; all
              foreign keys stay valid; reports remain accurate. Reversible.
            </p>
          </mat-radio-button>

          <mat-radio-button value="NULLIFY">
            <strong>Hard delete &mdash; null out references</strong>
            <p class="hint">
              Removes the employee row. Customers and direct reports survive
              but lose their link (sales rep / manager set to <code>NULL</code>).
              Loses the historical "who was assigned to whom" audit trail.
            </p>
          </mat-radio-button>

          <mat-radio-button value="CASCADE">
            <strong>Hard delete &mdash; cascade</strong>
            <p class="hint warn">
              Removes the employee <em>and</em> the customers they were assigned to.
              Will <strong>fail</strong> if any of those customers have existing orders
              or payments — by design, to protect financial history.
            </p>
          </mat-radio-button>

          <!--
            DEEP_CASCADE is only rendered when the backend reports it
            as enabled (app.delete.allow-deep-cascade=true). Hiding the
            option entirely is better than showing it greyed-out:
            "I have a destructive feature flag turned on" is information
            the operator should already know about, not something the
            UI needs to advertise to every user.
          -->
          @if (isStrategyAllowed('DEEP_CASCADE')) {
            <mat-radio-button value="DEEP_CASCADE">
              <strong class="warn">Hard delete &mdash; deep cascade</strong>
              <p class="hint warn">
                <strong>Destroys financial history.</strong> Removes the employee, their
                customers, every order from those customers, every order detail line,
                and every payment. Use only on test data or after a botched import.
              </p>
            </mat-radio-button>
          }

          <!--
            Reassign + delete only makes sense if there's someone to
            reassign TO. Hide the option entirely when reassignTargets
            is empty (every other active employee was already terminated,
            or this is the only employee in the system).
          -->
          @if (data.reassignTargets.length > 0) {
            <mat-radio-button value="REASSIGN_DELETE">
              <strong>Reassign to another employee &mdash; then delete</strong>
              <p class="hint">
                Atomically moves customers and direct reports to a chosen
                replacement, then removes the employee. The classic
                "they're leaving, hand over their book of business" flow.
              </p>
            </mat-radio-button>
          }
        </mat-radio-group>

        <!--
          Two destructive options share the type-to-confirm guard, with
          different phrases:
            CASCADE       → type the employee's last name (mild barrier)
            DEEP_CASCADE  → type "DELETE EVERYTHING" verbatim (high barrier)
          The phrase for DEEP_CASCADE is deliberately unusual to type so
          muscle memory can't auto-fill it.
        -->
        @if (strategy === 'CASCADE') {
          <div class="confirm-block">
            <p>
              To confirm, type the employee's last name
              (<code>{{ data.lastName }}</code>):
            </p>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Last name</mat-label>
              <input matInput [(ngModel)]="confirmText" autocomplete="off" />
            </mat-form-field>
          </div>
        }
        @if (strategy === 'DEEP_CASCADE') {
          <div class="confirm-block">
            <p class="warn">
              This destroys orders and payments. To confirm, type
              <code>{{ confirmPhrase }}</code> exactly (case-sensitive):
            </p>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirmation phrase</mat-label>
              <input matInput [(ngModel)]="confirmText" autocomplete="off" />
            </mat-form-field>
          </div>
        }
        @if (strategy === 'REASSIGN_DELETE') {
          <div class="confirm-block">
            <p>Who should inherit the customers and direct reports?</p>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Target employee</mat-label>
              <mat-select [(ngModel)]="targetEmployeeId">
                @for (m of data.reassignTargets; track m.employeeNumber) {
                  <mat-option [value]="m.employeeNumber">
                    {{ m.firstName }} {{ m.lastName }}
                    <span style="color: rgba(0,0,0,0.45)">— {{ m.jobTitle }}</span>
                  </mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
        }
      </section>

      <!--
        Section 3 — read-only blast-radius preview from the planner.
        Only rendered for the destructive strategies because for SOFT
        and NULLIFY the impact is already obvious from the references
        section above.

        The plan comes from GET /admin/deletion-plan and tells us
        exactly which SQL statements would run, in what order, and how
        many rows each one would touch — so the user is making an
        informed choice rather than a leap of faith.
      -->
      @if (strategy === 'CASCADE' || strategy === 'DEEP_CASCADE') {
        <section class="plan-section">
          <h3>What will run</h3>

          @if (planLoading) {
            <div class="loading">
              <mat-spinner diameter="20"></mat-spinner>
              <span>Computing deletion plan…</span>
            </div>
          } @else if (planError) {
            <p class="warn">Couldn't load plan: {{ planError }}</p>
          } @else if (plan) {
            <p>
              <strong>{{ plan.totalSteps }}</strong> SQL statement(s) would run,
              affecting <strong>{{ plan.totalAffectedRows }}</strong> row(s) in total.
            </p>
            <table mat-table [dataSource]="plan.steps" class="plan-table">
              <ng-container matColumnDef="order">
                <th mat-header-cell *matHeaderCellDef>#</th>
                <td mat-cell *matCellDef="let s">{{ s.order }}</td>
              </ng-container>
              <ng-container matColumnDef="kind">
                <th mat-header-cell *matHeaderCellDef>Kind</th>
                <td mat-cell *matCellDef="let s"
                    [class.warn]="s.kind === 'WARNING'">
                  {{ s.kind }}
                </td>
              </ng-container>
              <ng-container matColumnDef="tableName">
                <th mat-header-cell *matHeaderCellDef>Table</th>
                <td mat-cell *matCellDef="let s">
                  <span [style.padding-left.px]="s.depth * 12">{{ s.tableName }}</span>
                </td>
              </ng-container>
              <ng-container matColumnDef="rows">
                <th mat-header-cell *matHeaderCellDef>Rows</th>
                <td mat-cell *matCellDef="let s">{{ s.expectedRows }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="planCols"></tr>
              <tr mat-row *matRowDef="let row; columns: planCols"></tr>
            </table>
          }
        </section>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Cancel</button>
      <button mat-flat-button color="warn"
              [disabled]="!canConfirm()"
              (click)="confirm()">
        Delete
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .content { max-height: 70vh; overflow: auto; }
    .muted   { color: rgba(0, 0, 0, 0.55); font-weight: normal; }
    .warn    { color: #b71c1c; }
    .hint    { margin: 0.25rem 0 0 1.75rem; color: rgba(0, 0, 0, 0.7); }
    h3       { margin-top: 1.25rem; }
    h4       { margin: 0.75rem 0 0.25rem; }
    ul       { margin: 0 0 0.75rem 1.25rem; }
    .strategy-group { display: flex; flex-direction: column; gap: 0.75rem; margin-top: 0.5rem; }
    .confirm-block { margin-top: 1rem; }
    .full-width { width: 100%; }
    .plan-section { margin-top: 1rem; }
    .plan-table   { width: 100%; margin-top: 0.5rem; }
    .loading      { display: flex; align-items: center; gap: 0.5rem; }
  `],
})
export class EmployeeDependentsDialogComponent {
  private readonly employeeService = inject(EmployeeService);

  /**
   * Currently selected strategy. SOFT is the default because it's safest.
   * We use a plain property rather than a signal here because Angular's
   * `[(ngModel)]` two-way binding with `mat-radio-group` works most
   * cleanly with a regular field.
   */
  strategy: DeleteStrategy = 'SOFT';

  /** Used by both CASCADE and DEEP_CASCADE confirmation prompts. */
  confirmText = '';

  /** Used only when REASSIGN_DELETE is chosen — the chosen target employee. */
  targetEmployeeId: number | null = null;

  /** Exposed to the template so the user can see what phrase to type. */
  readonly confirmPhrase = DEEP_CASCADE_CONFIRM_PHRASE;

  /** Cached plan from /admin/deletion-plan. Populated lazily. */
  plan: DeletionPlan | undefined;
  planLoading = false;
  planError: string | undefined;
  readonly planCols = ['order', 'kind', 'tableName', 'rows'];

  constructor(
    public ref: MatDialogRef<EmployeeDependentsDialogComponent, DependentsDialogResult>,
    @Inject(MAT_DIALOG_DATA) public data: DependentsDialogData,
  ) {}

  /**
   * Called whenever the radio group's value changes. We use this to
   * lazily fetch the deletion plan only when the user actually picks
   * one of the destructive strategies — there's no point computing a
   * blast-radius preview for SOFT or NULLIFY.
   *
   * <p>The plan is fetched at most once per dialog open. It depends
   * only on (table, idColumn, id), which don't change while the
   * dialog is open. Re-selecting CASCADE after DEEP_CASCADE (or vice
   * versa) reuses the cached value.</p>
   */
  onStrategyChange() {
    const wantsPlan = this.strategy === 'CASCADE' || this.strategy === 'DEEP_CASCADE';
    if (!wantsPlan) return;
    if (this.plan || this.planLoading) return;

    this.planLoading = true;
    this.planError = undefined;
    this.employeeService
      .getDeletionPlan('employees', 'employeeNumber', this.data.employeeNumber)
      .subscribe({
        next: p => {
          this.plan = p;
          this.planLoading = false;
        },
        error: err => {
          this.planError = err?.error?.message ?? err?.message ?? 'Unknown error';
          this.planLoading = false;
        },
      });
  }

  /**
   * Whether a given strategy should appear as a radio option.
   * Filters against the list the backend says it will accept right now.
   */
  isStrategyAllowed(s: DeleteStrategy): boolean {
    return this.data.availableStrategies.includes(s);
  }

  /**
   * The Delete button is disabled until the user has cleared the
   * appropriate guard for the chosen strategy.
   *
   *   SOFT, NULLIFY     → no extra confirmation; SOFT is reversible,
   *                       NULLIFY only severs links, neither destroys
   *                       child rows.
   *   CASCADE           → type the employee's last name (case-insensitive,
   *                       low-friction barrier).
   *   DEEP_CASCADE      → type the literal phrase "DELETE EVERYTHING"
   *                       (case-sensitive, deliberately high friction).
   */
  canConfirm(): boolean {
    switch (this.strategy) {
      case 'CASCADE':
        return this.confirmText.trim().toLowerCase() === this.data.lastName.toLowerCase();
      case 'DEEP_CASCADE':
        // Strict equality on the trimmed value. We do NOT lower-case
        // here — the all-caps phrase is intentional friction.
        return this.confirmText.trim() === DEEP_CASCADE_CONFIRM_PHRASE;
      case 'REASSIGN_DELETE':
        return this.targetEmployeeId != null;
      default:
        return true;
    }
  }

  confirm() {
    if (!this.canConfirm()) return;
    if (this.strategy === 'REASSIGN_DELETE') {
      this.ref.close({
        strategy: this.strategy,
        targetEmployeeId: this.targetEmployeeId!,
      });
      return;
    }
    this.ref.close({ strategy: this.strategy });
  }
}
