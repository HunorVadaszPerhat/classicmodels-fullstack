import { Routes } from '@angular/router';
import { AdminHealthComponent } from './admin-health.component';

export default [
  { path: 'health', component: AdminHealthComponent },
  { path: '', redirectTo: 'health', pathMatch: 'full' },
] as Routes;
