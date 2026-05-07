import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Functional route guard. Allows the route through if the user is logged
 * in; otherwise redirects to /login with a returnUrl pointing back to
 * where they tried to go.
 *
 * <p>This is the Angular-side mirror of the backend's
 * {@code .anyRequest().authenticated()} rule. The backend is the
 * authoritative gate (a malicious user can edit Angular code), but the
 * guard saves a round trip and gives the user a friendlier experience
 * than "click button, see redirect to login, click again."</p>
 */
export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
