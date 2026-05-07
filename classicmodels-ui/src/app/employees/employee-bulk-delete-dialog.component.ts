import { Component, Inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';

import { DeleteStrategy } from './employee.service';

/**
 * Input data for the dialog: which employees we're about to delete,
 * and which strategies the backend permits.
 */
export interface BulkDeleteDialogData {
  selectedIds: number[];
  availableStrategies: DeleteStrategy[];
}

/** Output: null on cancel, or the chosen strategy on confirm. */
export type BulkDeleteDialogResult = { strategy: DeleteStrategy } | null;

/**
 * Confirmation dialog for the "Delete N selected" bulk action.
 *
 * <p>Same conventions as the single-row delete dialog (Feature 8):</p>
 *
 * <ul>
 *   <li><b>Strategy picker</b> — only strategies allowed by the
 *       current backend feature flags (no DEEP_CASCADE unless the
 *       env enables it).</li>
 *   <li><b>Type-to-confirm</b> — guard against accidental clicks for
 *       any of the destructive (non-SOFT) strategies. We require the
 *       user to type "DELETE N" where N is the count.</li>
 * </ul>
 *
 * <p>What we deliberately don't do here is render per-employee
 * dependency previews. With ~50 selected, that would be one
 * <code>/dependents</code> call per employee — N+1 city. The bulk
 * delete trades the explicit "what will this break?" preview for
 * speed; if a row fails, the per-id failure reason in the result
 * dialog is informative enough.</p>
 */
@Component({
  standalone: true,
  selector: 'app-employee-bulk-delete-dialog',
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="warn-icon">warning_amber</mat-icon>
      Bulk delete {{ data.selectedIds.length }} employee(s)
    </h2>

    <mat-dialog-content>
      <p>
        You're about to delete <strong>{{ data.selectedIds.length }}</strong>
        employees. Pick the strategy that should apply to all of them.
      </p>

      <mat-form-field appearance="outline" class="full">
        <mat-label>Strategy</mat-label>
        <mat-select [(ngModel)]="strategy">
          @for (s of data.availableStrategies; track s) {
            <mat-option [value]="s">{{ describe(s) }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      @if (isDestructive()) {
        <p class="confirm-prompt">
          This action affects rows in other tables and cannot be
          undone. Type <code>{{ confirmPhrase() }}</code> to enable the
          delete button.
        </p>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Confirmation</mat-label>
          <input matInput
                 [(ngModel)]="confirmation"
                 placeholder="{{ confirmPhrase() }}" />
        </mat-form-field>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">Cancel</button>
      <button mat-flat-button color="warn"
              [disabled]="!canSubmit()"
              (click)="confirm()">
        <mat-icon>delete</mat-icon>
        Delete {{ data.selectedIds.length }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .warn-icon {
      color: #b71c1c;
      vertical-align: middle;
      margin-right: 0.4rem;
    }
    .full { width: 100%; }
    .confirm-prompt {
      color: rgba(0, 0, 0, 0.7);
      font-size: 0.9rem;
      margin-top: 0.4rem;
    }
    .confirm-prompt code {
      background: #fdecea;
      padding: 1px 6px;
      border-radius: 3px;
      font-family: monospace;
    }
  `],
})
export class EmployeeBulkDeleteDialogComponent {
  strategy: DeleteStrategy = 'NULLIFY';
  confirmation = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: BulkDeleteDialogData,
    private ref: MatDialogRef<EmployeeBulkDeleteDialogComponent, BulkDeleteDialogResult>,
  ) {
    // Default to NULLIFY (the safest hard-delete option), or fall back
    // to whatever's first in the allowed list if NULLIFY isn't allowed.
    if (!data.availableStrategies.includes('NULLIFY') && data.availableStrategies.length > 0) {
      this.strategy = data.availableStrategies[0];
    }
  }

  describe(s: DeleteStrategy): string {
    switch (s) {
      case 'SOFT':           return 'SOFT — mark as terminated, keep history';
      case 'NULLIFY':        return 'NULLIFY — clear references, then delete';
      case 'CASCADE':        return 'CASCADE — delete + nullify dependent customers';
      case 'DEEP_CASCADE':   return 'DEEP CASCADE — delete entire dependency tree';
      case 'REASSIGN_DELETE': return 'REASSIGN — single-row only (not for bulk)';
    }
  }

  /** Anything other than SOFT is irreversible at the row level. */
  isDestructive(): boolean {
    return this.strategy !== 'SOFT';
  }

  confirmPhrase(): string {
    return `DELETE ${this.data.selectedIds.length}`;
  }

  canSubmit(): boolean {
    if (!this.strategy) return false;
    if (this.isDestructive()) return this.confirmation.trim() === this.confirmPhrase();
    return true;
  }

  confirm(): void {
    this.ref.close({ strategy: this.strategy });
  }

  cancel(): void {
    this.ref.close(null);
  }
}
