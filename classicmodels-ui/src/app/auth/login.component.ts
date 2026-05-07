import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from './auth.service';

/**
 * Login form. Submits to AuthService which posts to the backend's
 * /auth/login endpoint. On success, navigate to the returnUrl from the
 * query string (set by AuthInterceptor when it bounced an unauthed
 * request) or fall back to the home page.
 */
@Component({
  standalone: true,
  selector: 'app-login',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <mat-card class="login-card">
      <mat-card-header>
        <mat-card-title>Sign in</mat-card-title>
        <mat-card-subtitle>Classic Models</mat-card-subtitle>
      </mat-card-header>

      <mat-card-content>
        @if (error()) {
          <div class="error-banner">{{ error() }}</div>
        }
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Username</mat-label>
            <input matInput formControlName="username" autocomplete="username" autofocus required />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Password</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="current-password" required />
          </mat-form-field>

          <div class="hint">
            Try <code>admin / admin123</code> or <code>user / user123</code>.
          </div>
        </form>

        <!--
          Divider + OAuth2 button. The button is a regular anchor that
          triggers a full-page navigation to the backend's
          /oauth2/authorization/google endpoint — Spring then handles
          the rest of the redirect dance to Google and back. We don't
          use HttpClient here because the response is a 302 redirect,
          not JSON.
        -->
        <div class="divider"><span>or</span></div>
        <a mat-stroked-button
           class="google-btn full-width"
           href="/api/v1/oauth2/authorization/google">
          <span class="g-mark">G</span>
          Sign in with Google
        </a>
      </mat-card-content>

      <mat-card-actions align="end">
        <button mat-flat-button color="primary"
                type="button"
                [disabled]="form.invalid || loading()"
                (click)="submit()">
          @if (loading()) {
            <mat-spinner diameter="18" style="display:inline-block; margin-right:0.5rem;"></mat-spinner>
          }
          Sign in
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [`
    :host        { display: flex; justify-content: center; padding: 3rem 1rem; }
    .login-card  { width: 360px; max-width: 100%; }
    .full-width  { width: 100%; }
    .hint        { color: rgba(0,0,0,0.55); font-size: 0.85rem; margin: 0.5rem 0 0; }
    .error-banner {
      border-left: 4px solid #b71c1c;
      background: #fdecea;
      color: #b71c1c;
      padding: 0.5rem 1rem;
      margin-bottom: 1rem;
      border-radius: 4px;
    }

    /* "or" divider with horizontal lines on either side */
    .divider {
      display: flex; align-items: center;
      margin: 1.25rem 0 1rem;
      color: rgba(0, 0, 0, 0.45);
      font-size: 0.85rem;
    }
    .divider::before, .divider::after {
      content: '';
      flex: 1;
      border-top: 1px solid rgba(0, 0, 0, 0.12);
    }
    .divider span { padding: 0 0.6rem; }

    .google-btn {
      display: inline-flex; align-items: center; justify-content: center;
      gap: 0.6rem;
      text-decoration: none;
    }
    .g-mark {
      display: inline-flex;
      width: 20px; height: 20px;
      align-items: center; justify-content: center;
      border-radius: 50%;
      background: #4285f4;
      color: white;
      font-weight: 700;
      font-family: 'Product Sans', Arial, sans-serif;
      font-size: 13px;
    }
  `],
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  form = this.fb.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  loading = signal(false);
  error = signal<string | undefined>(undefined);

  submit() {
    if (this.form.invalid) return;
    const { username, password } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(undefined);

    this.auth.login(username!, password!).subscribe({
      next: () => {
        this.loading.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        this.router.navigateByUrl(returnUrl);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err.status === 401
            ? 'Wrong username or password.'
            : (err?.error?.message ?? err?.message ?? 'Login failed'));
      },
    });
  }
}
