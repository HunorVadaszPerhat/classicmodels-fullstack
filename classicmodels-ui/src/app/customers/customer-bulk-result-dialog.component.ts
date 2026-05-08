import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { BulkOperationResult } from './customer.service';

/**
 * Displays the per-item outcome of a customer bulk operation.
 *
 * <p>The bulk endpoint returns HTTP 200 with a result envelope even
 * when some items failed — partial success is a normal outcome, not
 * an error. This dialog turns that envelope into a readable summary:
 * total successes, total failures, and a scrollable list of the
 * individual failure reasons so the user can fix and retry.</p>
 *
 * <p>Three visual cases:</p>
 *
 * <ul>
 *   <li><b>All succeeded</b> — green checkmark, "N deleted."</li>
 *   <li><b>All failed</b> — red icon, "0 of N succeeded," failure list.</li>
 *   <li><b>Partial</b> — orange icon, mixed message, failure list.</li>
 * </ul>
 */
@Component({
  standalone: true,
  selector: 'app-customer-bulk-result-dialog',
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>
      <mat-icon [class.success]="data.failureCount === 0"
                [class.partial]="data.successCount > 0 && data.failureCount > 0"
                [class.fail]="data.successCount === 0 && data.failureCount > 0">
        {{ icon() }}
      </mat-icon>
      Bulk delete result
    </h2>

    <mat-dialog-content>
      <p class="summary">
        <strong>{{ data.successCount }}</strong> of
        <strong>{{ data.requested }}</strong> deleted successfully.
        @if (data.failureCount > 0) {
          <span class="fail-text">
            ({{ data.failureCount }} failure{{ data.failureCount === 1 ? '' : 's' }})
          </span>
        }
      </p>

      @if (data.failures.length > 0) {
        <h3>Failures</h3>
        <ul class="failures">
          @for (f of data.failures; track f.id) {
            <li>
              <strong>#{{ f.id }}</strong>: {{ f.reason }}
            </li>
          }
        </ul>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-flat-button color="primary" mat-dialog-close>OK</button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-icon {
      vertical-align: middle;
      margin-right: 0.4rem;
    }
    mat-icon.success { color: #2e7d32; }
    mat-icon.partial { color: #e65100; }
    mat-icon.fail    { color: #b71c1c; }

    .summary { font-size: 1rem; }
    .fail-text { color: #b71c1c; }

    h3 {
      margin: 1rem 0 0.4rem;
      font-size: 0.95rem;
      color: rgba(0, 0, 0, 0.7);
    }
    .failures {
      max-height: 220px;
      overflow-y: auto;
      padding-left: 1.25rem;
      margin: 0;
    }
    .failures li { margin-bottom: 0.3rem; word-break: break-word; }
  `],
})
export class CustomerBulkResultDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: BulkOperationResult,
    private ref: MatDialogRef<CustomerBulkResultDialogComponent>,
  ) {}

  icon(): string {
    if (this.data.failureCount === 0) return 'check_circle';
    if (this.data.successCount === 0) return 'error';
    return 'warning_amber';
  }
}
