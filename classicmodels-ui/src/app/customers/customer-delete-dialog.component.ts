import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatRadioModule } from '@angular/material/radio';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { CustomerDeleteStrategy } from './customer.service';

/**
 * Data passed in when opening the dialog.
 */
export interface CustomerDeleteDialogData {
  customerNumber: number;
  customerName: string;
  /** Strategies the backend is currently willing to execute. */
  availableStrategies: CustomerDeleteStrategy[];
}

/**
 * What comes back via afterClosed(). undefined means the user cancelled.
 */
export interface CustomerDeleteDialogResult {
  strategy: CustomerDeleteStrategy;
}

/**
 * Confirmation dialog for deleting a customer.
 *
 * <p>Customer-specific shape (vs. employee's dependents dialog):</p>
 * <ul>
 *   <li>Two strategies max — SOFT and DEEP_CASCADE — because NULLIFY,
 *       CASCADE, and REASSIGN_DELETE don't apply to customers.</li>
 *   <li>If only SOFT is available (DEEP_CASCADE flag off), the radio
 *       group still renders for consistency, but with a single option.
 *       Less code than branching.</li>
 *   <li>DEEP_CASCADE requires typing "DELETE EVERYTHING" to enable
 *       the confirm button. Same destructive-typing pattern the
 *       employee dialog uses.</li>
 * </ul>
 */
@Component({
  standalone: true,
  selector: 'app-customer-delete-dialog',
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatRadioModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="warn">warning_amber</mat-icon>
      Delete customer #{{ data.customerNumber }}?
    </h2>

    <mat-dialog-content>
      <p class="customer-line">
        <strong>{{ data.customerName }}</strong>
      </p>

      <p>How should this deletion be carried out?</p>

      <mat-radio-group [(ngModel)]="strategy" class="strategy-group">
        @if (data.availableStrategies.includes('SOFT')) {
          <mat-radio-button value="SOFT">
            <div class="option">
              <div class="option-title">Mark as inactive (recommended)</div>
              <div class="option-help">
                Hides the customer from default lists. Orders, order details,
                and payments are preserved with valid foreign keys. Fully
                reversible — the customer can be restored later.
              </div>
            </div>
          </mat-radio-button>
        }

        @if (data.availableStrategies.includes('DEEP_CASCADE')) {
          <mat-radio-button value="DEEP_CASCADE" class="destructive">
            <div class="option">
              <div class="option-title">Permanently delete + all history</div>
              <div class="option-help">
                Hard-deletes the customer and every order, order detail,
                and payment that references them. Not reversible.
                Destroys financial history.
              </div>
            </div>
          </mat-radio-button>
        }
      </mat-radio-group>

      <!--
        Destructive-typing gate. Requiring the user to type a specific
        phrase before the confirm button enables is the standard
        "are you really sure" pattern for irreversible operations.
      -->
      @if (strategy === 'DEEP_CASCADE') {
        <div class="confirm-block">
          <p class="confirm-prompt">
            Type <code>DELETE EVERYTHING</code> to confirm:
          </p>
          <mat-form-field appearance="outline" class="full-width" subscriptSizing="dynamic">
            <input matInput [(ngModel)]="confirmPhrase" placeholder="DELETE EVERYTHING" />
          </mat-form-field>
        </div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Cancel</button>
      <button mat-flat-button color="warn"
              [disabled]="!canConfirm()"
              (click)="confirm()">
        <mat-icon>delete</mat-icon>
        {{ confirmLabel() }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    h2 { display: flex; align-items: center; gap: 0.5rem; }
    .warn { color: #b71c1c; }

    .customer-line { margin-bottom: 1rem; }

    .strategy-group {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      margin-top: 0.5rem;
    }

    /* Push each radio option's body into a column so the help text
       wraps under the title rather than next to it. */
    .option { display: flex; flex-direction: column; gap: 0.15rem; }
    .option-title { font-weight: 500; }
    .option-help {
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.85rem;
      line-height: 1.35;
    }

    /* Slight visual cue that DEEP_CASCADE is the dangerous path. */
    .destructive .option-title { color: #b71c1c; }

    .confirm-block { margin-top: 1rem; }
    .confirm-prompt { margin: 0 0 0.5rem; font-size: 0.9rem; }
    .confirm-prompt code {
      background: rgba(0, 0, 0, 0.06);
      padding: 0 0.3rem;
      border-radius: 3px;
    }
    .full-width { width: 100%; }
  `],
})
export class CustomerDeleteDialogComponent {
  /** The strategy the user has currently picked. */
  strategy: CustomerDeleteStrategy = 'SOFT';

  /** The destructive-typing input. Only used when strategy === DEEP_CASCADE. */
  confirmPhrase = '';

  constructor(
    public ref: MatDialogRef<CustomerDeleteDialogComponent, CustomerDeleteDialogResult>,
    @Inject(MAT_DIALOG_DATA) public data: CustomerDeleteDialogData,
  ) {
    // Default the picker to the first available strategy. Almost
    // always SOFT; only different in some weird configuration where
    // SOFT was disabled (which currently can't happen).
    if (data.availableStrategies.length > 0
        && !data.availableStrategies.includes('SOFT')) {
      this.strategy = data.availableStrategies[0];
    }
  }

  /** True iff the confirm button should be enabled. */
  canConfirm(): boolean {
    if (this.strategy === 'DEEP_CASCADE') {
      return this.confirmPhrase === 'DELETE EVERYTHING';
    }
    return this.data.availableStrategies.includes(this.strategy);
  }

  /** Tailor the button label to the chosen strategy. */
  confirmLabel(): string {
    return this.strategy === 'DEEP_CASCADE'
      ? 'Delete everything'
      : 'Mark as inactive';
  }

  confirm() {
    this.ref.close({ strategy: this.strategy });
  }
}
