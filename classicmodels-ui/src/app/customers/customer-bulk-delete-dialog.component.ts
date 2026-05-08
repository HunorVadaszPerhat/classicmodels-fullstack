import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';

import { CustomerDeleteStrategy } from './customer.service';

/**
 * Input data for the dialog: which customers we're about to delete,
 * and which strategies the backend permits.
 */
export interface CustomerBulkDeleteDialogData {
  selectedIds: number[];
  availableStrategies: CustomerDeleteStrategy[];
}

/** Output: null on cancel, or the chosen strategy on confirm. */
export type CustomerBulkDeleteDialogResult = { strategy: CustomerDeleteStrategy } | null;

/**
 * Confirmation dialog for the "Delete N selected" bulk action on
 * customers.
 *
 * <p>Same conventions as the single-row customer delete dialog (C6):</p>
 *
 * <ul>
 *   <li><b>Strategy picker</b> — only strategies allowed by the
 *       current backend feature flags. SOFT is always available;
 *       DEEP_CASCADE only if {@code app.delete.allow-deep-cascade}
 *       is enabled.</li>
 *   <li><b>Type-to-confirm</b> — guard against accidental clicks for
 *       any destructive (non-SOFT) strategy. We require the user to
 *       type "DELETE N" where N is the count.</li>
 * </ul>
 *
 * <p>Default selection is SOFT — for customers, preserving order /
 * payment history is almost always the right answer. Differs from
 * the employee bulk dialog, which defaults to NULLIFY (because for
 * employees the FK references customers, which ARE nullable —
 * different data shape, different default).</p>
 */
@Component({
  standalone: true,
  selector: 'app-customer-bulk-delete-dialog',
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
      Bulk delete {{ data.selectedIds.length }} customer(s)
    </h2>

    <mat-dialog-content>
      <p>
        You're about to delete <strong>{{ data.selectedIds.length }}</strong>
        customers. Pick the strategy that should apply to all of them.
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
          This action permanently removes the customers <strong>and</strong>
          all of their orders, order details, and payments.
          It cannot be undone. Type <code>{{ confirmPhrase() }}</code>
          to enable the delete button.
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
export class CustomerBulkDeleteDialogComponent {
  strategy: CustomerDeleteStrategy = 'SOFT';
  confirmation = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: CustomerBulkDeleteDialogData,
    private ref: MatDialogRef<CustomerBulkDeleteDialogComponent, CustomerBulkDeleteDialogResult>,
  ) {
    // Default to SOFT (the safe option for customers, since orders
    // and payments retain valid FKs). If for some reason SOFT isn't
    // in the allowed list, fall back to whatever's first.
    if (!data.availableStrategies.includes('SOFT') && data.availableStrategies.length > 0) {
      this.strategy = data.availableStrategies[0];
    }
  }

  describe(s: CustomerDeleteStrategy): string {
    switch (s) {
      case 'SOFT':         return 'SOFT — mark as inactive, preserve orders + payments';
      case 'DEEP_CASCADE': return 'DEEP CASCADE — delete customers + every order, order detail, and payment';
    }
  }

  /** Anything other than SOFT is irreversible — and for customers, also destroys financial history. */
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
