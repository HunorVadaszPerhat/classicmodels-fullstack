import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, tap } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

/**
 * STAGE 3 — Token-based auth.
 *
 * <p>The token lives in localStorage. That choice has trade-offs:</p>
 * <ul>
 *   <li><b>Pro:</b> survives page refreshes; easy to attach as a header;
 *       no CSRF surface (token isn't auto-sent by the browser like a
 *       cookie would be).</li>
 *   <li><b>Con:</b> readable by any JavaScript on the same origin, so an
 *       XSS bug = stolen token. Some teams prefer in-memory storage and
 *       refresh tokens in HttpOnly cookies for this reason.</li>
 * </ul>
 *
 * <p>For learning, localStorage is the standard simple choice. For
 * production-grade you'd probably move to refresh-token + short-lived
 * access token in memory.</p>
 */
export interface UserInfo {
  username: string;
  roles: string[];
}

interface LoginResponse {
  token: string;
  username: string;
  roles: string[];
}

const TOKEN_KEY = 'classicmodels.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly currentUser = signal<UserInfo | null>(null);
  readonly isLoggedIn = () => this.currentUser() !== null;

  /** The current token, or null if not logged in. Read by the HTTP interceptor. */
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  /**
   * Probe the backend for "who am I?". Used at app startup. If the
   * locally-stored token is still valid, the backend tells us our
   * username and roles; if it's expired or missing, we get 401 and
   * clear the cache.
   */
  refresh(): Observable<UserInfo | null> {
    if (!this.getToken()) {
      this.currentUser.set(null);
      return of(null);
    }
    return this.http.get<UserInfo>('/api/v1/auth/me').pipe(
      tap(user => this.currentUser.set(user)),
      catchError(() => {
        this.currentUser.set(null);
        localStorage.removeItem(TOKEN_KEY);
        return of(null);
      }),
    );
  }

  login(username: string, password: string): Observable<UserInfo> {
    return this.http
      .post<LoginResponse>('/api/v1/auth/login', { username, password })
      .pipe(
        map(res => {
          // Persist token first so the next outgoing request sees it.
          localStorage.setItem(TOKEN_KEY, res.token);
          const user: UserInfo = { username: res.username, roles: res.roles };
          this.currentUser.set(user);
          return user;
        }),
      );
  }

  /**
   * Sign-in via a pre-issued JWT, e.g. one minted by the backend after
   * a successful Google OAuth2 round-trip. Stores the token, then
   * calls /auth/me to fetch the canonical user info — same flow as
   * username/password login from this point onwards.
   *
   * <p>Used by {@code OAuth2SuccessComponent} after capturing the
   * token from the URL fragment.</p>
   */
  loginWithToken(token: string): Observable<UserInfo | null> {
    localStorage.setItem(TOKEN_KEY, token);
    return this.refresh();
  }

  logout(): Observable<unknown> {
    // Optimistic: drop the token immediately so subsequent calls don't
    // accidentally attach it. The backend logout endpoint is a no-op in
    // stateless mode but we still call it for symmetry / future-proofing.
    localStorage.removeItem(TOKEN_KEY);
    this.currentUser.set(null);
    return this.http.post('/api/v1/auth/logout', {}).pipe(
      tap(() => this.router.navigate(['/login'])),
    );
  }
}
