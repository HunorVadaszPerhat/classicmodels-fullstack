import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { NAV_LINKS } from './home/home.component';
import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly title = signal('Classic Models');

  /**
   * Same array the {@code HomeComponent} renders. Imported here so the
   * toolbar nav and the landing-page nav can never drift out of sync.
   */
  readonly navLinks = NAV_LINKS;

  /**
   * Inject directly so the template can call auth.currentUser() and
   * auth.logout() without a wrapper. Keeps the toolbar template close
   * to the source of truth.
   */
  readonly auth = inject(AuthService);

  /** True iff the current user has the ADMIN role. Drives admin-only nav. */
  isAdmin(): boolean {
    return this.auth.currentUser()?.roles?.includes('ROLE_ADMIN') ?? false;
  }

  logout() {
    this.auth.logout().subscribe();
  }
}
