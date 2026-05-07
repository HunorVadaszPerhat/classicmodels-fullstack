import { Routes } from '@angular/router';
import { CustomerListComponent } from './customer-list.component';
import { CustomerDetailComponent } from './customer-detail.component';
import { CustomerFormComponent } from './customer-form.component';
import { CustomerLifetimeValueComponent } from './customer-lifetime-value.component';

export default [
  { path: '', component: CustomerListComponent },
  { path: 'new', component: CustomerFormComponent },
  // Two-segment ':id/<verb>' routes are matched precisely by Angular's
  // router; they don't collide with the bare ':id' detail route below.
  { path: ':id/lifetime-value', component: CustomerLifetimeValueComponent },
  { path: ':id/edit', component: CustomerFormComponent },
  { path: ':id', component: CustomerDetailComponent }
] as Routes;