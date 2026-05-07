import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/**
 * Data passed into {@link ErrorDialogComponent}.
 *
 * @property title    short heading shown in the dialog title bar.
 *                    Defaults to "Error" when omitted.
 * @property message  the actual error text. Rendered as-is, so
 *                    callers should pre-format multiline messages
 *                    with \n characters.
 */
export interface ErrorDialogData {
  title?: string;
  message: string;
}

/**
 * A small reusable dialog for surfacing user-visible errors.
 *
 * <p>Opening it is a one-liner from any component:</p>
 * <pre>
 *   this.dialog.open(ErrorDialogComponent, {
 *     data: { message: 'Something went wrong' }
 *   });
 * </pre>
 *
 * <p>Why a dedicated dialog rather than a snackbar or inline banner?
 * Snackbars auto-dismiss and are easy to miss; inline banners require
 * each consumer to make space in their template. A modal dialog forces
 * acknowledgement, which is appropriate for failures the user just
 * caused (here: "your destructive operation didn't complete").</p>
 */
@Component({
  standalone: true,
  selector: 'app-error-dialog',
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title class="title">
      <mat-icon class="icon" aria-hidden="true">error_outline</mat-icon>
      {{ data.title ?? 'Error' }}
    </h2>
    <mat-dialog-content>
      <p class="message">{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button color="primary" (click)="ref.close()">
        OK
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .title   { display: flex; align-items: center; gap: 0.5rem; }
    .icon    { color: #b71c1c; }
    .message { white-space: pre-wrap; word-break: break-word; margin: 0; }
  `],
})
export class ErrorDialogComponent {
  constructor(
    public ref: MatDialogRef<ErrorDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ErrorDialogData,
  ) {}
}
