import {
  Component,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { catchError, of } from 'rxjs';
import { EmployeeService } from './employee.service';

/**
 * Renders an employee's profile photo, falling back to a generic
 * avatar icon when there isn't one.
 *
 * <h3>Why not just &lt;img src="/api/v1/employees/{id}/photo"&gt;?</h3>
 *
 * <p>Because the photo endpoint requires the JWT bearer token, and the
 * browser doesn't add Authorization headers to image requests. The
 * &lt;img&gt; would fail with a 401 and show as broken.</p>
 *
 * <p>The fix: fetch the photo via {@code HttpClient} (which DOES add
 * the bearer token via our interceptor), get a {@code Blob} back, then
 * convert it to an object URL with {@code URL.createObjectURL(blob)}.
 * Object URLs are local, in-memory references the browser can render
 * directly without making another HTTP request — and so without
 * needing the auth header.</p>
 *
 * <h3>Memory hygiene</h3>
 *
 * <p>Object URLs hold a reference to the underlying Blob, which keeps
 * the bytes resident in memory until the URL is explicitly revoked.
 * We {@code revokeObjectURL} on destroy and on input change so we
 * don't leak. Forgetting this would cause a slow memory leak as the
 * user navigates between employees.</p>
 */
@Component({
  standalone: true,
  selector: 'app-employee-photo',
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    <!--
      The photo (or placeholder) and the optional remove-button are
      siblings inside a positioned container. The button is absolutely
      positioned in the bottom-right corner of the container.
    -->
    <div class="frame">
      @if (photoUrl()) {
        <img [src]="photoUrl()" [alt]="alt" class="photo" />
      } @else {
        <div class="placeholder" [attr.aria-label]="alt">
          <mat-icon>person</mat-icon>
        </div>
      }

      <!--
        The remove button only renders when the parent set
        [deletable]=true AND there is actually a photo to remove
        (no point letting the user click X on an empty placeholder).
      -->
      @if (deletable && photoUrl()) {
        <button type="button"
                class="remove-btn"
                aria-label="Remove photo"
                [disabled]="removing()"
                (click)="removePhoto()">
          <mat-icon>close</mat-icon>
        </button>
      }
    </div>
  `,
  styles: [`
    :host {
      display: inline-block;
      line-height: 0;   /* eat the line-height so the photo box is tight */
    }
    /*
      Positioned ancestor for the absolutely-positioned remove button.
      Sized to match the photo so the button anchors to its corner,
      not to the host's padding-box.
    */
    .frame {
      position: relative;
      width: var(--photo-size, 96px);
      height: var(--photo-size, 96px);
      display: inline-block;
    }
    .photo, .placeholder {
      width: 100%;
      height: 100%;
      border-radius: 50%;
      object-fit: cover;
      border: 1px solid rgba(0, 0, 0, 0.12);
      box-sizing: border-box;
    }
    .placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.04);
      color: rgba(0, 0, 0, 0.45);
    }
    .placeholder mat-icon {
      font-size: calc(var(--photo-size, 96px) * 0.55);
      width:    calc(var(--photo-size, 96px) * 0.55);
      height:   calc(var(--photo-size, 96px) * 0.55);
    }

    /*
      Round X button overlay. position:absolute pins it to the
      bottom-right corner of the .frame; the negative offsets make
      it sit just outside the photo's edge so it isn't clipped by
      the rounded mask.
    */
    .remove-btn {
      position: absolute;
      right: -4px;
      bottom: -4px;
      width: 24px; height: 24px;
      border-radius: 50%;
      border: 1px solid rgba(0, 0, 0, 0.12);
      background: #fff;
      color: #b71c1c;
      cursor: pointer;
      padding: 0;
      display: flex; align-items: center; justify-content: center;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
      line-height: 0;
    }
    .remove-btn:hover { background: #fdecea; }
    .remove-btn:disabled { opacity: 0.6; cursor: progress; }
    .remove-btn mat-icon {
      font-size: 16px; width: 16px; height: 16px;
    }
  `],
})
export class EmployeePhotoComponent implements OnChanges, OnDestroy {
  private readonly service = inject(EmployeeService);

  @Input({ required: true }) employeeId!: number;
  @Input() alt = 'Employee photo';

  /**
   * When true, render an X overlay in the bottom-right corner that
   * lets the user delete the photo. Defaults to false (read-only
   * display, e.g., on the detail page). The edit form sets it true.
   */
  @Input() deletable = false;

  /** Object URL for the current photo, or null when there is none. */
  readonly photoUrl = signal<string | null>(null);

  /** True while a delete request is in flight. */
  readonly removing = signal(false);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['employeeId']) {
      this.loadPhoto();
    }
  }

  ngOnDestroy(): void {
    this.revokeCurrent();
  }

  /**
   * Force the component to re-fetch the photo. Used by the edit form
   * after an upload — the {@code employeeId} input hasn't changed so
   * ngOnChanges wouldn't otherwise fire.
   */
  reload(): void {
    this.loadPhoto();
  }

  /**
   * Delete the photo on the backend, then drop the local object URL
   * so the placeholder shows. {@code disabled} on the button
   * deduplicates rapid clicks.
   */
  removePhoto(): void {
    if (this.removing()) return;
    this.removing.set(true);
    this.service.deletePhoto(this.employeeId).subscribe({
      next: () => {
        this.revokeCurrent();   // photoUrl → null → placeholder shows
        this.removing.set(false);
      },
      error: () => {
        // The most likely cause of failure here is "no photo on
        // file" (deleted by someone else, or never existed) — in
        // both cases the right local state IS no photo. So we just
        // wipe the local copy and surface no error.
        this.revokeCurrent();
        this.removing.set(false);
      },
    });
  }

  private loadPhoto(): void {
    this.revokeCurrent();
    this.service.fetchPhoto(this.employeeId).pipe(
      // 404 = no photo on file. Don't propagate; just set null so the
      // template falls through to the placeholder.
      catchError(() => of(null)),
    ).subscribe(blob => {
      if (blob == null) {
        this.photoUrl.set(null);
        return;
      }
      // createObjectURL returns a string like
      //   blob:http://localhost:4200/abc-123-...
      // that the browser resolves locally to the Blob's bytes — no
      // further HTTP needed.
      this.photoUrl.set(URL.createObjectURL(blob));
    });
  }

  private revokeCurrent(): void {
    const url = this.photoUrl();
    if (url) {
      URL.revokeObjectURL(url);
      this.photoUrl.set(null);
    }
  }
}
