import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';

import { AuthService } from './auth.service';

/**
 * Landing page after a successful OAuth2 sign-in.
 *
 * <p>The backend's {@code OAuth2LoginSuccessHandler} mints a JWT and
 * 302-redirects the browser to {@code /login/oauth2/success#token=<jwt>}.
 * This component reads the token out of {@code window.location.hash},
 * hands it to {@link AuthService#loginWithToken}, then routes to the
 * home page (or to the {@code returnUrl} if one was preserved in
 * sessionStorage by the auth interceptor before the redirect).</p>
 *
 * <h3>Why fragment, not query string?</h3>
 *
 * <p>Query params get sent to servers — proxies, the SPA's host
 * server, any redirect target — and end up in access logs. Fragments
 * are client-only; the server never sees the token. For sensitive
 * material this is the right default.</p>
 *
 * <h3>Why not just trust the URL?</h3>
 *
 * <p>If a malicious site sent a user to
 * {@code /login/oauth2/success#token=ATTACKERS_TOKEN}, we'd happily
 * store it. That's the OAuth2-equivalent of session fixation. Two
 * ways to mitigate:</p>
 *
 * <ol>
 *   <li><b>Bind the redirect to a state parameter</b> the SPA
 *       generates and stores in sessionStorage before kicking off the
 *       flow, then verify it matches on return. Spring already
 *       handles state in its own filter chain, so this is partial
 *       protection.</li>
 *   <li><b>Use HttpOnly cookies for the token</b> instead of
 *       localStorage. The browser holds it; JavaScript can't read or
 *       leak it. Different storage trade-offs (XSRF surface
 *       reappears, etc.).</li>
 * </ol>
 *
 * <p>We do the simpler thing here for learning. Production would lean
 * on a CSRF-bound state nonce + HttpOnly cookie. The doc has links.</p>
 */
@Component({
  standalone: true,
  selector: 'app-oauth2-success',
  imports: [CommonModule, MatProgressSpinnerModule, MatCardModule, MatButtonModule],
  template: `
    <div class="wrapper">
      <mat-card class="card">
        @if (state() === 'loading') {
          <mat-spinner diameter="32"></mat-spinner>
          <p>Finishing sign-in…</p>
        } @else if (state() === 'error') {
          <h3>Sign-in failed</h3>
          <p>{{ errorMessage() }}</p>
          <button mat-flat-button color="primary" (click)="backToLogin()">
            Back to login
          </button>
        }
      </mat-card>
    </div>
  `,
  styles: [`
    .wrapper { display: flex; justify-content: center; padding: 4rem 1rem; }
    .card {
      min-width: 320px; max-width: 100%;
      padding: 2rem;
      display: flex; flex-direction: column; align-items: center; gap: 1rem;
      text-align: center;
    }
    p { margin: 0; color: rgba(0, 0, 0, 0.7); }
  `],
})
export class OAuth2SuccessComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  state        = signal<'loading' | 'error'>('loading');
  errorMessage = signal('');

  ngOnInit(): void {
    // Fragments look like "#token=abc&foo=bar"; URLSearchParams on the
    // substring after the '#' is the standard way to parse them.
    const hash = window.location.hash.startsWith('#')
        ? window.location.hash.substring(1)
        : window.location.hash;
    const params = new URLSearchParams(hash);
    const token = params.get('token');

    if (!token) {
      this.state.set('error');
      this.errorMessage.set('No token in callback URL — sign-in did not complete.');
      return;
    }

    this.auth.loginWithToken(token).subscribe({
      next: user => {
        if (user) {
          this.router.navigateByUrl('/');
        } else {
          this.state.set('error');
          this.errorMessage.set('Token rejected by /auth/me. It may have expired.');
        }
      },
      error: err => {
        this.state.set('error');
        this.errorMessage.set(err?.message ?? 'Could not finalize sign-in.');
      },
    });
  }

  backToLogin(): void {
    this.router.navigate(['/login']);
  }
}
