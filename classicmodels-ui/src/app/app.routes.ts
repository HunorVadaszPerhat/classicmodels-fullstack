import { Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './auth/login.component';
import { OAuth2SuccessComponent } from './auth/oauth2-success.component';
import { authGuard } from './auth/auth.guard';


export const routes: Routes = [
  // The login page is intentionally NOT guarded — users need to reach
  // it precisely when they're not authenticated.
  { path: 'login', component: LoginComponent },

  // Backend OAuth2 success handler 302-redirects the browser here with
  // the freshly-minted JWT in the URL fragment. The component reads
  // the fragment, stores the token, then navigates to /. Also unguarded
  // because the guard would redirect to /login before we got the chance
  // to honor the token.
  { path: 'login/oauth2/success', component: OAuth2SuccessComponent },

  // Home stays the default landing route; gated like everything else.
  { path: '', component: HomeComponent, canActivate: [authGuard] },

  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadChildren: () => import('./dashboard/dashboard.routes').then(m => m.default)
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    loadChildren: () => import('./admin/admin.routes').then(m => m.default)
  },
  {
    path: 'customers',
    canActivate: [authGuard],
    loadChildren: () => import('./customers/customer.routes').then(m => m.default)
  },
  {
    path: 'employees',
    canActivate: [authGuard],
    loadChildren: () => import('./employees/employee.routes').then(m => m.default)
  },
  {
    path: 'offices',
    canActivate: [authGuard],
    loadChildren: () => import('./offices/office.routes').then(m => m.default)
  },
  {
    path: 'orders',
    canActivate: [authGuard],
    loadChildren: () => import('./orders/order.routes').then(m => m.default)
  },
  {
    path: 'order-details',
    canActivate: [authGuard],
    loadChildren: () => import('./order-details/order-detail.routes').then(m => m.default)
  },
  {
    path: 'payments',
    canActivate: [authGuard],
    loadChildren: () => import('./payments/payment.routes').then(m => m.default)
  },
  {
    path: 'products',
    canActivate: [authGuard],
    loadChildren: () => import('./products/product.routes').then(m => m.default)
  },
  {
    path: 'sandbox',
    canActivate: [authGuard],
    loadChildren: () => import('./sandbox/sandbox.routes').then(m => m.default)
  },
  // Unknown path → home (which itself redirects to /login if needed).
  { path: '**', redirectTo: '' }
];
