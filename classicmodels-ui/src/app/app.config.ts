import {
  APP_INITIALIZER,
  ApplicationConfig,
  importProvidersFrom,
  inject,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import {
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MatNativeDateModule } from '@angular/material/core';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    /*
     * Stage 3 (JWT): no CSRF wiring needed because we're not using
     * cookies. The interceptor attaches the bearer token; the rest of
     * Spring Security handles validation server-side.
     */
    provideHttpClient(
      withInterceptors([authInterceptor]),
    ),
    /*
     * APP_INITIALIZER runs before Angular bootstraps the root component.
     * We use it to call /auth/me once on startup so AuthService knows
     * whether there's an existing session (e.g. user refreshed the page
     * mid-session). Returning a Promise blocks bootstrap until the call
     * resolves, so the toolbar and route guards see the right auth
     * state from the very first render.
     */
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const auth = inject(AuthService);
        return () => firstValueFrom(auth.refresh()).catch(() => null);
      },
    },
    provideNoopAnimations(),
    importProvidersFrom(MatNativeDateModule),
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'fill' } },
  ],
};
