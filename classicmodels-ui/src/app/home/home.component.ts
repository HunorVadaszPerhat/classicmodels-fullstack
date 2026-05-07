import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

/**
 * The default landing page — intentionally blank.
 *
 * <p>All navigation lives in the toolbar (built from {@link NAV_LINKS}),
 * so the home route doesn't need any body content of its own. Keeping
 * the component empty rather than removing it preserves a clean URL for
 * "I'm just here to navigate" and keeps the wildcard route's redirect
 * target valid.</p>
 */
@Component({
  standalone: true,
  selector: 'app-home',
  imports: [CommonModule, MatButtonModule],
  template: ``,
})
export class HomeComponent {}

/**
 * The single source of truth for entity navigation, used by the toolbar
 * in {@code App}. Each entry maps to one of the routes declared in
 * {@code app.routes.ts}. Order is intentional — frequently-used entities
 * first. Sandbox is excluded; it's a developer playground, not part of
 * the user-facing entity list.
 */
export const NAV_LINKS: ReadonlyArray<{ path: string; label: string }> = [
  { path: '/dashboard',     label: 'Dashboard' },
  { path: '/customers',     label: 'Customers' },
  { path: '/employees',     label: 'Employees' },
  { path: '/offices',       label: 'Offices' },
  { path: '/orders',        label: 'Orders' },
  { path: '/order-details', label: 'Order details' },
  { path: '/payments',      label: 'Payments' },
  { path: '/products',      label: 'Products' },
];
