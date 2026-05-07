import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * STAGE 3 — Token-aware HTTP interceptor.
 *
 * <p>Two responsibilities:</p>
 * <ol>
 *   <li><b>Attach the token.</b> If we have one stored, clone the
 *       outgoing request and add an {@code Authorization: Bearer ...}
 *       header. The login and "who am I" endpoints get the same
 *       treatment — login obviously doesn't need it, but it's simpler
 *       to attach unconditionally than to special-case.</li>
 *   <li><b>Catch 401s.</b> Same as Stage 2: if the backend rejects a
 *       request as unauthenticated, drop the token and bounce to the
 *       login page. The token might be expired or revoked (we don't
 *       support revocation but might in the future).</li>
 * </ol>
 *
 * <p>Note we don't add the header for login itself — the auth/login
 * endpoint expects credentials in the body, not a token.</p>
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.getToken();
  const isLoginCall = req.url.includes('/auth/login');

  // Attach token if present and this isn't the login call.
  const authedReq = (token && !isLoginCall)
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authedReq).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/me');
      if (err.status === 401 && !isAuthEndpoint) {
        auth.currentUser.set(null);
        localStorage.removeItem('classicmodels.token');
        router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => err);
    }),
  );
};
